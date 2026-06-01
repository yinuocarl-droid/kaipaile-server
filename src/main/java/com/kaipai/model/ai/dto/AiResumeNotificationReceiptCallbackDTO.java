package com.kaipai.model.ai.dto;

import lombok.Data;

@Data
public class AiResumeNotificationReceiptCallbackDTO {

    private String requestId;

    private String providerCode;

    private String providerMessageId;

    private String failureId;

    private String receiptStatus;

    private String receiptAt;

    private String receiptFailureReason;

    private Object receiptPayload;
}
