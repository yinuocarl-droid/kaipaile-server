package com.kaipai.module.model.ai.dto;

import lombok.Data;

@Data
public class AdminAiResumeFailureActionDTO {

    private String reason;

    private Long assignedAdminId;

    private String escalationRoleCode;
}
