package com.kaipai.service.actor;

import com.kaipai.model.actor.dto.ActorProfileMineUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileRespDTO;

public interface ActorProfileWriteService {
    ActorProfileRespDTO mine(Long currentUserId);
    ActorProfileRespDTO saveMine(Long currentUserId, ActorProfileMineUpdateDTO request);
}
