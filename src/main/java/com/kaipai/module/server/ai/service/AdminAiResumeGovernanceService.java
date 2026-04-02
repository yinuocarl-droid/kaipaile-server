package com.kaipai.module.server.ai.service;

import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.ai.dto.AdminAiResumeHistoryItemDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeHistoryQueryDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeOverviewDTO;

public interface AdminAiResumeGovernanceService {

    AdminAiResumeOverviewDTO overview();

    PageResult<AdminAiResumeHistoryItemDTO> history(AdminAiResumeHistoryQueryDTO query);

    AdminAiResumeHistoryItemDTO historyDetail(String historyId);
}
