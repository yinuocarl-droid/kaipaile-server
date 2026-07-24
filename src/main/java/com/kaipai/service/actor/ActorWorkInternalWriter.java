package com.kaipai.service.actor;

import com.kaipai.model.actor.dto.ActorWorkRespDTO;
import com.kaipai.model.actor.dto.ActorWorkSaveDTO;

public interface ActorWorkInternalWriter {
    ActorWorkRespDTO createImportedWork(Long userId, ActorWorkSaveDTO request);

    ActorWorkRespDTO updateImportedWork(Long userId, Long experienceId, ActorWorkSaveDTO request);
}
