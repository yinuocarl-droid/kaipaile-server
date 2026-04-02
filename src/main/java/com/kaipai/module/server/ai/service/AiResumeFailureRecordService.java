package com.kaipai.module.server.ai.service;

import com.kaipai.module.model.ai.dto.AiResumeFailureRecordDTO;

import java.util.List;

public interface AiResumeFailureRecordService {

    void recordFailure(AiResumeFailureRecordDTO record);

    List<AiResumeFailureRecordDTO> recentFailures(int limit);

    List<AiResumeFailureRecordDTO> recentSensitiveHits(int limit);
}
