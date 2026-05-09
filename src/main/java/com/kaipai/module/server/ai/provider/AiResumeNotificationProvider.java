package com.kaipai.module.server.ai.provider;

import com.kaipai.module.model.ai.dto.AiResumeNotificationSendCommand;
import com.kaipai.module.model.ai.dto.AiResumeNotificationSendResultDTO;

public interface AiResumeNotificationProvider {

    String providerCode();

    AiResumeNotificationSendResultDTO send(AiResumeNotificationSendCommand command);

    default boolean supports(String providerCode) {
        return providerCode() != null
                && providerCode != null
                && providerCode().trim().equalsIgnoreCase(providerCode.trim());
    }
}
