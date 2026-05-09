package com.kaipai.module.server.ai.service;

import com.kaipai.module.model.ai.dto.AiResumeNotificationDispatchResultDTO;
import com.kaipai.module.model.ai.dto.AiResumeNotificationReceiptCallbackDTO;
import com.kaipai.module.model.ai.dto.AiResumeNotificationSendCommand;

public interface AiResumeNotificationDispatchService {

    AiResumeNotificationDispatchResultDTO dispatch(AiResumeNotificationSendCommand command);

    AiResumeNotificationDispatchResultDTO ingestReceipt(AiResumeNotificationReceiptCallbackDTO callback);
}
