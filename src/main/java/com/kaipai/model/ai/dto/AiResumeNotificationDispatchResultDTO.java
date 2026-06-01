package com.kaipai.model.ai.dto;

import com.kaipai.model.ai.entity.AiResumeNotificationDelivery;
import lombok.Data;

@Data
public class AiResumeNotificationDispatchResultDTO {

    private AiResumeNotificationDelivery delivery;

    private String sendStatus;

    private String receiptStatus;

    private String failureReason;
}
