package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.provider.AiProfileImageProvider;
import com.kaipai.integration.ai.provider.AiProfileImageProviderRegistry;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardVisualInputMapper;
import com.kaipai.mapper.ai.ActorAiProfileCardTaskMapper;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.model.actor.card.dto.ActorCardExpandImageReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardExpandImageRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardVisualInput;
import com.kaipai.model.ai.entity.ActorAiProfileCardTask;
import com.kaipai.model.user.entity.User;
import com.kaipai.service.actor.ActorCardMainVisualResolver.SourceReference;
import com.kaipai.service.actor.support.ActorCardVisualInputReadSupport;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.config.AiProfileCardProperties;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationResult;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Internal actor-card main-visual expansion orchestration. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ActorCardExpandImageService {

    static final String MODE_EXPAND = "expand_image";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";

    private final ActorCardMapper actorCardMapper;
    private final ActorAiProfileCardTaskMapper taskMapper;
    private final AiProfileImageProviderRegistry providerRegistry;
    private final AiImageProviderConfigService providerConfigService;
    private final AiProfileCardProperties properties;
    private final ActorMediaAssetService mediaAssetService;
    private final ActorCardMediaBindingService mediaBindingService;
    private final AiGeneratedImageStorage generatedImageStorage;
    private final ActorCardExpandResultService resultService;
    private final ActorCardVisualInputMapper visualInputMapper;
    private final UserMapper userMapper;

    /** Retains direct-construction compatibility for the pre-B5 single-image tests. */
    ActorCardExpandImageService(
            ActorCardMapper actorCardMapper,
            ActorAiProfileCardTaskMapper taskMapper,
            AiProfileImageProviderRegistry providerRegistry,
            AiImageProviderConfigService providerConfigService,
            AiProfileCardProperties properties,
            ActorMediaAssetService mediaAssetService,
            ActorCardMediaBindingService mediaBindingService,
            AiGeneratedImageStorage generatedImageStorage,
            ActorCardExpandResultService resultService) {
        this(actorCardMapper, taskMapper, providerRegistry, providerConfigService, properties,
                mediaAssetService, mediaBindingService, generatedImageStorage, resultService,
                null, null);
    }

    @Resource(name = "aiProfileCardTaskExecutor")
    private Executor taskExecutor;

    /** Compatibility endpoint for old clients. New clients never call this directly. */
    public ActorCardExpandImageRespDTO submit(
            Long userId,
            Long cardId,
            ActorCardExpandImageReqDTO dto) {
        requireOwnedCard(userId, cardId);
        if (currentVisualInputOrLegacy(cardId) != null) {
            throw new BizException(409, "当前演员卡已使用新版首图输入，请通过新版首图入口重试");
        }
        SourceReference source = sourceFromRequest(dto);
        String originalUrl = source.assetId() == null
                ? source.imageUrl()
                : mediaAssetService.issueOwnerReadyPhotoAccessUrl(userId, source.assetId()).getAccessUrl();
        ActorCardExpandImageRespDTO response = enqueue(userId, cardId, source, true);
        response.setOriginalUrl(originalUrl);
        return response;
    }

    /**
     * Runs after a step-1 save or source binding transaction has completed.
     * Submission failures remain internal and must not turn a successful actor edit into an error.
     */
    public void requestAutomaticExpansion(Long userId, Long cardId) {
        try {
            ActorCard card = requireOwnedCard(userId, cardId);
            ActorCardVisualInput visualInput = currentVisualInputOrLegacy(cardId);
            if (visualInput != null) {
                requireRealName(userId);
                enqueueVisual(userId, cardId, visualInput, false);
                return;
            }
            ActorCardMediaBindingRespDTO binding = currentSourceBinding(userId, cardId);
            SourceReference source = ActorCardMainVisualResolver.resolveSourceReference(card, binding);
            if (source == null) {
                log.debug("[ActorCardExpand] cardId={} has no current source", cardId);
                return;
            }
            enqueue(userId, cardId, source, false);
        } catch (Exception error) {
            log.warn("[ActorCardExpand] automatic submit failed cardId={}: {}", cardId, error.getMessage(), error);
        }
    }

    /** Submits the saved B5 visual input. Person and background remain independent references. */
    public ActorCardExpandImageRespDTO submitVisualInput(Long userId, Long cardId) {
        requireOwnedCard(userId, cardId);
        requireRealName(userId);
        ActorCardVisualInput visualInput = requireVisualInput(userId, cardId);
        return enqueueVisual(userId, cardId, visualInput, false);
    }

    /** Retries only the task identified by the currently saved input fingerprint. */
    public ActorCardExpandImageRespDTO retryVisualInput(Long userId, Long cardId) {
        requireOwnedCard(userId, cardId);
        requireRealName(userId);
        return enqueueVisual(userId, cardId, requireVisualInput(userId, cardId), true);
    }

    public ActorCardExpandImageRespDTO status(Long userId, String taskId) {
        ActorAiProfileCardTask task = taskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BizException("扩图任务不存在");
        }
        ActorCardExpandImageRespDTO response = toResponse(task);
        response.setOriginalUrl(resolveResponseSourceUrl(
                userId,
                new SourceReference(
                        task.getSourceAssetId(),
                        task.getSourceImageUrl(),
                        task.getSourceFingerprint())));
        return response;
    }

    private ActorCardExpandImageRespDTO enqueue(
            Long userId,
            Long cardId,
            SourceReference source,
            boolean explicitRetry) {
        ActorAiProfileCardTask existing = taskMapper.selectByCardModeAndSource(
                cardId, MODE_EXPAND, source.fingerprint());
        if (existing != null) {
            return reuseExisting(existing, source, explicitRetry);
        }

        ProviderSelection provider = resolveProvider();
        ActorAiProfileCardTask task = newTask(userId, cardId, source, provider);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException duplicate) {
            ActorAiProfileCardTask raced = taskMapper.selectByCardModeAndSource(
                    cardId, MODE_EXPAND, source.fingerprint());
            if (raced == null) {
                throw duplicate;
            }
            return reuseExisting(raced, source, explicitRetry);
        }
        execute(task);
        return toResponse(task);
    }

    private ActorCardExpandImageRespDTO enqueueVisual(
            Long userId,
            Long cardId,
            ActorCardVisualInput visualInput,
            boolean explicitRetry) {
        ActorAiProfileCardTask existing = taskMapper.selectByCardModeAndSource(
                cardId, visualInput.getGenerationMode(), visualInput.getInputFingerprint());
        if (existing != null) {
            return reuseExisting(existing, visualSourceReference(visualInput), explicitRetry);
        }

        int referenceCount = ActorCardVisualInputService.MODE_PERSON_BACKGROUND_AI.equals(
                visualInput.getGenerationMode()) ? 2 : 1;
        ProviderSelection provider = resolveProvider(referenceCount);
        ActorAiProfileCardTask task = newVisualTask(userId, cardId, visualInput, provider);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException duplicate) {
            ActorAiProfileCardTask raced = taskMapper.selectByCardModeAndSource(
                    cardId, visualInput.getGenerationMode(), visualInput.getInputFingerprint());
            if (raced == null) {
                throw duplicate;
            }
            return reuseExisting(raced, visualSourceReference(visualInput), explicitRetry);
        }
        visualInputMapper.attachTaskIfCurrent(
                userId, cardId, visualInput.getInputFingerprint(), task.getTaskId());
        execute(task);
        return toResponse(task);
    }

    private ActorCardExpandImageRespDTO reuseExisting(
            ActorAiProfileCardTask task,
            SourceReference source,
            boolean explicitRetry) {
        if (STATUS_SUCCESS.equals(task.getStatus())) {
            if (StringUtils.hasText(task.getGeneratedImageUrl())) {
                applyExistingResult(task, source.fingerprint(), task.getGeneratedImageUrl());
            }
            return toResponse(task);
        }
        if (!STATUS_FAILED.equals(task.getStatus())) {
            return toResponse(task);
        }

        // TokenHub has no observed vendor idempotency contract. A failed task may already
        // have been billed (timeout, response parsing or COS storage); reads/saves must not
        // silently submit it again, even if the currently active provider has changed.
        if ("tencent-tokenhub".equals(task.getProviderCode()) && !explicitRetry) {
            return toResponse(task);
        }

        ProviderSelection provider = resolveProvider(referenceImageCount(task));
        int claimed = taskMapper.update(null, new UpdateWrapper<ActorAiProfileCardTask>()
                .eq("task_id", task.getTaskId())
                .eq("status", STATUS_FAILED)
                .set("status", STATUS_PENDING)
                .set("provider_code", provider.code())
                .set("model_code", provider.modelCode())
                .set("failure_reason", null)
                .set("generated_image_url", null)
                .set("started_at", null)
                .set("completed_at", null));
        if (claimed == 1) {
            task.setStatus(STATUS_PENDING);
            task.setProviderCode(provider.code());
            task.setModelCode(provider.modelCode());
            task.setFailureReason(null);
            task.setGeneratedImageUrl(null);
            task.setStartedAt(null);
            task.setCompletedAt(null);
            execute(task);
        } else {
            ActorAiProfileCardTask current = taskMapper.selectById(task.getTaskId());
            if (current != null) {
                task = current;
            }
        }
        return toResponse(task);
    }

    private void execute(ActorAiProfileCardTask task) {
        try {
            taskExecutor.execute(() -> {
                if (isVisualMode(task.getGenerationMode())) {
                    runVisual(task);
                } else {
                    runExpand(task);
                }
            });
        } catch (RuntimeException error) {
            markFailed(task.getTaskId(), error.getMessage());
            throw error;
        }
    }

    private void runExpand(ActorAiProfileCardTask task) {
        markRunning(task.getTaskId());
        try {
            String providerInput = resolveProviderInput(task);
            AiProfileImageProvider provider = providerRegistry.resolve(task.getProviderCode());
            AiProfileImageGenerationResult result = provider.generate(new AiProfileImageGenerationRequest(
                    task.getTaskId(),
                    provider.modelCode(),
                    "expand_image",
                    "expand_image",
                    providerInput,
                    buildExpandPrompt(),
                    null,
                    null));
            String expandedUrl = storeGeneratedResult(result, providerInput);
            boolean applied = resultService.applyIfCurrent(
                    task.getUserId(),
                    task.getActorCardId(),
                    task.getSourceFingerprint(),
                    expandedUrl);
            markSuccess(task.getTaskId(), expandedUrl);
            if (!applied) {
                log.info("[ActorCardExpand] ignored stale result taskId={} cardId={}",
                        task.getTaskId(), task.getActorCardId());
            }
        } catch (Exception error) {
            log.warn("[ActorCardExpand] taskId={} failed: {}", task.getTaskId(), error.getMessage(), error);
            markFailed(task.getTaskId(), error.getMessage());
        }
    }

    private void runVisual(ActorAiProfileCardTask task) {
        markRunning(task.getTaskId());
        try {
            ActorCardVisualInput currentInput = requireCurrentVisualTaskInput(task);
            String personInput = resolveProviderInput(task);
            String backgroundInput = resolveBackgroundProviderInput(currentInput);
            AiProfileImageProvider provider = providerRegistry.resolve(task.getProviderCode());
            int referenceCount = backgroundInput == null ? 1 : 2;
            if (!provider.supportsReferenceImageCount(referenceCount)) {
                throw new BizException("当前 AI Provider " + task.getProviderCode()
                        + " 不支持人物图 + 背景图双参考图输入");
            }
            AiProfileImageGenerationResult result = provider.generate(new AiProfileImageGenerationRequest(
                    task.getTaskId(),
                    provider.modelCode(),
                    "actor_card_main_visual",
                    task.getGenerationMode(),
                    personInput,
                    backgroundInput,
                    buildVisualPrompt(task.getGenerationMode()),
                    null,
                    task.getPromptJson()));
            String generatedUrl = storeGeneratedResult(result, personInput, backgroundInput);
            boolean applied = resultService.applyVisualIfCurrent(
                    task.getUserId(), task.getActorCardId(), task.getSourceFingerprint(),
                    task.getTaskId(), generatedUrl);
            markSuccess(task.getTaskId(), generatedUrl);
            if (!applied) {
                log.info("[ActorCardExpand] ignored stale visual result taskId={} cardId={}",
                        task.getTaskId(), task.getActorCardId());
            }
        } catch (Exception error) {
            log.warn("[ActorCardExpand] visual taskId={} failed: {}", task.getTaskId(), error.getMessage(), error);
            markFailed(task.getTaskId(), error.getMessage());
        }
    }

    private ActorAiProfileCardTask newTask(
            Long userId,
            Long cardId,
            SourceReference source,
            ProviderSelection provider) {
        ActorAiProfileCardTask task = new ActorAiProfileCardTask();
        task.setTaskId("acexp_" + UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(userId);
        task.setActorProfileId(-1L);
        task.setActorCardId(cardId);
        task.setSourceAssetId(source.assetId());
        task.setSourceFingerprint(source.fingerprint());
        task.setTemplateSceneCode("expand_image");
        task.setStyleCode("expand_image");
        task.setSourceImageUrl(source.imageUrl());
        task.setProviderCode(provider.code());
        task.setModelCode(provider.modelCode());
        task.setGenerationMode(MODE_EXPAND);
        task.setStatus(STATUS_PENDING);
        return task;
    }

    private ActorAiProfileCardTask newVisualTask(
            Long userId,
            Long cardId,
            ActorCardVisualInput visualInput,
            ProviderSelection provider) {
        ActorAiProfileCardTask task = new ActorAiProfileCardTask();
        task.setTaskId("acvis_" + UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(userId);
        task.setActorProfileId(-1L);
        task.setActorCardId(cardId);
        task.setSourceAssetId(visualInput.getPersonAssetId());
        task.setSourceImageUrl(visualInput.getPersonImageUrl());
        task.setSourceFingerprint(visualInput.getInputFingerprint());
        task.setTemplateSceneCode("actor_card_main_visual");
        task.setStyleCode(visualInput.getGenerationMode());
        task.setProviderCode(provider.code());
        task.setModelCode(provider.modelCode());
        task.setGenerationMode(visualInput.getGenerationMode());
        task.setPromptJson("{\"generationConfigVersion\":\""
                + visualInput.getGenerationConfigVersion() + "\"}");
        task.setStatus(STATUS_PENDING);
        return task;
    }

    private SourceReference sourceFromRequest(ActorCardExpandImageReqDTO dto) {
        if (dto == null) {
            throw new BizException("扩图请求不能为空");
        }
        if (dto.getSourceAssetId() != null) {
            return SourceReference.forAsset(dto.getSourceAssetId());
        }
        if (!StringUtils.hasText(dto.getSourceImageUrl())) {
            throw new BizException("sourceAssetId 或 sourceImageUrl 不能为空");
        }
        return SourceReference.forUrl(dto.getSourceImageUrl());
    }

    private String resolveProviderInput(ActorAiProfileCardTask task) {
        if (task.getSourceAssetId() != null) {
            return mediaAssetService.issueOwnerReadyPhotoAccessUrl(
                    task.getUserId(), task.getSourceAssetId()).getAccessUrl();
        }
        if (!StringUtils.hasText(task.getSourceImageUrl())) {
            throw new BizException("扩图来源不存在");
        }
        String stableUrl = task.getSourceImageUrl().trim();
        return generatedImageStorage.isManagedUrl(stableUrl)
                ? generatedImageStorage.issueReadUrl(stableUrl, Duration.ofMinutes(10))
                : stableUrl;
    }

    private String resolveBackgroundProviderInput(ActorCardVisualInput visualInput) {
        if (ActorCardVisualInputService.MODE_PERSON_AI.equals(visualInput.getGenerationMode())) {
            return null;
        }
        if (visualInput.getBackgroundId() == null
                || !StringUtils.hasText(visualInput.getBackgroundImageUrl())) {
            throw new BizException("AI 首图背景快照不存在，请重新选择背景");
        }
        return generatedImageStorage.issueReadUrl(
                visualInput.getBackgroundImageUrl(), Duration.ofMinutes(10));
    }

    private ActorCardVisualInput requireCurrentVisualTaskInput(ActorAiProfileCardTask task) {
        ActorCardVisualInput current = visualInputMapper.selectByCardId(task.getActorCardId());
        if (current == null
                || !task.getUserId().equals(current.getUserId())
                || !task.getGenerationMode().equals(current.getGenerationMode())
                || !task.getSourceFingerprint().equals(current.getInputFingerprint())) {
            throw new BizException("AI 首图输入已变化，旧任务已失效");
        }
        return current;
    }

    private String resolveResponseSourceUrl(Long userId, SourceReference source) {
        if (source == null) {
            return null;
        }
        if (source.assetId() != null) {
            try {
                return mediaAssetService.issueOwnerReadyPhotoAccessUrl(
                        userId, source.assetId()).getAccessUrl();
            } catch (Exception error) {
                log.debug("[ActorCardExpand] source preview unavailable assetId={}", source.assetId(), error);
                return null;
            }
        }
        return source.imageUrl();
    }

    private ActorCardMediaBindingRespDTO currentSourceBinding(Long userId, Long cardId) {
        try {
            return mediaBindingService.list(userId, cardId, "source_image", null);
        } catch (DataAccessException missingMigration) {
            log.info("[ActorCardExpand] media binding unavailable; using legacy URL source");
            return null;
        }
    }

    private ProviderSelection resolveProvider() {
        return resolveProvider(1);
    }

    private ProviderSelection resolveProvider(int referenceImageCount) {
        String providerCode = providerConfigService.resolveActiveProviderCode(properties.getProviderCode());
        AiProfileImageProvider provider = providerRegistry.resolve(providerCode);
        if (referenceImageCount > 1 && !provider.supportsReferenceImageCount(referenceImageCount)) {
            throw new BizException("当前 AI Provider " + providerCode
                    + " 不支持人物图 + 背景图双参考图输入");
        }
        return new ProviderSelection(providerCode, provider.modelCode());
    }

    private String storeGeneratedResult(AiProfileImageGenerationResult result, String... sourceImageUrls) {
        if (result == null) {
            throw new BizException("AI 扩图结果为空");
        }
        if (StringUtils.hasText(result.imageUrl())) {
            String resultUrl = result.imageUrl().trim();
            for (String sourceImageUrl : sourceImageUrls) {
                if (StringUtils.hasText(sourceImageUrl) && resultUrl.equals(sourceImageUrl)) {
                    throw new BizException("AI 扩图结果不能直接返回原始图片");
                }
            }
            return generatedImageStorage.uploadFromUrl(resultUrl, "actor-card-expand");
        }
        if (result.imageBytes() != null && result.imageBytes().length > 0) {
            return generatedImageStorage.upload(
                    result.imageBytes(),
                    StringUtils.hasText(result.contentType()) ? result.contentType() : "image/png",
                    "actor-card-expand");
        }
        throw new BizException("AI 扩图结果缺少图片内容");
    }

    private ActorCardExpandImageRespDTO toResponse(ActorAiProfileCardTask task) {
        ActorCardExpandImageRespDTO response = new ActorCardExpandImageRespDTO();
        response.setTaskId(task.getTaskId());
        response.setStatus(ActorCardTaskStatus.toApi(task.getStatus()));
        response.setOriginalUrl(task.getSourceImageUrl());
        response.setExpandedUrl(task.getGeneratedImageUrl());
        response.setFailureReason(task.getFailureReason());
        return response;
    }

    private String buildExpandPrompt() {
        return "参考图中的演员为主体，保持人物外貌与姿态原样，将画面向四周自然延伸扩展为 9:16 竖版全幅构图。"
                + "背景延伸部分保持与原图风格一致，干净自然，不添加任何文字、标签、水印或 UI 元素。"
                + "Preserve the subject exactly; expand the background outward to fill a 9:16 vertical canvas, "
                + "no text, no watermarks, no logos.";
    }

    private String buildVisualPrompt(String mode) {
        if (ActorCardVisualInputService.MODE_PERSON_BACKGROUND_AI.equals(mode)) {
            return "参考图1是演员人物来源，只保持人物身份、面貌、姿态和自然质感；"
                    + "参考图2是后台受控背景来源，只保持场景、色调和空间关系。"
                    + "将人物自然置入参考图2场景，输出9:16竖版全幅首图；"
                    + "不要混淆两张参考图的职责，不添加姓名、作品、按钮、文字、水印、Logo或任何UI。";
        }
        return "参考图1是演员人物来源，保持人物身份、面貌、姿态和自然质感，"
                + "扩展为9:16竖版全幅演员首图；不添加姓名、作品、按钮、文字、水印、Logo或任何UI。";
    }

    private void markRunning(String taskId) {
        taskMapper.update(null, new UpdateWrapper<ActorAiProfileCardTask>()
                .eq("task_id", taskId)
                .eq("status", STATUS_PENDING)
                .set("status", STATUS_RUNNING)
                .set("started_at", LocalDateTime.now()));
    }

    private void markSuccess(String taskId, String imageUrl) {
        taskMapper.update(null, new UpdateWrapper<ActorAiProfileCardTask>()
                .eq("task_id", taskId)
                .set("status", STATUS_SUCCESS)
                .set("generated_image_url", imageUrl)
                .set("failure_reason", null)
                .set("completed_at", LocalDateTime.now()));
    }

    private void markFailed(String taskId, String reason) {
        String message = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        taskMapper.update(null, new UpdateWrapper<ActorAiProfileCardTask>()
                .eq("task_id", taskId)
                .set("status", STATUS_FAILED)
                .set("failure_reason", message)
                .set("completed_at", LocalDateTime.now()));
    }

    private ActorCard requireOwnedCard(Long userId, Long cardId) {
        ActorCard card = actorCardMapper.selectById(cardId);
        if (card == null) {
            throw new BizException("演员卡不存在");
        }
        if (!userId.equals(card.getUserId())) {
            throw new BizException("无权操作该演员卡");
        }
        return card;
    }

    private ActorCardVisualInput requireVisualInput(Long userId, Long cardId) {
        if (visualInputMapper == null) {
            throw visualStorageUnavailable();
        }
        ActorCardVisualInput value;
        try {
            value = visualInputMapper.selectByCardId(cardId);
        } catch (RuntimeException error) {
            if (ActorCardVisualInputReadSupport.isMissingVisualInputTable(error)) {
                throw visualStorageUnavailable();
            }
            throw error;
        }
        if (value == null || !userId.equals(value.getUserId())) {
            throw new BizException("首图输入不存在");
        }
        return value;
    }

    private ActorCardVisualInput currentVisualInputOrLegacy(Long cardId) {
        return visualInputMapper == null
                ? null
                : ActorCardVisualInputReadSupport.selectOrLegacy(visualInputMapper, cardId);
    }

    private BizException visualStorageUnavailable() {
        return new BizException(503, "首图输入存储尚未就绪，请执行V20261022_003");
    }

    private void requireRealName(Long userId) {
        if (userMapper == null) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(2).equals(user.getRealAuthStatus())) {
            throw new BizException("完成实名认证后才可生成 AI 首图");
        }
    }

    private SourceReference visualSourceReference(ActorCardVisualInput visualInput) {
        return new SourceReference(
                visualInput.getPersonAssetId(),
                visualInput.getPersonImageUrl(),
                visualInput.getInputFingerprint());
    }

    private int referenceImageCount(ActorAiProfileCardTask task) {
        return ActorCardVisualInputService.MODE_PERSON_BACKGROUND_AI.equals(task.getGenerationMode())
                ? 2 : 1;
    }

    private boolean isVisualMode(String mode) {
        return ActorCardVisualInputService.MODE_PERSON_AI.equals(mode)
                || ActorCardVisualInputService.MODE_PERSON_BACKGROUND_AI.equals(mode);
    }

    private void applyExistingResult(
            ActorAiProfileCardTask task,
            String sourceFingerprint,
            String generatedImageUrl) {
        if (isVisualMode(task.getGenerationMode())) {
            resultService.applyVisualIfCurrent(
                    task.getUserId(), task.getActorCardId(), sourceFingerprint,
                    task.getTaskId(), generatedImageUrl);
            return;
        }
        resultService.applyIfCurrent(
                task.getUserId(), task.getActorCardId(), sourceFingerprint, generatedImageUrl);
    }

    private record ProviderSelection(String code, String modelCode) {
    }
}
