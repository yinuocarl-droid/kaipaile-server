package com.kaipai.module.model.ai.dto;

import lombok.Data;

@Data
public class AdminAiResumeFailureEscalationRoleOptionDTO {

    private Long adminRoleId;

    private String roleCode;

    private String roleName;

    private String rolloutStage;
}
