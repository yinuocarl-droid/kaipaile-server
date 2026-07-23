package com.kaipai.service.actor;

import com.kaipai.model.actor.dto.ActorProfileMineUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileRespDTO;

public interface ActorProfileWriteService {
    ActorProfileRespDTO saveMine(Long currentUserId, ActorProfileMineUpdateDTO request);
}
