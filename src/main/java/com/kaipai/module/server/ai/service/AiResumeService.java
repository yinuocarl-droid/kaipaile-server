package com.kaipai.module.server.ai.service;

import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.ai.dto.AiResumeHistoryItemDTO;
import com.kaipai.module.model.ai.dto.AiResumePolishReqDTO;
import com.kaipai.module.model.ai.dto.AiResumePolishRespDTO;
import com.kaipai.module.model.ai.dto.AiResumeRollbackReqDTO;
import com.kaipai.module.model.ai.dto.AiResumeRollbackRespDTO;

public interface AiResumeService {

    AiResumePolishRespDTO polishResume(Long userId, AiResumePolishReqDTO dto);

    PageResult<AiResumeHistoryItemDTO> history(Long userId, int page, int size);

    AiResumeRollbackRespDTO rollback(Long userId, String historyId, AiResumeRollbackReqDTO dto);
}
