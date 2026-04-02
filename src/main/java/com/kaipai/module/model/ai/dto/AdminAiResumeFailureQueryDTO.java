package com.kaipai.module.model.ai.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminAiResumeFailureQueryDTO {

    private Long userId;

    private String handlingStatus;

    private String failureType;

    private String keyword;

    private String requestId;

    private Long assignedAdminId;

    private String escalationRoleCode;

    @Min(1)
    private Integer limit = 20;
}
