package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.ai.ActorAiProfileCardTaskMapper;
import com.kaipai.model.actor.card.dto.ActorCardGenerateRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.ai.entity.ActorAiProfileCardTask;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * T5: 演员卡 AI 生成服务
 * 校验必填步骤 → 提交异步生成任务 → 轮询状态。
 * 当前阶段生成结果为拼合预览图 URL；渲染引擎可独立迭代，不阻塞接口结构。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActorCardGenerateService {

    private static final String MODE_CARD_GENERATE = "actor_card_generate";
    private static final String STATUS_PENDING  = "pending";
    private static final String STATUS_RUNNING  = "running";
    private static final String STATUS_SUCCESS  = "success";
    private static final String STATUS_FAILED   = "failed";

    private final ActorCardMapper actorCardMapper;
    private final ActorCardWorkMapper actorCardWorkMapper;
    private final ActorAiProfileCardTaskMapper taskMapper;

    @Resource(name = "aiProfileCardTaskExecutor")
    private Executor taskExecutor;

    public ActorCardGenerateRespDTO submit(Long userId, Long cardId) {
        ActorCard card = requireOwned(userId, cardId);
        validateRequiredSteps(card);

        ActorAiProfileCardTask task = new ActorAiProfileCardTask();
        task.setTaskId("acgen_" + UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(userId);
        task.setActorProfileId(-1L);
        task.setTemplateSceneCode("actor_card_generate");
        task.setStyleCode(StringUtils.hasText(card.getStyle()) ? card.getStyle() : "classic");
        task.setSourceImageUrl(card.getExpandedImageUrl() != null
                ? card.getExpandedImageUrl() : card.getSourceImageUrl());
        task.setProviderCode("actor_card_layout");
        task.setGenerationMode(MODE_CARD_GENERATE);
        task.setStatus(STATUS_PENDING);
        taskMapper.insert(task);

        taskExecutor.execute(() -> runGenerate(task.getTaskId(), cardId));

        ActorCardGenerateRespDTO resp = new ActorCardGenerateRespDTO();
        resp.setTaskId(task.getTaskId());
        resp.setStatus(STATUS_PENDING);
        return resp;
    }

    public ActorCardGenerateRespDTO status(Long userId, String taskId) {
        ActorAiProfileCardTask task = taskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BizException("生成任务不存在");
        }
        ActorCardGenerateRespDTO resp = new ActorCardGenerateRespDTO();
        resp.setTaskId(taskId);
        resp.setStatus(task.getStatus());
        resp.setPreviewUrl(task.getGeneratedImageUrl());
        resp.setFailureReason(task.getFailureReason());
        return resp;
    }

    private void runGenerate(String taskId, Long cardId) {
        markRunning(taskId);
        try {
            ActorCard card = actorCardMapper.selectById(cardId);
            if (card == null) throw new BizException("演员卡不存在");

            // TODO: 接入真实长页渲染引擎（当前以主视觉 URL 作为预览占位）
            String previewUrl = StringUtils.hasText(card.getExpandedImageUrl())
                    ? card.getExpandedImageUrl() : card.getSourceImageUrl();

            markSuccess(taskId, previewUrl);

            actorCardMapper.update(null, new LambdaUpdateWrapper<ActorCard>()
                    .eq(ActorCard::getId, cardId)
                    .set(ActorCard::getGeneratedPreviewUrl, previewUrl));
        } catch (Exception ex) {
            log.warn("[ActorCardGenerate] taskId={} failed: {}", taskId, ex.getMessage());
            markFailed(taskId, ex.getMessage());
        }
    }

    private void validateRequiredSteps(ActorCard card) {
        if (!StringUtils.hasText(card.getSourceImageUrl())) {
            throw new BizException("步骤1（主视觉照片）尚未完成");
        }
        if (!StringUtils.hasText(card.getProfileSnapshotJson())) {
            throw new BizException("步骤2（个人资料）尚未完成");
        }
        // 步骤3（参演作品）由 actor_card_work 表校验
        long workCount = actorCardWorkMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        com.kaipai.model.actor.card.entity.ActorCardWork>()
                        .eq(com.kaipai.model.actor.card.entity.ActorCardWork::getCardId, card.getId()));
        if (workCount == 0) {
            throw new BizException("步骤3（参演作品）至少需要 1 部作品");
        }
    }

    private void markRunning(String taskId) {
        taskMapper.update(null, new LambdaUpdateWrapper<ActorAiProfileCardTask>()
                .eq(ActorAiProfileCardTask::getTaskId, taskId)
                .set(ActorAiProfileCardTask::getStatus, STATUS_RUNNING)
                .set(ActorAiProfileCardTask::getStartedAt, LocalDateTime.now()));
    }

    private void markSuccess(String taskId, String previewUrl) {
        taskMapper.update(null, new LambdaUpdateWrapper<ActorAiProfileCardTask>()
                .eq(ActorAiProfileCardTask::getTaskId, taskId)
                .set(ActorAiProfileCardTask::getStatus, STATUS_SUCCESS)
                .set(ActorAiProfileCardTask::getGeneratedImageUrl, previewUrl)
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

    private ActorCard requireOwned(Long userId, Long cardId) {
        ActorCard card = actorCardMapper.selectById(cardId);
        if (card == null) throw new BizException("演员卡不存在");
        if (!userId.equals(card.getUserId())) throw new BizException("无权操作该演员卡");
        return card;
    }
}
