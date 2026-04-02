package com.kaipai.module.server.ai.service;

import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.actor.dto.ActorProfileSaveDTO;

public interface AiResumeApplyRecorder {

    void recordAppliedDraft(Long userId, ActorProfileDTO beforeProfile, ActorProfileSaveDTO saveDTO);
}
