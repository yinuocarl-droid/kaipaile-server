package com.kaipai.module.model.ai.dto;

import lombok.Data;

@Data
public class AiResumeFailureHandlingNoteDTO {

    private String actionType;

    private String handlingStatus;

    private String handlingNote;

    private Long handledByAdminId;

    private String handledByAdminName;

    private Long assignedAdminId;

    private String assignedAdminName;

    private String escalationRoleCode;

    private String escalationRoleName;

    private String handledAt;
}
