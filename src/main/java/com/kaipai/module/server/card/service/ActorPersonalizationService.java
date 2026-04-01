package com.kaipai.module.server.card.service;

import com.kaipai.module.model.card.dto.ActorPersonalizationRespDTO;

public interface ActorPersonalizationService {

    ActorPersonalizationRespDTO resolve(Long actorId, String requestedScene, boolean loadFortune, Long currentUserId);
}
