package com.kaipai.module.server.ai.service;

import com.kaipai.module.model.ai.dto.AiProfileCardGenerateReqDTO;
import com.kaipai.module.model.ai.dto.AiProfileCardGenerateRespDTO;
import com.kaipai.module.model.ai.dto.AiProfileCardTaskRespDTO;

import java.util.List;

public interface AiProfileCardService {

    AiProfileCardGenerateRespDTO generate(Long currentUserId, AiProfileCardGenerateReqDTO dto);

    AiProfileCardTaskRespDTO task(Long currentUserId, String taskId);

    List<AiProfileCardTaskRespDTO> tasks(Long currentUserId);
}
