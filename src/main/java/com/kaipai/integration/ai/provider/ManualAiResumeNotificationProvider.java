package com.kaipai.integration.ai.provider;

import com.kaipai.model.ai.dto.AiResumeNotificationSendCommand;
import com.kaipai.model.ai.dto.AiResumeNotificationSendResultDTO;
import com.kaipai.model.system.entity.AdminUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ManualAiResumeNotificationProvider implements AiResumeNotificationProvider {

    @Override
    public String providerCode() {
        return "manual";
    }

    @Override
    public AiResumeNotificationSendResultDTO send(AiResumeNotificationSendCommand command) {
        LocalDateTime now = LocalDateTime.now();
        AiResumeNotificationSendResultDTO result = new AiResumeNotificationSendResultDTO();
        result.setProviderCode(providerCode());
        result.setSendRequestedAt(now);

        AdminUser recipientAdmin = command == null ? null : command.getRecipientAdmin();
        String recipientPhone = recipientAdmin == null ? null : normalize(recipientAdmin.getPhone());
        String recipientEmail = recipientAdmin == null ? null : normalize(recipientAdmin.getEmail());
        if (!StringUtils.hasText(recipientPhone) && !StringUtils.hasText(recipientEmail)) {
            result.setChannelCode("manual");
            result.setSendStatus("send_failed");
            result.setSendFailureReason("recipient_contact_missing");
            return result;
        }

        result.setChannelCode(StringUtils.hasText(recipientPhone) ? "sms" : "email");
        result.setProviderMessageId("manual-" + UUID.randomUUID().toString().replace("-", ""));
        result.setSendStatus("sent");
        result.setSentAt(now);
        return result;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
