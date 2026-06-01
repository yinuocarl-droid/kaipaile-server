package com.kaipai.service.ai;

import com.kaipai.model.ai.dto.AiResumeFailureRecordDTO;

import java.util.List;

public interface AiResumeFailureRecordService {

    void recordFailure(AiResumeFailureRecordDTO record);

    AiResumeFailureRecordDTO findFailure(String failureId);

    List<AiResumeFailureRecordDTO> listAllRecords();

    List<AiResumeFailureRecordDTO> recentFailures(int limit);

    List<AiResumeFailureRecordDTO> recentSensitiveHits(int limit);
}
