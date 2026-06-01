package com.kaipai.service.ai;

import com.kaipai.model.ai.dto.ActorAiQuotaRespDTO;

public interface AiQuotaService {

    ActorAiQuotaRespDTO quota(Long userId, String quotaType);

    ActorAiQuotaRespDTO consumeResumePolishQuota(Long userId);
}
