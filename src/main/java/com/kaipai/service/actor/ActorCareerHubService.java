package com.kaipai.service.actor;

import com.kaipai.model.actor.dto.ActorCareerHubSummaryRespDTO;

public interface ActorCareerHubService {
    ActorCareerHubSummaryRespDTO summary(Long userId);
}
