package com.kaipai.model.ai.dto;

import lombok.Data;

@Data
public class AdminAiResumeGovernanceSweepItemDTO {

    private String failureId;

    private String requestId;

    private Long assignedAdminId;

    private String assignedAdminName;

    private String escalationRoleCode;

    private String escalationRoleName;

    private String actionType;

    private String actionStatus;

    private String detail;

    private String evaluatedAt;

    private String beforeCollaborationStatus;

    private String beforeNotificationStatus;

    private String beforeNotificationReceiptStatus;

    private String beforeAutoRemindStage;

    private String beforeSlaStatus;

    private Integer beforeReminderCount;

    private String afterHandlingStatus;

    private String afterCollaborationStatus;

    private String afterNotificationStatus;

    private String afterNotificationReceiptStatus;

    private String afterAutoRemindStage;

    private String afterSlaStatus;

    private Integer afterReminderCount;

    private AdminAiResumeFailureItemDTO failure;
}
