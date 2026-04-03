package com.kaipai.module.model.ai.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminAiResumeFailureItemDTO {

    private String failureId;

    private Long userId;

    private String userName;

    private String phone;

    private Integer realAuthStatus;

    private Integer level;

    private String membershipTier;

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

    private Integer reminderCount;

    private Long lastRemindedByAdminId;

    private String lastRemindedByAdminName;

    private String lastRemindedAt;

    private String claimDeadlineAt;

    private String collaborationStatus;

    private String notificationStatus;

    private String notificationSentAt;

    private String notificationReceiptStatus;

    private String notificationReceiptAt;

    private String autoRemindStage;

    private String slaStatus;

    private String handledAt;

    private String createdAt;

    private List<AiResumeFailureHandlingNoteDTO> handlingNotes;
}
