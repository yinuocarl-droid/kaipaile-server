package com.kaipai.module.server.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.actor.dto.ActorPhotoCategoriesDTO;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.actor.dto.ActorWorkExperienceDTO;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.ai.dto.AiProfileCardArtifactRespDTO;
import com.kaipai.module.model.ai.dto.AiProfileCardGenerateReqDTO;
import com.kaipai.module.model.ai.dto.AiProfileCardGenerateRespDTO;
import com.kaipai.module.model.ai.dto.AiProfileCardThemeRespDTO;
import com.kaipai.module.model.ai.dto.AiProfileCardTaskRespDTO;
import com.kaipai.module.model.ai.entity.ActorAiProfileCardPage;
import com.kaipai.module.model.ai.entity.ActorAiProfileCardTask;
import com.kaipai.module.model.card.dto.ActorCardConfigRespDTO;
import com.kaipai.module.model.card.dto.ActorCardConfigSaveDTO;
import com.kaipai.module.model.card.dto.ActorMyShareCardItemDTO;
import com.kaipai.module.model.card.dto.CreateShareCardDTO;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.actor.service.ActorProfileService;
import com.kaipai.module.server.ai.config.AiProfileCardProperties;
import com.kaipai.module.server.ai.mapper.ActorAiProfileCardPageMapper;
import com.kaipai.module.server.ai.mapper.ActorAiProfileCardTaskMapper;
import com.kaipai.module.server.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.module.server.ai.profilecard.AiProfileCardGeneration;
import com.kaipai.module.server.ai.profilecard.AiProfileCardImageQualityInspection;
import com.kaipai.module.server.ai.profilecard.AiProfileCardImageQualityInspector;
import com.kaipai.module.server.ai.profilecard.AiProfileCardPrompt;
import com.kaipai.module.server.ai.profilecard.AiProfileCardPromptAgent;
import com.kaipai.module.server.ai.profilecard.AiProfileCardProviderDescriptor;
import com.kaipai.module.server.ai.profilecard.AiProfileCardThemeResolver;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;
import com.kaipai.module.server.ai.service.AiImageProviderConfigService;
import com.kaipai.module.server.ai.service.AiProfileCardService;
import com.kaipai.module.server.card.service.ActorCardConfigService;
import com.kaipai.module.server.card.service.UserShareCardService;
import com.kaipai.module.server.card.support.CurrentPhaseShareArtifactSupport;
import com.kaipai.module.server.card.support.TemplateSceneCodeValidator;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiProfileCardServiceImpl extends ServiceImpl<ActorAiProfileCardTaskMapper, ActorAiProfileCardTask> implements AiProfileCardService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private final ActorProfileService actorProfileService;
    private final ActorProfileMapper actorProfileMapper;
    private final AiProfileCardProperties properties;
    private final AiImageProviderConfigService aiImageProviderConfigService;
    private final AiProfileCardPromptAgent promptAgent;
    private final UserShareCardService userShareCardService;
    private final ActorCardConfigService actorCardConfigService;
    private final AiGeneratedImageStorage generatedImageStorage;
    private final ActorAiProfileCardPageMapper actorAiProfileCardPageMapper;
    private final AiProfileCardImageQualityInspector imageQualityInspector;

    @Resource(name = "aiProfileCardTaskExecutor")
    private Executor aiProfileCardTaskExecutor;

    @Override
    public AiProfileCardGenerateRespDTO generate(Long currentUserId, AiProfileCardGenerateReqDTO dto) {
        String templateSceneCode = TemplateSceneCodeValidator.requireAllowed(dto.getTemplateSceneCode());
        String styleCode = StringUtils.hasText(dto.getStyleCode()) ? dto.getStyleCode().trim() : templateSceneCode;
        ActorProfileDTO profile = actorProfileService.mine(currentUserId);
        ActorProfile profileEntity = requireProfileEntity(currentUserId);
        String sourceImageUrl = resolveSourceImage(profile, dto.getSourceImageUrl());
        String providerCode = aiImageProviderConfigService.resolveActiveProviderCode(properties.getProviderCode());
        AiProfileCardProviderDescriptor provider = promptAgent.resolveProvider(providerCode);

        ActorAiProfileCardTask task = new ActorAiProfileCardTask();
        task.setTaskId("aipf_" + UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(currentUserId);
        task.setActorProfileId(profileEntity.getActorProfileId());
        task.setTemplateSceneCode(templateSceneCode);
        task.setStyleCode(styleCode);
        task.setSourceImageUrl(sourceImageUrl);
        task.setProviderCode(provider.providerCode());
        task.setModelCode(provider.modelCode());
        task.setGenerationMode(defaultText(properties.getGenerationMode(), "image_to_image"));
        task.setStatus(STATUS_PENDING);
        save(task);

        aiProfileCardTaskExecutor.execute(() -> runGeneration(task.getTaskId()));

        AiProfileCardGenerateRespDTO response = new AiProfileCardGenerateRespDTO();
        response.setTaskId(task.getTaskId());
        response.setStatus(task.getStatus());
        response.setEstimatedReadyMinutes(properties.getEstimatedReadyMinutes());
        response.setMessage("图片生成中，请在10分钟后到「我的作品集」的「已创建分享」中查看 AI 图详情页。");
        return response;
    }

    @Override
    public AiProfileCardTaskRespDTO task(Long currentUserId, String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new BizException("taskId 不能为空");
        }
        ActorAiProfileCardTask task = getById(taskId.trim());
        if (task == null || !currentUserId.equals(task.getUserId())) {
            throw new BizException("AI 分享图任务不存在");
        }
        return toTaskResp(task);
    }

    @Override
    public List<AiProfileCardTaskRespDTO> tasks(Long currentUserId) {
        return list(new LambdaQueryWrapper<ActorAiProfileCardTask>()
                .eq(ActorAiProfileCardTask::getUserId, currentUserId)
                .orderByDesc(ActorAiProfileCardTask::getCreateTime)
                .last("limit 50"))
                .stream()
                .map(this::toTaskResp)
                .toList();
    }

    @Override
    public List<AiProfileCardArtifactRespDTO> artifacts(Long currentUserId) {
        return list(successArtifactQuery()
                .eq(ActorAiProfileCardTask::getUserId, currentUserId)
                .orderByDesc(ActorAiProfileCardTask::getCreateTime)
                .last("limit 50"))
                .stream()
                .filter(this::isRealGeneratedImageTask)
                .map(this::toArtifactResp)
                .toList();
    }

    @Override
    public AiProfileCardArtifactRespDTO artifact(String artifactId) {
        if (!StringUtils.hasText(artifactId)) {
            throw new BizException("artifactId 不能为空");
        }
        ActorAiProfileCardTask task = getOne(successArtifactQuery()
                .eq(ActorAiProfileCardTask::getTaskId, artifactId.trim())
                .last("limit 1"), false);
        if (task == null || !isRealGeneratedImageTask(task)) {
            throw new BizException("AI 分享图作品不存在");
        }
        return toArtifactResp(task);
    }

    @Override
    public AiProfileCardArtifactRespDTO latestArtifactByShareCard(Long shareCardId) {
        if (shareCardId == null || shareCardId <= 0) {
            throw new BizException("shareCardId 不能为空");
        }
        ActorAiProfileCardTask task = getOne(successArtifactQuery()
                .eq(ActorAiProfileCardTask::getShareCardId, shareCardId)
                .orderByDesc(ActorAiProfileCardTask::getCreateTime)
                .last("limit 1"), false);
        if (task == null || !isRealGeneratedImageTask(task)) {
            throw new BizException("AI 分享图作品不存在");
        }
        return toArtifactResp(task);
    }

    @Override
    public void deleteArtifact(Long currentUserId, String artifactId) {
        if (!StringUtils.hasText(artifactId)) {
            throw new BizException("artifactId 不能为空");
        }
        ActorAiProfileCardTask task = getById(artifactId.trim());
        if (task == null || !currentUserId.equals(task.getUserId())) {
            throw new BizException("AI 分享图作品不存在");
        }
        actorAiProfileCardPageMapper.delete(new LambdaQueryWrapper<ActorAiProfileCardPage>()
                .eq(ActorAiProfileCardPage::getTaskId, task.getTaskId()));
        removeById(task.getTaskId());
    }

    private void runGeneration(String taskId) {
        ActorAiProfileCardTask task = getById(taskId);
        if (task == null) {
            return;
        }

        markRunning(taskId);
        try {
            ActorProfileDTO profile = actorProfileService.mine(task.getUserId());
            String generatedImageUrl = generateCoverImageWithQualityGate(profile, task);
            ActorMyShareCardItemDTO card = createOrGetGeneratedShareCard(task);
            saveGeneratedShareCardConfig(task, profile, card, generatedImageUrl);
            markSuccess(taskId, card.getCardId(), generatedImageUrl);
        } catch (Exception error) {
            log.warn("AI profile card generation failed, taskId={}", taskId, error);
            markFailed(taskId, error.getMessage());
        }
    }

    private String generateCoverImageWithQualityGate(ActorProfileDTO profile,
                                                     ActorAiProfileCardTask task) {
        int maxAttempts = Math.max(1, properties.getCoverQualityMaxAttempts());
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                AiProfileCardGeneration generation = promptAgent.generate(
                        profile,
                        task.getTaskId() + "_cover_try" + attempt,
                        task.getProviderCode(),
                        task.getTemplateSceneCode(),
                        task.getStyleCode(),
                        task.getSourceImageUrl());
                savePrompt(task.getTaskId(), generation.prompt());
                String pageImageUrl = resolveGeneratedImageUrl(generation.imageResult(), task.getSourceImageUrl());
                if (properties.isCoverQualityGateEnabled()) {
                    AiProfileCardImageQualityInspection inspection = imageQualityInspector.inspectCover(
                            pageImageUrl,
                            task.getProviderCode());
                    if (!inspection.accepted()) {
                        throw new BizException(inspection.reason());
                    }
                }
                return pageImageUrl;
            } catch (RuntimeException error) {
                lastError = error;
                if (attempt >= maxAttempts) {
                    throw error;
                }
            } catch (Exception error) {
                lastError = new BizException(error.getMessage());
                if (attempt >= maxAttempts) {
                    throw lastError;
                }
            }
        }
        throw lastError == null ? new BizException("AI 分享图封面页生成失败") : lastError;
    }

    private String buildCoverRetryFailureReason(int attempt, int maxAttempts, String detail) {
        String reason = StringUtils.hasText(detail) ? detail.trim() : "封面成图质检未通过";
        if (attempt >= maxAttempts) {
            return reason;
        }
        return "封面成图质检未通过，已自动重试（" + attempt + "/" + maxAttempts + "）：" + reason;
    }

    private ActorMyShareCardItemDTO createOrGetGeneratedShareCard(ActorAiProfileCardTask task) {
        CreateShareCardDTO createDto = new CreateShareCardDTO();
        createDto.setTemplateSceneCode(task.getTemplateSceneCode());
        return userShareCardService.createCard(task.getUserId(), createDto);
    }

    private void saveGeneratedShareCardConfig(ActorAiProfileCardTask task,
                                              ActorProfileDTO profile,
                                              ActorMyShareCardItemDTO card,
                                              String generatedImageUrl) {
        AiProfileCardThemeResolver.Theme theme = AiProfileCardThemeResolver.resolve(task.getTemplateSceneCode(), task.getStyleCode());
        ActorCardConfigSaveDTO config = new ActorCardConfigSaveDTO();
        config.setShareCardId(card.getCardId());
        config.setLayoutVariant(defaultText(card.getLayoutVariant(), defaultLayoutVariant(task.getTemplateSceneCode())));
        config.setPrimaryColor(defaultText(card.getPrimaryColor(), theme.accentColor()));
        config.setAccentColor(defaultText(card.getAccentColor(), theme.surfaceStrongColor()));
        config.setBackgroundColor(defaultText(card.getBackgroundColor(), theme.backgroundColor()));
        config.setHighlightedExperiences(Collections.emptyList());
        config.setHighlightedPhotos(buildHighlightedPhotos(generatedImageUrl, task.getSourceImageUrl()));
        config.setTagOrder(buildTagOrder(profile));
        config.setPreferredArtifact(CurrentPhaseShareArtifactSupport.POSTER);
        actorCardConfigService.saveActorConfig(task.getUserId(), config);
    }

    private String resolveGeneratedImageUrl(AiProfileImageGenerationResult result, String sourceImageUrl) {
        if (result == null) {
            throw new BizException("AI 图片生成结果为空");
        }
        if (StringUtils.hasText(result.imageUrl())) {
            String imageUrl = result.imageUrl().trim();
            if (sameMediaUrl(imageUrl, sourceImageUrl)) {
                throw new BizException("AI 图片生成结果不能直接返回原始参考图");
            }
            return generatedImageStorage.uploadFromUrl(imageUrl, "ai-profile-card");
        }
        if (result.imageBytes() != null && result.imageBytes().length > 0) {
            return generatedImageStorage.upload(
                    result.imageBytes(),
                    StringUtils.hasText(result.contentType()) ? result.contentType() : "image/png",
                    "ai-profile-card");
        }
        throw new BizException("AI 图片生成结果缺少图片内容");
    }

    private ActorProfile requireProfileEntity(Long currentUserId) {
        ActorProfile profile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, currentUserId)
                .last("limit 1"));
        if (profile == null || profile.getActorProfileId() == null) {
            throw new BizException("请先完善演员档案后再生成 AI 分享图");
        }
        return profile;
    }

    private String resolveSourceImage(ActorProfileDTO profile, String requestedSourceImageUrl) {
        List<String> candidates = collectSourceImages(profile);
        if (candidates.isEmpty()) {
            throw new BizException("请先在个人档案上传头像或形象照后再生成 AI 分享图");
        }
        if (!StringUtils.hasText(requestedSourceImageUrl)) {
            return candidates.get(0);
        }
        String requested = requestedSourceImageUrl.trim();
        if (!candidates.contains(requested)) {
            throw new BizException("选择的参考图不在当前个人档案中");
        }
        return requested;
    }

    private List<String> collectSourceImages(ActorProfileDTO profile) {
        LinkedHashSet<String> images = new LinkedHashSet<>();
        addImage(images, profile.getAvatar());
        ActorPhotoCategoriesDTO categories = profile.getPhotoCategories();
        if (categories != null) {
            addImages(images, categories.getPortrait());
            addImages(images, categories.getLifestyle());
            addImages(images, categories.getProduction());
        }
        addImages(images, profile.getPhotos());
        for (ActorWorkExperienceDTO experience : safeList(profile.getWorkExperiences())) {
            addImages(images, experience.getPhotos());
        }
        return new ArrayList<>(images);
    }

    private void addImages(LinkedHashSet<String> images, List<String> values) {
        for (String value : safeList(values)) {
            addImage(images, value);
        }
    }

    private void addImage(LinkedHashSet<String> images, String value) {
        if (StringUtils.hasText(value)) {
            images.add(value.trim());
        }
    }

    private List<String> buildHighlightedPhotos(String generatedImageUrl, String sourceImageUrl) {
        LinkedHashSet<String> photos = new LinkedHashSet<>();
        addImage(photos, generatedImageUrl);
        addImage(photos, sourceImageUrl);
        return new ArrayList<>(photos).stream().limit(3).toList();
    }

    private List<String> buildTagOrder(ActorProfileDTO profile) {
        List<String> tags = new ArrayList<>();
        for (String skill : safeList(profile.getSkillTypes())) {
            if (StringUtils.hasText(skill) && tags.size() < 8) {
                tags.add(skill.trim());
            }
        }
        return tags;
    }

    private void markRunning(String taskId) {
        ActorAiProfileCardTask update = new ActorAiProfileCardTask();
        update.setTaskId(taskId);
        update.setStatus(STATUS_RUNNING);
        update.setStartedAt(LocalDateTime.now());
        updateById(update);
    }

    private void savePrompt(String taskId, AiProfileCardPrompt prompt) {
        ActorAiProfileCardTask update = new ActorAiProfileCardTask();
        update.setTaskId(taskId);
        update.setPromptJson(prompt.promptJson());
        update.setPromptText(prompt.promptText());
        update.setNegativePrompt(prompt.negativePrompt());
        updateById(update);
    }

    private void markSuccess(String taskId, Long shareCardId, String generatedImageUrl) {
        ActorAiProfileCardTask update = new ActorAiProfileCardTask();
        update.setTaskId(taskId);
        update.setStatus(STATUS_SUCCESS);
        update.setShareCardId(shareCardId);
        update.setGeneratedImageUrl(generatedImageUrl);
        update.setCompletedAt(LocalDateTime.now());
        updateById(update);
    }

    private void markFailed(String taskId, String failureReason) {
        ActorAiProfileCardTask update = new ActorAiProfileCardTask();
        update.setTaskId(taskId);
        update.setStatus(STATUS_FAILED);
        update.setFailureReason(truncateFailure(failureReason));
        update.setCompletedAt(LocalDateTime.now());
        updateById(update);
    }

    private AiProfileCardTaskRespDTO toTaskResp(ActorAiProfileCardTask task) {
        task = ensurePersistedGeneratedImage(task);
        AiProfileCardTaskRespDTO dto = new AiProfileCardTaskRespDTO();
        dto.setTaskId(task.getTaskId());
        dto.setStatus(task.getStatus());
        dto.setTemplateSceneCode(task.getTemplateSceneCode());
        dto.setStyleCode(task.getStyleCode());
        dto.setProviderCode(task.getProviderCode());
        dto.setModelCode(task.getModelCode());
        dto.setShareCardId(task.getShareCardId());
        dto.setSourceImageUrl(task.getSourceImageUrl());
        dto.setGeneratedImageUrl(isRealGeneratedImageTask(task) ? task.getGeneratedImageUrl() : null);
        dto.setTheme(resolveTheme(task));
        dto.setFailureReason(task.getFailureReason());
        dto.setCreateTime(task.getCreateTime());
        dto.setLastUpdate(task.getLastUpdate());
        return dto;
    }

    private AiProfileCardArtifactRespDTO toArtifactResp(ActorAiProfileCardTask task) {
        task = ensurePersistedGeneratedImage(task);
        AiProfileCardArtifactRespDTO dto = new AiProfileCardArtifactRespDTO();
        dto.setArtifactId(task.getTaskId());
        dto.setTaskId(task.getTaskId());
        dto.setStatus(task.getStatus());
        dto.setTemplateSceneCode(task.getTemplateSceneCode());
        dto.setStyleCode(task.getStyleCode());
        dto.setProviderCode(task.getProviderCode());
        dto.setModelCode(task.getModelCode());
        dto.setShareCardId(task.getShareCardId());
        dto.setSourceImageUrl(task.getSourceImageUrl());
        dto.setGeneratedImageUrl(task.getGeneratedImageUrl());
        dto.setTheme(resolveTheme(task));
        dto.setCreateTime(task.getCreateTime());
        dto.setLastUpdate(task.getLastUpdate());
        return dto;
    }

    private boolean isRealGeneratedImageTask(ActorAiProfileCardTask task) {
        return task != null
                && STATUS_SUCCESS.equals(task.getStatus())
                && StringUtils.hasText(task.getGeneratedImageUrl())
                && !"mock".equalsIgnoreCase(defaultText(task.getProviderCode(), ""))
                && !sameMediaUrl(task.getGeneratedImageUrl(), task.getSourceImageUrl());
    }

    private AiProfileCardThemeRespDTO resolveTheme(ActorAiProfileCardTask task) {
        AiProfileCardThemeResolver.Theme theme = AiProfileCardThemeResolver.resolve(
                task == null ? "" : task.getTemplateSceneCode(),
                task == null ? "" : task.getStyleCode());
        AiProfileCardThemeRespDTO dto = new AiProfileCardThemeRespDTO();
        dto.setBackgroundColor(theme.backgroundColor());
        dto.setSurfaceColor(theme.surfaceColor());
        dto.setSurfaceStrongColor(theme.surfaceStrongColor());
        dto.setAccentColor(theme.accentColor());
        dto.setTextColor(theme.textColor());
        dto.setMutedTextColor(theme.mutedTextColor());
        dto.setBorderColor(theme.borderColor());
        return dto;
    }

    private boolean sameMediaUrl(String left, String right) {
        String normalizedLeft = normalizeMediaUrl(left);
        String normalizedRight = normalizeMediaUrl(right);
        return StringUtils.hasText(normalizedLeft)
                && StringUtils.hasText(normalizedRight)
                && normalizedLeft.equals(normalizedRight);
    }

    private String normalizeMediaUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().split("\\?", 2)[0];
    }

    private ActorAiProfileCardTask ensurePersistedGeneratedImage(ActorAiProfileCardTask task) {
        if (task == null
                || !STATUS_SUCCESS.equals(task.getStatus())
                || !StringUtils.hasText(task.getGeneratedImageUrl())
                || !isRealGeneratedImageTask(task)
                || generatedImageStorage.isManagedUrl(task.getGeneratedImageUrl())) {
            return task;
        }
        String originalImageUrl = task.getGeneratedImageUrl().trim();
        try {
            String persistedImageUrl = generatedImageStorage.uploadFromUrl(originalImageUrl, "ai-profile-card");
            ActorAiProfileCardTask update = new ActorAiProfileCardTask();
            update.setTaskId(task.getTaskId());
            update.setGeneratedImageUrl(persistedImageUrl);
            updateById(update);
            replaceGeneratedImageInPages(task.getTaskId(), originalImageUrl, persistedImageUrl);
            replaceGeneratedImageInCardConfig(task, originalImageUrl, persistedImageUrl);
            task.setGeneratedImageUrl(persistedImageUrl);
        } catch (Exception error) {
            log.warn("AI profile card generated image persist fallback failed, taskId={}", task.getTaskId(), error);
        }
        return task;
    }

    private void replaceGeneratedImageInPages(String taskId, String originalImageUrl, String persistedImageUrl) {
        if (!StringUtils.hasText(taskId) || !StringUtils.hasText(originalImageUrl) || !StringUtils.hasText(persistedImageUrl)) {
            return;
        }
        ActorAiProfileCardPage update = new ActorAiProfileCardPage();
        update.setGeneratedImageUrl(persistedImageUrl);
        actorAiProfileCardPageMapper.update(update, new LambdaUpdateWrapper<ActorAiProfileCardPage>()
                .eq(ActorAiProfileCardPage::getTaskId, taskId)
                .eq(ActorAiProfileCardPage::getGeneratedImageUrl, originalImageUrl));
    }

    private void replaceGeneratedImageInCardConfig(ActorAiProfileCardTask task,
                                                   String originalImageUrl,
                                                   String persistedImageUrl) {
        if (task.getShareCardId() == null || task.getShareCardId() <= 0 || !StringUtils.hasText(persistedImageUrl)) {
            return;
        }
        try {
            ActorCardConfigRespDTO current = actorCardConfigService.actorConfig(task.getShareCardId());
            List<String> photos = new ArrayList<>(safeList(current.getHighlightedPhotos()));
            boolean replaced = false;
            for (int index = 0; index < photos.size(); index++) {
                if (originalImageUrl.equals(photos.get(index))) {
                    photos.set(index, persistedImageUrl);
                    replaced = true;
                }
            }
            if (!replaced && !photos.contains(persistedImageUrl)) {
                photos.add(0, persistedImageUrl);
            }

            ActorCardConfigSaveDTO dto = new ActorCardConfigSaveDTO();
            dto.setShareCardId(task.getShareCardId());
            dto.setLayoutVariant(current.getLayoutVariant());
            dto.setPrimaryColor(current.getPrimaryColor());
            dto.setAccentColor(current.getAccentColor());
            dto.setBackgroundColor(current.getBackgroundColor());
            dto.setHighlightedExperiences(current.getHighlightedExperiences());
            dto.setHighlightedPhotos(photos.stream()
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(3)
                    .toList());
            dto.setTagOrder(current.getTagOrder());
            actorCardConfigService.saveActorConfig(task.getUserId(), dto);
        } catch (Exception error) {
            log.warn("AI profile card config generated image replacement failed, taskId={}, shareCardId={}",
                    task.getTaskId(), task.getShareCardId(), error);
        }
    }

    private LambdaQueryWrapper<ActorAiProfileCardTask> successArtifactQuery() {
        return new LambdaQueryWrapper<ActorAiProfileCardTask>()
                .eq(ActorAiProfileCardTask::getStatus, STATUS_SUCCESS)
                .isNotNull(ActorAiProfileCardTask::getShareCardId)
                .isNotNull(ActorAiProfileCardTask::getGeneratedImageUrl);
    }

    private String truncateFailure(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "AI 分享图生成失败";
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }

    private String defaultLayoutVariant(String templateSceneCode) {
        if ("urban".equals(templateSceneCode)) {
            return "spacious";
        }
        if ("costume".equals(templateSceneCode)) {
            return "magazine";
        }
        return "compact";
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

}
