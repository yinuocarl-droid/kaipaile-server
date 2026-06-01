package com.kaipai.service.ai;

import com.kaipai.model.actor.dto.ActorProfileDTO;
import com.kaipai.model.actor.dto.ActorProfileSaveDTO;

public interface AiResumeApplyRecorder {

    void recordAppliedDraft(Long userId, ActorProfileDTO beforeProfile, ActorProfileSaveDTO saveDTO);
}
