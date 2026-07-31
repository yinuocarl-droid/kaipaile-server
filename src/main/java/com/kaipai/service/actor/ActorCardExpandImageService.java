package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.provider.AiProfileImageProvider;
import com.kaipai.integration.ai.provider.AiProfileImageProviderRegistry;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.ai.ActorAiProfileCardTaskMapper;
import com.kaipai.model.actor.card.dto.ActorCardExpandImageReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardExpandImageRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.ai.entity.ActorAiProfileCardTask;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.config.AiProfileCardProperties;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationResult;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * T4: 演员卡首图扩图服务
 * 复用腾讯混元 provider 基础设施（callTencent / pollJob），使用扩图专属 prompt。
 * 任务记录存入 actor_ai_profile_card_task（generation_mode='expand_image'）复用轮询机制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActorCardExpandImageService {

    private static final String MODE_EXPAND = "expand_image";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";

    private final ActorCardMapper actorCardMapper;
    private final ActorAiProfileCardTaskMapper taskMapper;
    private final AiProfileImageProviderRegistry providerRegistry;
    private final AiImageProviderConfigService providerConfigService;
    private final AiProfileCardProperties properties;

    @Resource(name = "aiProfileCardTaskExecutor")
    private Executor taskExecutor;

    public ActorCardExpandImageRespDTO submit(Long userId, Long cardId,
                                              ActorCardExpandImageReqDTO dto) {
        requireOwnedCard(userId, cardId);
        if (!StringUtils.hasText(dto.getSourceImageUrl())) {
            throw new BizException("sourceImageUrl 不能为空");
        }

        String providerCode = providerConfigService.resolveActiveProviderCode(properties.getProviderCode());
        AiProfileImageProvider provider = providerRegistry.resolve(providerCode);

        ActorAiProfileCardTask task = new ActorAiProfileCardTask();
        task.setTaskId("acexp_" + UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(userId);
        // 扩图任务不关联 profile/share-card，设为 -1 占位
        task.setActorProfileId(-1L);
        task.setTemplateSceneCode("expand_image");
        task.setStyleCode("expand_image");
        task.setSourceImageUrl(dto.getSourceImageUrl());
        task.setProviderCode(providerCode);
        task.setModelCode(provider.modelCode());
        task.setGenerationMode(MODE_EXPAND);
        task.setStatus(STATUS_PENDING);
        taskMapper.insert(task);

        taskExecutor.execute(() -> runExpand(task.getTaskId(), cardId, dto.getSourceImageUrl(), providerCode));

        ActorCardExpandImageRespDTO resp = new ActorCardExpandImageRespDTO();
        resp.setTaskId(task.getTaskId());
        resp.setStatus(STATUS_PENDING);
        resp.setOriginalUrl(dto.getSourceImageUrl());
        return resp;
    }

    public ActorCardExpandImageRespDTO status(Long userId, String taskId) {
        ActorAiProfileCardTask task = taskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BizException("扩图任务不存在");
        }
        ActorCardExpandImageRespDTO resp = new ActorCardExpandImageRespDTO();
        resp.setTaskId(taskId);
        resp.setStatus(task.getStatus());
        resp.setOriginalUrl(task.getSourceImageUrl());
        resp.setExpandedUrl(task.getGeneratedImageUrl());
        resp.setFailureReason(task.getFailureReason());
        return resp;
    }

    private void runExpand(String taskId, Long cardId, String sourceImageUrl, String providerCode) {
        markRunning(taskId);
        try {
            AiProfileImageProvider provider = providerRegistry.resolve(providerCode);
            AiProfileImageGenerationRequest request = new AiProfileImageGenerationRequest(
                    taskId,
                    provider.modelCode(),
                    "expand_image",
                    "expand_image",
                    sourceImageUrl,
                    buildExpandPrompt(),
                    null,
                    null
            );
            AiProfileImageGenerationResult result = provider.generate(request);
            String expandedUrl = result.imageUrl();
            markSuccess(taskId, expandedUrl);
            // 自动回写 expanded_image_url 到演员卡
            actorCardMapper.update(null, new LambdaUpdateWrapper<ActorCard>()
                    .eq(ActorCard::getId, cardId)
                    .set(ActorCard::getExpandedImageUrl, expandedUrl));
        } catch (Exception ex) {
            log.warn("[ActorCardExpand] taskId={} failed: {}", taskId, ex.getMessage());
            markFailed(taskId, ex.getMessage());
        }
    }

    /** 扩图专属 prompt：保留人物，向四周自然延伸为 9:16 竖版构图 */
    private String buildExpandPrompt() {
        return "参考图中的演员为主体，保持人物外貌与姿态原样，将画面向四周自然延伸扩展为 9:16 竖版全幅构图。" +
               "背景延伸部分保持与原图风格一致，干净自然，不添加任何文字、标签、水印或 UI 元素。" +
               "Preserve the subject exactly; expand the background outward to fill a 9:16 vertical canvas, " +
               "no text, no watermarks, no logos.";
    }

    private void markRunning(String taskId) {
        taskMapper.update(null, new LambdaUpdateWrapper<ActorAiProfileCardTask>()
                .eq(ActorAiProfileCardTask::getTaskId, taskId)
                .set(ActorAiProfileCardTask::getStatus, STATUS_RUNNING)
                .set(ActorAiProfileCardTask::getStartedAt, LocalDateTime.now()));
    }

    private void markSuccess(String taskId, String imageUrl) {
        taskMapper.update(null, new LambdaUpdateWrapper<ActorAiProfileCardTask>()
                .eq(ActorAiProfileCardTask::getTaskId, taskId)
                .set(ActorAiProfileCardTask::getStatus, STATUS_SUCCESS)
                .set(ActorAiProfileCardTask::getGeneratedImageUrl, imageUrl)
                .set(ActorAiProfileCardTask::getCompletedAt, LocalDateTime.now()));
    }

    private void markFailed(String taskId, String reason) {
        String msg = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        taskMapper.update(null, new LambdaUpdateWrapper<ActorAiProfileCardTask>()
                .eq(ActorAiProfileCardTask::getTaskId, taskId)
                .set(ActorAiProfileCardTask::getStatus, STATUS_FAILED)
                .set(ActorAiProfileCardTask::getFailureReason, msg)
                .set(ActorAiProfileCardTask::getCompletedAt, LocalDateTime.now()));
    }

    private void requireOwnedCard(Long userId, Long cardId) {
        ActorCard card = actorCardMapper.selectById(cardId);
        if (card == null) throw new BizException("演员卡不存在");
        if (!userId.equals(card.getUserId())) throw new BizException("无权操作该演员卡");
    }
}
