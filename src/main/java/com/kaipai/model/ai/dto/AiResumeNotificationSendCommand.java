package com.kaipai.model.ai.dto;

import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.model.system.entity.AdminUser;
import lombok.Data;

@Data
public class AiResumeNotificationSendCommand {

    private String requestId;

    private String sendSourceType;

    private String reason;

    private AiResumeFailureRecordDTO failureRecord;

    private AdminAuthenticatedUser operator;

    private AdminUser recipientAdmin;
}
