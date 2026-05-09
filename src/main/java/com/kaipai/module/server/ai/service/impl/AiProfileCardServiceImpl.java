package com.kaipai.module.server.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.actor.dto.ActorPhotoCategoriesDTO;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.actor.dto.ActorWorkExperienceDTO;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.ai.dto.AiProfileCardArtifactRespDTO;
import com.kaipai.module.model.ai.dto.AiProfileCardGenerateReqDTO;
import com.kaipai.module.model.ai.dto.AiProfileCardGenerateRespDTO;
import com.kaipai.module.model.ai.dto.AiProfileCardTaskRespDTO;
import com.kaipai.module.model.ai.entity.ActorAiProfileCardTask;
import com.kaipai.module.model.card.dto.ActorCardConfigSaveDTO;
import com.kaipai.module.model.card.dto.ActorMyShareCardItemDTO;
import com.kaipai.module.model.card.dto.CreateShareCardDTO;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.actor.service.ActorProfileService;
import com.kaipai.module.server.ai.config.AiProfileCardProperties;
import com.kaipai.module.server.ai.mapper.ActorAiProfileCardTaskMapper;
import com.kaipai.module.server.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.module.server.ai.profilecard.AiProfileCardPrompt;
import com.kaipai.module.server.ai.profilecard.AiProfileCardPromptAgent;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;
import com.kaipai.module.server.ai.provider.AiProfileImageProvider;
import com.kaipai.module.server.ai.provider.AiProfileImageProviderRegistry;
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
    private final AiProfileCardPromptAgent promptAgent;
    private final AiProfileImageProviderRegistry providerRegistry;
    private final UserShareCardService userShareCardService;
    private final ActorCardConfigService actorCardConfigService;
    private final AiGeneratedImageStorage generatedImageStorage;

    @Resource(name = "aiProfileCardTaskExecutor")
    private Executor aiProfileCardTaskExecutor;

    @Override
    public AiProfileCardGenerateRespDTO generate(Long currentUserId, AiProfileCardGenerateReqDTO dto) {
        String templateSceneCode = TemplateSceneCodeValidator.requireAllowed(dto.getTemplateSceneCode());
        String styleCode = StringUtils.hasText(dto.getStyleCode()) ? dto.getStyleCode().trim() : templateSceneCode;
        ActorProfileDTO profile = actorProfileService.mine(currentUserId);
        ActorProfile profileEntity = requireProfileEntity(currentUserId);
        String sourceImageUrl = resolveSourceImage(profile, dto.getSourceImageUrl());
        AiProfileImageProvider provider = providerRegistry.resolve(properties.getProviderCode());

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
        if (task == null) {
            throw new BizException("AI 分享图作品不存在");
        }
        return toArtifactResp(task);
    }

    private void runGeneration(String taskId) {
        ActorAiProfileCardTask task = getById(taskId);
        if (task == null) {
            return;
        }

        markRunning(taskId);
        try {
            ActorProfileDTO profile = actorProfileService.mine(task.getUserId());
            AiProfileImageProvider provider = providerRegistry.resolve(task.getProviderCode());
            AiProfileCardPrompt prompt = promptAgent.build(
                    profile,
                    task.getTemplateSceneCode(),
                    task.getSourceImageUrl(),
                    provider.modelCode());
            savePrompt(taskId, prompt);

            AiProfileImageGenerationResult generationResult = provider.generate(new AiProfileImageGenerationRequest(
                    taskId,
                    provider.modelCode(),
                    task.getTemplateSceneCode(),
                    task.getSourceImageUrl(),
                    prompt.promptText(),
                    prompt.negativePrompt(),
                    prompt.promptJson()
            ));
            String generatedImageUrl = resolveGeneratedImageUrl(generationResult);
            Long shareCardId = saveGeneratedShareCard(task, profile, generatedImageUrl);
            markSuccess(taskId, shareCardId, generatedImageUrl);
        } catch (Exception error) {
            log.warn("AI profile card generation failed, taskId={}", taskId, error);
            markFailed(taskId, error.getMessage());
        }
    }

    private Long saveGeneratedShareCard(ActorAiProfileCardTask task,
                                        ActorProfileDTO profile,
                                        String generatedImageUrl) {
        CreateShareCardDTO createDto = new CreateShareCardDTO();
        createDto.setTemplateSceneCode(task.getTemplateSceneCode());
        ActorMyShareCardItemDTO card = userShareCardService.createCard(task.getUserId(), createDto);

        ActorCardConfigSaveDTO config = new ActorCardConfigSaveDTO();
        config.setShareCardId(card.getCardId());
        config.setLayoutVariant(defaultText(card.getLayoutVariant(), defaultLayoutVariant(task.getTemplateSceneCode())));
        config.setPrimaryColor(defaultText(card.getPrimaryColor(), "#8c6f4f"));
        config.setAccentColor(defaultText(card.getAccentColor(), "#d4b896"));
        config.setBackgroundColor(defaultText(card.getBackgroundColor(), "#f5f3ee"));
        config.setHighlightedExperiences(Collections.emptyList());
        config.setHighlightedPhotos(buildHighlightedPhotos(generatedImageUrl, task.getSourceImageUrl()));
        config.setTagOrder(buildTagOrder(profile));
        config.setPreferredArtifact(CurrentPhaseShareArtifactSupport.POSTER);
        actorCardConfigService.saveActorConfig(task.getUserId(), config);
        return card.getCardId();
    }

    private String resolveGeneratedImageUrl(AiProfileImageGenerationResult result) {
        if (result == null) {
            throw new BizException("AI 图片生成结果为空");
        }
        if (StringUtils.hasText(result.imageUrl())) {
            return result.imageUrl().trim();
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
        AiProfileCardTaskRespDTO dto = new AiProfileCardTaskRespDTO();
        dto.setTaskId(task.getTaskId());
        dto.setStatus(task.getStatus());
        dto.setTemplateSceneCode(task.getTemplateSceneCode());
        dto.setStyleCode(task.getStyleCode());
        dto.setShareCardId(task.getShareCardId());
        dto.setSourceImageUrl(task.getSourceImageUrl());
        dto.setGeneratedImageUrl(task.getGeneratedImageUrl());
        dto.setFailureReason(task.getFailureReason());
        dto.setCreateTime(task.getCreateTime());
        dto.setLastUpdate(task.getLastUpdate());
        return dto;
    }

    private AiProfileCardArtifactRespDTO toArtifactResp(ActorAiProfileCardTask task) {
        AiProfileCardArtifactRespDTO dto = new AiProfileCardArtifactRespDTO();
        dto.setArtifactId(task.getTaskId());
        dto.setTaskId(task.getTaskId());
        dto.setStatus(task.getStatus());
        dto.setTemplateSceneCode(task.getTemplateSceneCode());
        dto.setStyleCode(task.getStyleCode());
        dto.setShareCardId(task.getShareCardId());
        dto.setGeneratedImageUrl(task.getGeneratedImageUrl());
        dto.setCreateTime(task.getCreateTime());
        dto.setLastUpdate(task.getLastUpdate());
        return dto;
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
