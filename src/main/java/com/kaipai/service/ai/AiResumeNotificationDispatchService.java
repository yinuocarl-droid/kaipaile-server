package com.kaipai.service.ai;

import com.kaipai.model.ai.dto.AiResumeNotificationDispatchResultDTO;
import com.kaipai.model.ai.dto.AiResumeNotificationReceiptCallbackDTO;
import com.kaipai.model.ai.dto.AiResumeNotificationSendCommand;

public interface AiResumeNotificationDispatchService {

    AiResumeNotificationDispatchResultDTO dispatch(AiResumeNotificationSendCommand command);

    AiResumeNotificationDispatchResultDTO ingestReceipt(AiResumeNotificationReceiptCallbackDTO callback);
}
