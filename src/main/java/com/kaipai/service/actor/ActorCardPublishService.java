package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.card.dto.ActorCardListItemDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.dto.ActorProfileCompletenessRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardWork;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.service.actor.support.ActorCardAttachmentCriterion;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * T6: 演员卡发布 / 名片夹列表 / 个人中心完整度
 */
@Service
@RequiredArgsConstructor
public class ActorCardPublishService {

    private static final Duration MAIN_VISUAL_READ_TTL = Duration.ofMinutes(10);

    private final ActorCardMapper actorCardMapper;
    private final ActorCardWorkMapper actorCardWorkMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final ActorCardMediaBindingService mediaBindingService;
    private final AiGeneratedImageStorage generatedImageStorage;

    // ── 发布 ──────────────────────────────────────────────────────────────────

    public void publish(Long userId, Long cardId) {
        ActorCard card = requireOwned(userId, cardId);
        if (!StringUtils.hasText(card.getGeneratedPreviewUrl())) {
            throw new BizException("演员卡尚未生成预览，请先完成 AI 生成步骤");
        }
        int newVersion = card.getPublishedVersion() != null ? card.getPublishedVersion() + 1 : 1;
        actorCardMapper.update(null, new LambdaUpdateWrapper<ActorCard>()
                .eq(ActorCard::getId, cardId)
                .set(ActorCard::getStatus, "published")
                .set(ActorCard::getPublishedVersion, newVersion)
                .set(ActorCard::getPublishedAt, LocalDateTime.now()));
    }

    // ── 名片夹列表 ────────────────────────────────────────────────────────────

    /** 获取名片夹列表，status 为 "published"、"draft" 或 null（全部） */
    public List<ActorCardListItemDTO> list(Long userId, String status) {
        LambdaQueryWrapper<ActorCard> query = new LambdaQueryWrapper<ActorCard>()
                .eq(ActorCard::getUserId, userId)
                .orderByDesc(ActorCard::getLastUpdate);
        if (StringUtils.hasText(status)) {
            query.eq(ActorCard::getStatus, status);
        }
        return actorCardMapper.selectList(query)
                .stream().map(this::toListItem).collect(Collectors.toList());
    }

    // ── 资料完整度 ────────────────────────────────────────────────────────────

    public ActorProfileCompletenessRespDTO completeness(Long userId) {
        ActorProfile profile = actorProfileMapper.selectOne(
                new LambdaQueryWrapper<ActorProfile>().eq(ActorProfile::getUserId, userId));

        long cardCount = actorCardMapper.selectCount(
                new LambdaQueryWrapper<ActorCard>()
                        .eq(ActorCard::getUserId, userId)
                        .eq(ActorCard::getStatus, "published"));

        ActorProfileCompletenessRespDTO dto = new ActorProfileCompletenessRespDTO();
        dto.setCardCount((int) cardCount);
        dto.setMaterialCount(0);  // 由素材库服务提供，此处占位
        dto.setViewCount(0);      // 由浏览统计服务提供，此处占位
        dto.setPercentage(profile != null ? calcProfileCompleteness(profile) : 0);
        return dto;
    }

    // ── 私有 ──────────────────────────────────────────────────────────────────

    private ActorCard requireOwned(Long userId, Long cardId) {
        ActorCard card = actorCardMapper.selectById(cardId);
        if (card == null) throw new BizException("演员卡不存在");
        if (!userId.equals(card.getUserId())) throw new BizException("无权操作该演员卡");
        return card;
    }

    private ActorCardListItemDTO toListItem(ActorCard card) {
        var sourceBinding = sourceBinding(card);
        long workCount = workCount(card);
        ActorCardListItemDTO dto = new ActorCardListItemDTO();
        dto.setId(card.getId());
        dto.setStatus(card.getStatus());
        dto.setTitle(card.getTitle());
        dto.setStyle(card.getStyle());
        dto.setCoverImageUrl(resolveCoverImageUrl(card, sourceBinding));
        dto.setPublishedVersion(card.getPublishedVersion());
        dto.setPublishedAt(card.getPublishedAt());
        dto.setLastUpdate(card.getLastUpdate());
        dto.setCompletionPercentage(calcCardCompletion(card, sourceBinding, workCount));
        boolean draft = "draft".equals(card.getStatus());
        dto.setGenerateReady(draft && hasRequiredGenerationInputs(card, sourceBinding, workCount));
        dto.setPublishReady(draft && StringUtils.hasText(card.getGeneratedPreviewUrl()));
        return dto;
    }

    private int calcProfileCompleteness(ActorProfile profile) {
        int fields = 10, done = 0;
        if (StringUtils.hasText(profile.getNickName())) done++;
        if (profile.getGender() != null) done++;
        if (profile.getHeight() != null) done++;
        if (profile.getBirthday() != null) done++;
        if (StringUtils.hasText(profile.getLocationCity())) done++;
        if (StringUtils.hasText(profile.getPhone())) done++;
        if (StringUtils.hasText(profile.getAvatarUrl())) done++;
        if (StringUtils.hasText(profile.getIntro())) done++;
        if (StringUtils.hasText(profile.getSkillTag())) done++;
        if (Boolean.TRUE.equals(profile.getIsCertified())) done++;
        return done * 100 / fields;
    }

    private int calcCardCompletion(
            ActorCard card,
            ActorCardMediaBindingRespDTO sourceBinding,
            long workCount) {
        int total = 7, done = 0;
        if (ActorCardMainVisualResolver.hasEffectiveSource(card, sourceBinding)) done++;
        if (StringUtils.hasText(card.getProfileSnapshotJson())) done++;
        if (workCount > 0) done++;
        if (StringUtils.hasText(card.getPhotosJson())) done++;
        if (StringUtils.hasText(card.getVideoUrl())) done++;
        // 与步骤标签共用同一判据，不再各写一份 hasText(attachmentUrl)
        if (ActorCardAttachmentCriterion.hasAttachment(card)) done++;
        if (StringUtils.hasText(card.getSettingsJson())) done++;
        return done * 100 / total;
    }

    private boolean hasRequiredGenerationInputs(
            ActorCard card,
            ActorCardMediaBindingRespDTO sourceBinding,
            long workCount) {
        return ActorCardMainVisualResolver.hasEffectiveSource(card, sourceBinding)
                && StringUtils.hasText(card.getProfileSnapshotJson())
                && workCount > 0
                && StringUtils.hasText(card.getSettingsJson());
    }

    private long workCount(ActorCard card) {
        try {
            return actorCardWorkMapper.selectCount(new LambdaQueryWrapper<ActorCardWork>()
                    .eq(ActorCardWork::getCardId, card.getId()));
        } catch (DataAccessException ignored) {
            return 0;
        }
    }

    private ActorCardMediaBindingRespDTO sourceBinding(ActorCard card) {
        try {
            return mediaBindingService.list(card.getUserId(), card.getId(), "source_image", null);
        } catch (DataAccessException ignored) {
            return null;
        }
    }

    private String resolveCoverImageUrl(ActorCard card, ActorCardMediaBindingRespDTO sourceBinding) {
        String resolved = ActorCardMainVisualResolver.resolveDisplayUrl(
                card, sourceBinding, this::isHttpUrl);
        if (!StringUtils.hasText(resolved)) {
            return null;
        }
        try {
            return generatedImageStorage.issueReadUrl(resolved, MAIN_VISUAL_READ_TTL);
        } catch (RuntimeException ignored) {
            return ActorCardMainVisualResolver.resolveSourceDisplayUrl(
                    card, sourceBinding, this::isHttpUrl);
        }
    }

    private boolean isHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

}
