package com.kaipai.module.server.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.module.model.ai.dto.AiResumeFailureRecordDTO;
import com.kaipai.module.model.ai.entity.AiResumeNotificationDelivery;
import com.kaipai.module.model.system.entity.AdminUser;
import com.kaipai.module.server.ai.mapper.AiResumeNotificationDeliveryMapper;
import com.kaipai.module.server.ai.service.AiResumeNotificationDeliveryService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiResumeNotificationDeliveryServiceImpl
        extends ServiceImpl<AiResumeNotificationDeliveryMapper, AiResumeNotificationDelivery>
        implements AiResumeNotificationDeliveryService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public AiResumeNotificationDelivery findLatestByFailureId(String failureId) {
        return latestDelivery(failureId);
    }

    @Override
    public AiResumeNotificationDelivery findByProviderMessageId(String providerMessageId) {
        if (!StringUtils.hasText(providerMessageId)) {
            return null;
        }
        return lambdaQuery()
                .eq(AiResumeNotificationDelivery::getProviderMessageId, providerMessageId.trim())
                .orderByDesc(AiResumeNotificationDelivery::getCreateTime)
                .orderByDesc(AiResumeNotificationDelivery::getDeliveryId)
                .last("limit 1")
                .one();
    }

    @Override
    public AiResumeNotificationDelivery recordManualNotification(AiResumeFailureRecordDTO failureRecord,
                                                                 AdminAuthenticatedUser operator,
                                                                 AdminUser recipientAdmin,
                                                                 String sendStatus,
                                                                 String reason) {
        LocalDateTime now = LocalDateTime.now();
        AiResumeNotificationDelivery delivery = buildBaseDelivery(failureRecord, recipientAdmin);
        delivery.setChannelCode("manual");
        delivery.setProviderCode("manual");
        delivery.setSendSourceType("manual_admin_record");
        delivery.setSendStatus(sendStatus);
        delivery.setSendRequestedAt(now);
        delivery.setSentAt("send_failed".equals(sendStatus) ? null : now);
        delivery.setSendFailureReason("send_failed".equals(sendStatus) ? normalize(reason) : null);
        delivery.setSendOperatorAdminUserId(operator == null ? null : operator.getAdminUserId());
        delivery.setSendOperatorAdminUserName(operator == null ? null : normalize(operator.getUserName()));
        delivery.setLastRequestId(failureRecord == null ? null : normalize(failureRecord.getRequestId()));
        save(delivery);
        return delivery;
    }

    @Override
    public AiResumeNotificationDelivery recordManualNotificationReceipt(AiResumeFailureRecordDTO failureRecord,
                                                                        AdminAuthenticatedUser operator,
                                                                        AdminUser recipientAdmin,
                                                                        String receiptStatus,
                                                                        String reason) {
        LocalDateTime now = LocalDateTime.now();
        AiResumeNotificationDelivery delivery = latestDelivery(failureRecord == null ? null : failureRecord.getFailureId());
        if (delivery == null) {
            delivery = buildManualReceiptDelivery(failureRecord, recipientAdmin);
        }
        delivery.setReceiptSourceType("manual_admin_receipt");
        delivery.setReceiptStatus(receiptStatus);
        delivery.setReceiptAt("receipt_failed".equals(receiptStatus) ? null : now);
        delivery.setReceiptFailureReason("receipt_failed".equals(receiptStatus) ? normalize(reason) : null);
        delivery.setReceiptOperatorAdminUserId(operator == null ? null : operator.getAdminUserId());
        delivery.setReceiptOperatorAdminUserName(operator == null ? null : normalize(operator.getUserName()));
        delivery.setLastRequestId(failureRecord == null ? null : normalize(failureRecord.getRequestId()));
        if (delivery.getDeliveryId() == null) {
            save(delivery);
            return delivery;
        }
        updateById(delivery);
        return delivery;
    }

    private AiResumeNotificationDelivery latestDelivery(String failureId) {
        if (!StringUtils.hasText(failureId)) {
            return null;
        }
        return lambdaQuery()
                .eq(AiResumeNotificationDelivery::getFailureId, failureId.trim())
                .orderByDesc(AiResumeNotificationDelivery::getCreateTime)
                .orderByDesc(AiResumeNotificationDelivery::getDeliveryId)
                .last("limit 1")
                .one();
    }

    private AiResumeNotificationDelivery buildManualReceiptDelivery(AiResumeFailureRecordDTO failureRecord,
                                                                    AdminUser recipientAdmin) {
        AiResumeNotificationDelivery delivery = buildBaseDelivery(failureRecord, recipientAdmin);
        delivery.setChannelCode("manual");
        delivery.setProviderCode("manual");
        delivery.setSendSourceType("manual_admin_record");
        delivery.setSendStatus(normalize(failureRecord == null ? null : failureRecord.getNotificationStatus()));
        delivery.setSendRequestedAt(parseTime(failureRecord == null ? null : failureRecord.getNotificationSentAt()));
        delivery.setSentAt(parseTime(failureRecord == null ? null : failureRecord.getNotificationSentAt()));
        delivery.setSendFailureReason(normalize(failureRecord == null ? null : failureRecord.getNotificationFailureReason()));
        return delivery;
    }

    private AiResumeNotificationDelivery buildBaseDelivery(AiResumeFailureRecordDTO failureRecord, AdminUser recipientAdmin) {
        AiResumeNotificationDelivery delivery = new AiResumeNotificationDelivery();
        delivery.setFailureId(failureRecord == null ? null : normalize(failureRecord.getFailureId()));
        delivery.setFailureRequestId(failureRecord == null ? null : normalize(failureRecord.getRequestId()));
        delivery.setUserId(failureRecord == null ? null : failureRecord.getUserId());
        delivery.setAssignedAdminUserId(failureRecord == null ? null : failureRecord.getAssignedAdminId());
        delivery.setRecipientType("admin_user");
        delivery.setRecipientAdminUserId(recipientAdmin == null ? null : recipientAdmin.getAdminUserId());
        delivery.setRecipientName(resolveRecipientName(recipientAdmin, failureRecord));
        delivery.setRecipientPhone(recipientAdmin == null ? null : normalize(recipientAdmin.getPhone()));
        delivery.setRecipientEmail(recipientAdmin == null ? null : normalize(recipientAdmin.getEmail()));
        return delivery;
    }

    private String resolveRecipientName(AdminUser recipientAdmin, AiResumeFailureRecordDTO failureRecord) {
        if (recipientAdmin != null && StringUtils.hasText(recipientAdmin.getUserName())) {
            return recipientAdmin.getUserName().trim();
        }
        return failureRecord == null ? null : normalize(failureRecord.getAssignedAdminName());
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), TIME_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
