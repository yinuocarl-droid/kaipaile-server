package com.kaipai.module.model.ai.dto;

import com.kaipai.module.model.ai.entity.AiResumeNotificationDelivery;
import lombok.Data;

@Data
public class AiResumeNotificationDispatchResultDTO {

    private AiResumeNotificationDelivery delivery;

    private String sendStatus;

    private String receiptStatus;

    private String failureReason;
}
