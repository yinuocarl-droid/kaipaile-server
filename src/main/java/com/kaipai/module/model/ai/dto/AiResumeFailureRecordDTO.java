package com.kaipai.module.model.ai.dto;

import java.util.List;
import lombok.Data;

@Data
public class AiResumeFailureRecordDTO {

    private String failureId;

    private Long userId;

    private String requestId;

    private String conversationId;

    private String instruction;

    private Integer errorCode;

    private String errorMessage;

    private String failureType;

    private String hitKeyword;

    private String handlingStatus;

    private String handlingNote;

    private Long handledByAdminId;

    private String handledByAdminName;

    private Long assignedAdminId;

    private String assignedAdminName;

    private String assignedAt;

    private String escalationRoleCode;

    private String escalationRoleName;

    private Long assignmentAcknowledgedByAdminId;

    private String assignmentAcknowledgedByAdminName;

    private String assignmentAcknowledgedAt;

    private String handledAt;

    private String createdAt;

    private List<AiResumeFailureHandlingNoteDTO> handlingNotes;
}
