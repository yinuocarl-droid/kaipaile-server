package com.kaipai.model.ai.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminAiResumeFailureCollaborationCatalogDTO {

    private List<AdminAiResumeFailureAssigneeOptionDTO> assigneeOptions;

    private List<AdminAiResumeFailureEscalationRoleOptionDTO> escalationRoleOptions;
}
