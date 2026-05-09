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

    private String notificationStatus;

    private Long notificationDeliveryId;

    private String notificationSourceType;

    private String notificationChannelCode;

    private String notificationRecipient;

    private String notificationProviderCode;

    private String notificationProviderMessageId;

    private String notificationSentAt;

    private String notificationFailureReason;

    private String notificationReceiptStatus;

    private String notificationReceiptSourceType;

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

    private String createdAt;

    private List<AiResumeFailureHandlingNoteDTO> handlingNotes;
}
