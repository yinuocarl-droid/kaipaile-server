package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.provider.AiProfileImageProvider;
import com.kaipai.integration.ai.provider.AiProfileImageProviderRegistry;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.ai.ActorAiProfileCardTaskMapper;
import com.kaipai.model.actor.card.dto.ActorCardExpandImageReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardExpandImageRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.ai.entity.ActorAiProfileCardTask;
import com.kaipai.service.actor.ActorCardMainVisualResolver.SourceReference;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.config.AiProfileCardProperties;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationResult;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Internal actor-card main-visual expansion orchestration. */
@Slf4j
@Service
@RequiredArgsConstructor
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

    @Resource(name = "aiProfileCardTaskExecutor")
    private Executor taskExecutor;

    /** Compatibility endpoint for old clients. New clients never call this directly. */
    public ActorCardExpandImageRespDTO submit(
            Long userId,
            Long cardId,
            ActorCardExpandImageReqDTO dto) {
        requireOwnedCard(userId, cardId);
        SourceReference source = sourceFromRequest(dto);
        String originalUrl = source.assetId() == null
                ? source.imageUrl()
                : mediaAssetService.issueOwnerReadyPhotoAccessUrl(userId, source.assetId()).getAccessUrl();
        ActorCardExpandImageRespDTO response = enqueue(userId, cardId, source);
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
            ActorCardMediaBindingRespDTO binding = currentSourceBinding(userId, cardId);
            SourceReference source = ActorCardMainVisualResolver.resolveSourceReference(card, binding);
            if (source == null) {
                log.debug("[ActorCardExpand] cardId={} has no current source", cardId);
                return;
            }
            enqueue(userId, cardId, source);
        } catch (Exception error) {
            log.warn("[ActorCardExpand] automatic submit failed cardId={}: {}", cardId, error.getMessage(), error);
        }
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
            SourceReference source) {
        ActorAiProfileCardTask existing = taskMapper.selectByCardModeAndSource(
                cardId, MODE_EXPAND, source.fingerprint());
        if (existing != null) {
            return reuseExisting(existing, source);
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
            return reuseExisting(raced, source);
        }
        execute(task);
        return toResponse(task);
    }

    private ActorCardExpandImageRespDTO reuseExisting(
            ActorAiProfileCardTask task,
            SourceReference source) {
        if (STATUS_SUCCESS.equals(task.getStatus())) {
            if (StringUtils.hasText(task.getGeneratedImageUrl())) {
                resultService.applyIfCurrent(
                        task.getUserId(),
                        task.getActorCardId(),
                        source.fingerprint(),
                        task.getGeneratedImageUrl());
            }
            return toResponse(task);
        }
        if (!STATUS_FAILED.equals(task.getStatus())) {
            return toResponse(task);
        }

        ProviderSelection provider = resolveProvider();
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
            taskExecutor.execute(() -> runExpand(task));
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
        return task.getSourceImageUrl().trim();
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
        String providerCode = providerConfigService.resolveActiveProviderCode(properties.getProviderCode());
        AiProfileImageProvider provider = providerRegistry.resolve(providerCode);
        return new ProviderSelection(providerCode, provider.modelCode());
    }

    private String storeGeneratedResult(AiProfileImageGenerationResult result, String sourceImageUrl) {
        if (result == null) {
            throw new BizException("AI 扩图结果为空");
        }
        if (StringUtils.hasText(result.imageUrl())) {
            String resultUrl = result.imageUrl().trim();
            if (resultUrl.equals(sourceImageUrl)) {
                throw new BizException("AI 扩图结果不能直接返回原始图片");
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

    private record ProviderSelection(String code, String modelCode) {
    }
}
