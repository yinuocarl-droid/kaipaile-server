package com.kaipai.service.actor;

import com.kaipai.model.actor.card.dto.ActorCardMediaBindingReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import java.util.List;
import java.util.Map;

public interface ActorCardMediaBindingService {

    ActorCardMediaBindingRespDTO list(Long userId, Long cardId, String usage, Long workId);

    ActorCardMediaBindingRespDTO replace(
            Long userId,
            Long cardId,
            ActorCardMediaBindingReqDTO request);

    void clearWorkBindings(Long userId, Long cardId, List<Long> workIds);

    void copyBindings(
            Long userId,
            Long sourceCardId,
            Long targetCardId,
            Map<Long, Long> workIdMapping);
}
