package com.kaipai.module.server.ai.service;

import com.kaipai.module.model.ai.dto.ActorAiQuotaRespDTO;

public interface AiQuotaService {

    ActorAiQuotaRespDTO quota(Long userId, String quotaType);

    ActorAiQuotaRespDTO consumeResumePolishQuota(Long userId);
}
