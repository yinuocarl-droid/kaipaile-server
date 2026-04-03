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

    private String assignedAt;

    private String escalationRoleCode;

    private String escalationRoleName;

    private Long assignmentAcknowledgedByAdminId;

    private String assignmentAcknowledgedByAdminName;

    private String assignmentAcknowledgedAt;

    private String notificationStatus;

    private String notificationSentAt;

    private String notificationFailureReason;

    private String notificationReceiptStatus;

    private String notificationReceiptAt;

    private String notificationReceiptFailureReason;

    private Integer reminderCount;

    private Long lastRemindedByAdminId;

    private String lastRemindedByAdminName;

    private String lastRemindedAt;

    private Long manualTakeoverByAdminId;

    private String manualTakeoverByAdminName;

    private String manualTakeoverAt;

    private Long autoRemindSkippedByAdminId;

    private String autoRemindSkippedByAdminName;

    private String autoRemindSkippedAt;

    private String handledAt;
}
