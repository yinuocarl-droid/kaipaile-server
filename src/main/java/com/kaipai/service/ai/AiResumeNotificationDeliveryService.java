package com.kaipai.service.ai;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.model.ai.dto.AiResumeFailureRecordDTO;
import com.kaipai.model.ai.entity.AiResumeNotificationDelivery;
import com.kaipai.model.system.entity.AdminUser;

public interface AiResumeNotificationDeliveryService extends IService<AiResumeNotificationDelivery> {

    AiResumeNotificationDelivery findLatestByFailureId(String failureId);

    AiResumeNotificationDelivery findByProviderMessageId(String providerMessageId);

    AiResumeNotificationDelivery recordManualNotification(AiResumeFailureRecordDTO failureRecord,
                                                          AdminAuthenticatedUser operator,
                                                          AdminUser recipientAdmin,
                                                          String sendStatus,
                                                          String reason);

    AiResumeNotificationDelivery recordManualNotificationReceipt(AiResumeFailureRecordDTO failureRecord,
                                                                 AdminAuthenticatedUser operator,
                                                                 AdminUser recipientAdmin,
                                                                 String receiptStatus,
                                                                 String reason);
}
