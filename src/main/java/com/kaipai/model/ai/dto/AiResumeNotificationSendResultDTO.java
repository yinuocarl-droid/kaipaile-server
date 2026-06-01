package com.kaipai.model.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiResumeNotificationSendResultDTO {

    private String providerCode;

    private String channelCode;

    private String providerMessageId;

    private String sendStatus;

    private LocalDateTime sendRequestedAt;

    private LocalDateTime sentAt;

    private String sendFailureReason;
}
