package com.kaipai.service.actor.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.model.actor.card.dto.ActorCardRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardStepSaveReqDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.service.actor.ActorCardDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActorCardDraftServiceImpl implements ActorCardDraftService {

    private final ActorCardMapper actorCardMapper;

    @Override
    public ActorCardRespDTO createDraft(Long userId) {
        ActorCard card = new ActorCard();
        card.setUserId(userId);
        card.setStatus("draft");
        card.setCurrentStep(1);
        card.setPublishedVersion(0);
        actorCardMapper.insert(card);
        return toDto(card);
    }

    @Override
    public void saveStep(Long userId, Long cardId, ActorCardStepSaveReqDTO dto) {
        ActorCard card = requireOwned(userId, cardId);
        if (dto.getCurrentStep() != null) card.setCurrentStep(dto.getCurrentStep());
        if (StringUtils.hasText(dto.getTitle())) card.setTitle(dto.getTitle());
        if (StringUtils.hasText(dto.getStyle())) card.setStyle(dto.getStyle());
        if (StringUtils.hasText(dto.getBackgroundImageUrl())) card.setBackgroundImageUrl(dto.getBackgroundImageUrl());
        if (StringUtils.hasText(dto.getSourceImageUrl())) card.setSourceImageUrl(dto.getSourceImageUrl());
        if (StringUtils.hasText(dto.getExpandedImageUrl())) card.setExpandedImageUrl(dto.getExpandedImageUrl());
        if (StringUtils.hasText(dto.getProfileSnapshotJson())) card.setProfileSnapshotJson(dto.getProfileSnapshotJson());
        if (dto.getPhotosJson() != null) card.setPhotosJson(dto.getPhotosJson());
        if (dto.getVideoUrl() != null) card.setVideoUrl(dto.getVideoUrl());
        if (dto.getAttachmentUrl() != null) card.setAttachmentUrl(dto.getAttachmentUrl());
        if (StringUtils.hasText(dto.getSettingsJson())) card.setSettingsJson(dto.getSettingsJson());
        actorCardMapper.updateById(card);
    }

    @Override
    public ActorCardRespDTO getDraft(Long userId, Long cardId) {
        return toDto(requireOwned(userId, cardId));
    }

    @Override
    public List<ActorCardRespDTO> listDrafts(Long userId) {
        return actorCardMapper.selectList(new LambdaQueryWrapper<ActorCard>()
                        .eq(ActorCard::getUserId, userId)
                        .eq(ActorCard::getStatus, "draft")
                        .orderByDesc(ActorCard::getLastUpdate))
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public void deleteDraft(Long userId, Long cardId) {
        ActorCard card = requireOwned(userId, cardId);
        if (!"draft".equals(card.getStatus())) {
            throw new BizException("只允许删除草稿状态的演员卡");
        }
        actorCardMapper.deleteById(cardId);
    }

    private ActorCard requireOwned(Long userId, Long cardId) {
        ActorCard card = actorCardMapper.selectById(cardId);
        if (card == null) throw new BizException("演员卡不存在");
        if (!userId.equals(card.getUserId())) throw new BizException("无权操作该演员卡");
        return card;
    }

    private ActorCardRespDTO toDto(ActorCard card) {
        ActorCardRespDTO dto = new ActorCardRespDTO();
        dto.setId(card.getId());
        dto.setStatus(card.getStatus());
        dto.setTitle(card.getTitle());
        dto.setStyle(card.getStyle());
        dto.setCurrentStep(card.getCurrentStep() != null ? card.getCurrentStep() : 1);
        dto.setBackgroundImageUrl(card.getBackgroundImageUrl());
        dto.setSourceImageUrl(card.getSourceImageUrl());
        dto.setExpandedImageUrl(card.getExpandedImageUrl());
        dto.setGeneratedPreviewUrl(card.getGeneratedPreviewUrl());
        dto.setPublishedVersion(card.getPublishedVersion());
        dto.setPublishedAt(card.getPublishedAt());
        dto.setCreateTime(card.getCreateTime());
        dto.setLastUpdate(card.getLastUpdate());
        dto.setCompletionPercentage(calcCompletion(card));
        return dto;
    }

    private int calcCompletion(ActorCard card) {
        int done = 0;
        if (StringUtils.hasText(card.getExpandedImageUrl())) done++;      // 步骤1
        if (StringUtils.hasText(card.getProfileSnapshotJson())) done++;   // 步骤2
        if (StringUtils.hasText(card.getSettingsJson())) done++;          // 步骤7（必填）
        // 步骤3（参演作品）由 actor_card_work 表决定，简化为 settlingsJson 已填时视为完成
        int total = 7;
        return (int) Math.round(done * 100.0 / total);
    }
}
