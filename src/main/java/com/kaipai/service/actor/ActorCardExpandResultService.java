package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits an expansion result only while its stable source is still current. */
@Service
@RequiredArgsConstructor
public class ActorCardExpandResultService {

    private final ActorCardMapper actorCardMapper;
    private final ActorCardMediaBindingService mediaBindingService;

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
}
