package com.kaipai.module.server.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.module.model.ai.dto.AiResumeFailureRecordDTO;
import com.kaipai.module.model.ai.entity.AiResumeNotificationDelivery;
import com.kaipai.module.model.system.entity.AdminUser;

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
