package com.kaipai.module.server.ai.service;

import com.kaipai.module.model.ai.dto.AdminAiResumeFailureItemDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureActionDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureCollaborationCatalogDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureQueryDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeGovernanceSweepRequestDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeGovernanceSweepResultDTO;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.ai.dto.AdminAiResumeHistoryItemDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeHistoryQueryDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeOverviewDTO;

import java.util.List;

public interface AdminAiResumeGovernanceService {

    AdminAiResumeOverviewDTO overview();

    PageResult<AdminAiResumeHistoryItemDTO> history(AdminAiResumeHistoryQueryDTO query);

    AdminAiResumeHistoryItemDTO historyDetail(String historyId);

    List<AdminAiResumeFailureItemDTO> failures(AdminAiResumeFailureQueryDTO query);

    List<AdminAiResumeFailureItemDTO> sensitiveHits(AdminAiResumeFailureQueryDTO query);

    AdminAiResumeFailureCollaborationCatalogDTO collaborationCatalog();

    AdminAiResumeFailureItemDTO reviewFailure(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeFailureItemDTO suggestRetry(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeFailureItemDTO closeFailure(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeFailureItemDTO ignoreFailure(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeFailureItemDTO escalateFailure(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeFailureItemDTO assignFailure(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeFailureItemDTO acknowledgeAssignment(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeFailureItemDTO remindFailure(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeFailureItemDTO manualTakeoverFailure(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeFailureItemDTO skipAutoRemind(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeFailureItemDTO recordNotification(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeFailureItemDTO recordNotificationReceipt(String failureId, AdminAiResumeFailureActionDTO action);

    AdminAiResumeGovernanceSweepResultDTO previewGovernanceSweep(AdminAiResumeGovernanceSweepRequestDTO request);

    AdminAiResumeGovernanceSweepResultDTO executeGovernanceSweep(AdminAiResumeGovernanceSweepRequestDTO request);
}
