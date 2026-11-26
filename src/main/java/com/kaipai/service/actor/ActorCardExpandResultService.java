package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardVisualInputMapper;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits an expansion result only while its stable source is still current. */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ActorCardExpandResultService {

    private final ActorCardMapper actorCardMapper;
    private final ActorCardMediaBindingService mediaBindingService;
    private final ActorCardVisualInputMapper visualInputMapper;

    /** Retains direct-construction compatibility for the legacy expansion tests. */
    ActorCardExpandResultService(
            ActorCardMapper actorCardMapper,
            ActorCardMediaBindingService mediaBindingService) {
        this(actorCardMapper, mediaBindingService, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean applyIfCurrent(
            Long userId,
            Long cardId,
            String expectedSourceFingerprint,
            String expandedImageUrl) {
        ActorCard card = actorCardMapper.selectOwnedForUpdate(userId, cardId);
        if (card == null) {
            return false;
        }
        ActorCardMediaBindingRespDTO binding;
        try {
            binding = mediaBindingService.list(userId, cardId, "source_image", null);
        } catch (DataAccessException missingMigration) {
            binding = null;
        }
        var current = ActorCardMainVisualResolver.resolveSourceReference(card, binding);
        if (current == null || !current.fingerprint().equals(expectedSourceFingerprint)) {
            return false;
        }
        return actorCardMapper.update(null, new UpdateWrapper<ActorCard>()
                .eq("id", cardId)
                .eq("user_id", userId)
                .set("expanded_image_url", expandedImageUrl)
                .set("expanded_source_fingerprint", expectedSourceFingerprint)) == 1;
    }

    /** Applies a generated dual-mode image only if the current visual input still has the same fingerprint. */
    @Transactional(rollbackFor = Exception.class)
    public boolean applyVisualIfCurrent(
            Long userId,
            Long cardId,
            String expectedInputFingerprint,
            String taskId,
            String generatedImageUrl) {
        if (visualInputMapper == null) {
            return false;
        }
        return visualInputMapper.applyResultIfCurrent(
                userId, cardId, expectedInputFingerprint, taskId, generatedImageUrl) == 1;
    }
}
