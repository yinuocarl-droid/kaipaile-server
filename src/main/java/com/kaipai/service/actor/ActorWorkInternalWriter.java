package com.kaipai.service.actor;

import com.kaipai.model.actor.dto.ActorWorkRespDTO;
import com.kaipai.model.actor.dto.ActorWorkSaveDTO;

public interface ActorWorkInternalWriter {
    ActorWorkRespDTO createWork(
            Long userId, ActorWorkSaveDTO request, ActorWorkSourceType sourceType);
}
