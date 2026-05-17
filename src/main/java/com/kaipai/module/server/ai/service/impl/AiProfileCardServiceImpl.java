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
import com.kaipai.module.model.ai.dto.AiProfileCardPageRespDTO;
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
import com.kaipai.module.server.ai.profilecard.AiProfileCardPrompt;
import com.kaipai.module.server.ai.profilecard.AiProfileCardPromptAgent;
import com.kaipai.module.server.ai.profilecard.AiProfileCardProviderDescriptor;
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
    private static final List<AlbumPageDef> ALBUM_PAGE_DEFS = List.of(
            new AlbumPageDef(1, "cover"),
            new AlbumPageDef(2, "resume"),
            new AlbumPageDef(3, "gallery")
    );

    private final ActorProfileService actorProfileService;
    private final ActorProfileMapper actorProfileMapper;
    private final AiProfileCardProperties properties;
    private final AiImageProviderConfigService aiImageProviderConfigService;
    private final AiProfileCardPromptAgent promptAgent;
    private final UserShareCardService userShareCardService;
    private final ActorCardConfigService actorCardConfigService;
    private final AiGeneratedImageStorage generatedImageStorage;
    private final ActorAiProfileCardPageMapper actorAiProfileCardPageMapper;

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
        createInitialPages(task);

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
            List<ActorAiProfileCardPage> pages = ensurePages(task);
            String generatedImageUrl = "";
            for (AlbumPageDef pageDef : ALBUM_PAGE_DEFS) {
                ActorAiProfileCardPage page = findPage(pages, pageDef);
                if (isRealGeneratedPage(page, task.getSourceImageUrl())) {
                    if ("cover".equals(pageDef.pageType())) {
                        generatedImageUrl = page.getGeneratedImageUrl();
                    }
                    continue;
                }
                try {
                    markPageRunning(page);
                    AiProfileCardGeneration generation = promptAgent.generatePage(
                            profile,
                            taskId + "_" + pageDef.pageType(),
                            task.getProviderCode(),
                            task.getTemplateSceneCode(),
                            task.getStyleCode(),
                            task.getSourceImageUrl(),
                            pageDef.pageType(),
                            pageDef.pageNo());
                    savePagePrompt(page, generation.prompt());
                    if ("cover".equals(pageDef.pageType())) {
                        savePrompt(taskId, generation.prompt());
                    }

                    String pageImageUrl = resolveGeneratedImageUrl(generation.imageResult(), task.getSourceImageUrl());
                    markPageSuccess(page, pageImageUrl);
                    page.setStatus(STATUS_SUCCESS);
                    page.setGeneratedImageUrl(pageImageUrl);
                    if ("cover".equals(pageDef.pageType())) {
                        generatedImageUrl = pageImageUrl;
                    }
                } catch (Exception error) {
                    markPageFailed(page, error.getMessage());
                    throw error;
                }
            }

            if (!StringUtils.hasText(generatedImageUrl)) {
                generatedImageUrl = loadPages(taskId).stream()
                        .filter(page -> "cover".equals(page.getPageType()))
                        .filter(page -> isRealGeneratedPage(page, task.getSourceImageUrl()))
                        .map(ActorAiProfileCardPage::getGeneratedImageUrl)
                        .findFirst()
                        .orElseThrow(() -> new BizException("AI 分享图封面页生成失败"));
            }
            ActorMyShareCardItemDTO card = createOrGetGeneratedShareCard(task);
            saveGeneratedShareCardConfig(task, profile, card, generatedImageUrl);
            markPagesShareCardId(taskId, card.getCardId());
            markSuccess(taskId, card.getCardId(), generatedImageUrl);
        } catch (Exception error) {
            log.warn("AI profile card generation failed, taskId={}", taskId, error);
            markFailed(taskId, error.getMessage());
        }
    }

    private ActorMyShareCardItemDTO createOrGetGeneratedShareCard(ActorAiProfileCardTask task) {
        CreateShareCardDTO createDto = new CreateShareCardDTO();
        createDto.setTemplateSceneCode(task.getTemplateSceneCode());
        return userShareCardService.createCard(task.getUserId(), createDto);
    }

    private void createInitialPages(ActorAiProfileCardTask task) {
        for (AlbumPageDef pageDef : ALBUM_PAGE_DEFS) {
            ActorAiProfileCardPage page = new ActorAiProfileCardPage();
            page.setTaskId(task.getTaskId());
            page.setPageNo(pageDef.pageNo());
            page.setPageType(pageDef.pageType());
            page.setProviderCode(task.getProviderCode());
            page.setModelCode(task.getModelCode());
            page.setStatus(STATUS_PENDING);
            actorAiProfileCardPageMapper.insert(page);
        }
    }

    private List<ActorAiProfileCardPage> ensurePages(ActorAiProfileCardTask task) {
        List<ActorAiProfileCardPage> pages = loadPages(task.getTaskId());
        if (pages.size() >= ALBUM_PAGE_DEFS.size()) {
            return pages;
        }
        for (AlbumPageDef pageDef : ALBUM_PAGE_DEFS) {
            boolean exists = pages.stream().anyMatch(page -> pageDef.matches(page));
            if (exists) {
                continue;
            }
            ActorAiProfileCardPage page = new ActorAiProfileCardPage();
            page.setTaskId(task.getTaskId());
            page.setShareCardId(task.getShareCardId());
            page.setPageNo(pageDef.pageNo());
            page.setPageType(pageDef.pageType());
            page.setProviderCode(task.getProviderCode());
            page.setModelCode(task.getModelCode());
            page.setStatus(STATUS_PENDING);
            actorAiProfileCardPageMapper.insert(page);
        }
        return loadPages(task.getTaskId());
    }

    private ActorAiProfileCardPage findPage(List<ActorAiProfileCardPage> pages, AlbumPageDef pageDef) {
        return pages.stream()
                .filter(page -> pageDef.matches(page))
                .findFirst()
                .orElseThrow(() -> new BizException("AI 分享图页面任务缺失：" + pageDef.pageType()));
    }

    private List<ActorAiProfileCardPage> loadPages(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return Collections.emptyList();
        }
        return actorAiProfileCardPageMapper.selectList(new LambdaQueryWrapper<ActorAiProfileCardPage>()
                .eq(ActorAiProfileCardPage::getTaskId, taskId)
                .orderByAsc(ActorAiProfileCardPage::getPageNo));
    }

    private void saveGeneratedShareCardConfig(ActorAiProfileCardTask task,
                                              ActorProfileDTO profile,
                                              ActorMyShareCardItemDTO card,
                                              String generatedImageUrl) {
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

    private void markPageRunning(ActorAiProfileCardPage page) {
        ActorAiProfileCardPage update = new ActorAiProfileCardPage();
        update.setPageId(page.getPageId());
        update.setStatus(STATUS_RUNNING);
        update.setStartedAt(LocalDateTime.now());
        update.setFailureReason("");
        actorAiProfileCardPageMapper.updateById(update);
        page.setStatus(STATUS_RUNNING);
    }

    private void savePrompt(String taskId, AiProfileCardPrompt prompt) {
        ActorAiProfileCardTask update = new ActorAiProfileCardTask();
        update.setTaskId(taskId);
        update.setPromptJson(prompt.promptJson());
        update.setPromptText(prompt.promptText());
        update.setNegativePrompt(prompt.negativePrompt());
        updateById(update);
    }

    private void savePagePrompt(ActorAiProfileCardPage page, AiProfileCardPrompt prompt) {
        ActorAiProfileCardPage update = new ActorAiProfileCardPage();
        update.setPageId(page.getPageId());
        update.setPromptJson(prompt.promptJson());
        update.setPromptText(prompt.promptText());
        update.setNegativePrompt(prompt.negativePrompt());
        actorAiProfileCardPageMapper.updateById(update);
    }

    private void markPageSuccess(ActorAiProfileCardPage page, String generatedImageUrl) {
        ActorAiProfileCardPage update = new ActorAiProfileCardPage();
        update.setPageId(page.getPageId());
        update.setStatus(STATUS_SUCCESS);
        update.setGeneratedImageUrl(generatedImageUrl);
        update.setFailureReason("");
        update.setCompletedAt(LocalDateTime.now());
        actorAiProfileCardPageMapper.updateById(update);
    }

    private void markPageFailed(ActorAiProfileCardPage page, String failureReason) {
        ActorAiProfileCardPage update = new ActorAiProfileCardPage();
        update.setPageId(page.getPageId());
        update.setStatus(STATUS_FAILED);
        update.setFailureReason(truncateFailure(failureReason));
        update.setCompletedAt(LocalDateTime.now());
        actorAiProfileCardPageMapper.updateById(update);
    }

    private void markPagesShareCardId(String taskId, Long shareCardId) {
        if (!StringUtils.hasText(taskId) || shareCardId == null || shareCardId <= 0) {
            return;
        }
        ActorAiProfileCardPage update = new ActorAiProfileCardPage();
        update.setShareCardId(shareCardId);
        actorAiProfileCardPageMapper.update(update, new LambdaUpdateWrapper<ActorAiProfileCardPage>()
                .eq(ActorAiProfileCardPage::getTaskId, taskId));
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
        dto.setFailureReason(task.getFailureReason());
        dto.setPages(toPageRespList(task));
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
        dto.setPages(toPageRespList(task));
        dto.setCreateTime(task.getCreateTime());
        dto.setLastUpdate(task.getLastUpdate());
        return dto;
    }

    private List<AiProfileCardPageRespDTO> toPageRespList(ActorAiProfileCardTask task) {
        return loadPages(task.getTaskId()).stream()
                .map(page -> toPageResp(page, task.getSourceImageUrl()))
                .toList();
    }

    private AiProfileCardPageRespDTO toPageResp(ActorAiProfileCardPage page, String sourceImageUrl) {
        AiProfileCardPageRespDTO dto = new AiProfileCardPageRespDTO();
        dto.setPageNo(page.getPageNo());
        dto.setPageType(page.getPageType());
        dto.setStatus(page.getStatus());
        dto.setProviderCode(page.getProviderCode());
        dto.setModelCode(page.getModelCode());
        dto.setGeneratedImageUrl(isRealGeneratedPage(page, sourceImageUrl) ? page.getGeneratedImageUrl() : null);
        dto.setFailureReason(page.getFailureReason());
        dto.setCreateTime(page.getCreateTime());
        dto.setLastUpdate(page.getLastUpdate());
        return dto;
    }

    private boolean isRealGeneratedImageTask(ActorAiProfileCardTask task) {
        return task != null
                && STATUS_SUCCESS.equals(task.getStatus())
                && StringUtils.hasText(task.getGeneratedImageUrl())
                && !"mock".equalsIgnoreCase(defaultText(task.getProviderCode(), ""))
                && !sameMediaUrl(task.getGeneratedImageUrl(), task.getSourceImageUrl());
    }

    private boolean isRealGeneratedPage(ActorAiProfileCardPage page, String sourceImageUrl) {
        return page != null
                && STATUS_SUCCESS.equals(page.getStatus())
                && StringUtils.hasText(page.getGeneratedImageUrl())
                && !"mock".equalsIgnoreCase(defaultText(page.getProviderCode(), ""))
                && !sameMediaUrl(page.getGeneratedImageUrl(), sourceImageUrl);
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

    private record AlbumPageDef(
            int pageNo,
            String pageType
    ) {

        private boolean matches(ActorAiProfileCardPage page) {
            return page != null
                    && pageNo == (page.getPageNo() == null ? 0 : page.getPageNo())
                    && pageType.equals(page.getPageType());
        }
    }
}
