package com.kaipai.module.model.ai.dto;

import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.module.model.system.entity.AdminUser;
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
