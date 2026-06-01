package com.kaipai.service.ai.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.ai.dto.AiResumeFailureRecordDTO;
import com.kaipai.model.ai.dto.AiResumeNotificationDispatchResultDTO;
import com.kaipai.model.ai.dto.AiResumeNotificationReceiptCallbackDTO;
import com.kaipai.model.ai.dto.AiResumeNotificationSendCommand;
import com.kaipai.model.ai.dto.AiResumeNotificationSendResultDTO;
import com.kaipai.model.ai.entity.AiResumeNotificationDelivery;
import com.kaipai.model.system.entity.AdminUser;
import com.kaipai.service.ai.config.AiResumeNotificationProperties;
import com.kaipai.integration.ai.provider.AiResumeNotificationProvider;
import com.kaipai.service.ai.AiResumeFailureRecordService;
import com.kaipai.service.ai.AiResumeNotificationDeliveryService;
import com.kaipai.service.ai.AiResumeNotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiResumeNotificationDispatchServiceImpl implements AiResumeNotificationDispatchService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final AiResumeNotificationProperties properties;
    private final List<AiResumeNotificationProvider> providers;
    private final AiResumeNotificationDeliveryService aiResumeNotificationDeliveryService;
    private final AiResumeFailureRecordService aiResumeFailureRecordService;
    private final AdminOperationLogger adminOperationLogger;
    private final ObjectMapper objectMapper;

    @Override
    public AiResumeNotificationDispatchResultDTO dispatch(AiResumeNotificationSendCommand command) {
        AiResumeFailureRecordDTO failureRecord = requireFailureRecord(command);
        AiResumeNotificationSendResultDTO sendResult = resolveSendResult(command);
        AiResumeNotificationDelivery delivery = buildDispatchDelivery(command, sendResult, failureRecord);
        aiResumeNotificationDeliveryService.save(delivery);
        logDispatch(command, delivery, sendResult);
        return buildDispatchResult(delivery);
    }

    @Override
    public AiResumeNotificationDispatchResultDTO ingestReceipt(AiResumeNotificationReceiptCallbackDTO callback) {
        AiResumeNotificationDelivery delivery = resolveReceiptDelivery(callback);
        String receiptStatus = normalizeReceiptStatus(callback == null ? null : callback.getReceiptStatus());
        LocalDateTime receiptAt = resolveReceiptAt(callback, receiptStatus);

        delivery.setReceiptSourceType("provider_callback");
        delivery.setReceiptStatus(receiptStatus);
        delivery.setReceiptAt("receipt_failed".equals(receiptStatus) ? null : receiptAt);
        delivery.setReceiptFailureReason("receipt_failed".equals(receiptStatus)
                ? normalize(callback == null ? null : callback.getReceiptFailureReason())
                : null);
        delivery.setReceiptPayloadJson(toJson(callback == null ? null : callback.getReceiptPayload()));
        if (StringUtils.hasText(callback == null ? null : callback.getRequestId())) {
            delivery.setLastRequestId(callback.getRequestId().trim());
        }
        aiResumeNotificationDeliveryService.updateById(delivery);
        syncFailureReceipt(delivery);
        logReceipt(callback, delivery);
        return buildDispatchResult(delivery);
    }

    private AiResumeFailureRecordDTO requireFailureRecord(AiResumeNotificationSendCommand command) {
        AiResumeFailureRecordDTO failureRecord = command == null ? null : command.getFailureRecord();
        if (failureRecord == null || !StringUtils.hasText(failureRecord.getFailureId())) {
            throw new BizException("AI 通知发送缺少失败样本");
        }
        return failureRecord;
    }

    private AiResumeNotificationSendResultDTO resolveSendResult(AiResumeNotificationSendCommand command) {
        if (!properties.isEnabled()) {
            AiResumeNotificationSendResultDTO result = new AiResumeNotificationSendResultDTO();
            result.setProviderCode(resolveProviderCode());
            result.setChannelCode("manual");
            result.setSendStatus("send_failed");
            result.setSendRequestedAt(LocalDateTime.now());
            result.setSendFailureReason("notification_disabled");
            return result;
        }
        AiResumeNotificationProvider provider = resolveProvider(resolveProviderCode());
        return provider.send(command);
    }

    private AiResumeNotificationProvider resolveProvider(String providerCode) {
        return providers.stream()
                .filter(item -> item.supports(providerCode))
                .findFirst()
                .orElseThrow(() -> new BizException("AI 通知 provider 未配置: " + providerCode));
    }

    private String resolveProviderCode() {
        return StringUtils.hasText(properties.getProviderCode()) ? properties.getProviderCode().trim() : "manual";
    }

    private AiResumeNotificationDelivery buildDispatchDelivery(AiResumeNotificationSendCommand command,
                                                               AiResumeNotificationSendResultDTO sendResult,
                                                               AiResumeFailureRecordDTO failureRecord) {
        LocalDateTime now = LocalDateTime.now();
        AiResumeNotificationDelivery delivery = new AiResumeNotificationDelivery();
        AdminUser recipientAdmin = command == null ? null : command.getRecipientAdmin();
        delivery.setFailureId(normalize(failureRecord.getFailureId()));
        delivery.setFailureRequestId(normalize(failureRecord.getRequestId()));
        delivery.setUserId(failureRecord.getUserId());
        delivery.setAssignedAdminUserId(failureRecord.getAssignedAdminId());
        delivery.setRecipientType("admin_user");
        delivery.setRecipientAdminUserId(recipientAdmin == null ? null : recipientAdmin.getAdminUserId());
        delivery.setRecipientName(resolveRecipientName(recipientAdmin, failureRecord));
        delivery.setRecipientPhone(recipientAdmin == null ? null : normalize(recipientAdmin.getPhone()));
        delivery.setRecipientEmail(recipientAdmin == null ? null : normalize(recipientAdmin.getEmail()));
        delivery.setChannelCode(normalize(sendResult.getChannelCode()));
        delivery.setProviderCode(normalize(sendResult.getProviderCode()));
        delivery.setProviderMessageId(normalize(sendResult.getProviderMessageId()));
        delivery.setSendSourceType(normalize(command == null ? null : command.getSendSourceType()));
        delivery.setSendStatus(normalizeSendStatus(sendResult.getSendStatus()));
        delivery.setSendRequestedAt(sendResult.getSendRequestedAt() == null ? now : sendResult.getSendRequestedAt());
        delivery.setSentAt("send_failed".equals(delivery.getSendStatus()) ? null : sendResult.getSentAt());
        delivery.setSendFailureReason("send_failed".equals(delivery.getSendStatus())
                ? normalize(sendResult.getSendFailureReason())
                : null);
        delivery.setSendOperatorAdminUserId(command == null || command.getOperator() == null
                ? null
                : command.getOperator().getAdminUserId());
        delivery.setSendOperatorAdminUserName(command == null || command.getOperator() == null
                ? null
                : normalize(command.getOperator().getUserName()));
        delivery.setLastRequestId(resolveRequestId(command, failureRecord));
        return delivery;
    }

    private String resolveRequestId(AiResumeNotificationSendCommand command, AiResumeFailureRecordDTO failureRecord) {
        String requestId = command == null ? null : command.getRequestId();
        if (StringUtils.hasText(requestId)) {
            return requestId.trim();
        }
        return normalize(failureRecord == null ? null : failureRecord.getRequestId());
    }

    private AiResumeNotificationDelivery resolveReceiptDelivery(AiResumeNotificationReceiptCallbackDTO callback) {
        if (callback == null) {
            throw new BizException("通知回执不能为空");
        }
        AiResumeNotificationDelivery delivery = null;
        if (StringUtils.hasText(callback.getProviderMessageId())) {
            delivery = aiResumeNotificationDeliveryService.findByProviderMessageId(callback.getProviderMessageId().trim());
        }
        if (delivery == null && StringUtils.hasText(callback.getFailureId())) {
            delivery = aiResumeNotificationDeliveryService.findLatestByFailureId(callback.getFailureId().trim());
        }
        if (delivery == null) {
            throw new BizException("通知投递记录不存在");
        }
        return delivery;
    }

    private void syncFailureReceipt(AiResumeNotificationDelivery delivery) {
        if (delivery == null || !StringUtils.hasText(delivery.getFailureId())) {
            return;
        }
        AiResumeFailureRecordDTO failureRecord = aiResumeFailureRecordService.findFailure(delivery.getFailureId());
        if (failureRecord == null) {
            return;
        }
        failureRecord.setNotificationReceiptStatus(delivery.getReceiptStatus());
        failureRecord.setNotificationReceiptAt(formatTime(delivery.getReceiptAt()));
        failureRecord.setNotificationReceiptFailureReason(delivery.getReceiptFailureReason());
        applyNotificationDeliverySummary(failureRecord, delivery);
        aiResumeFailureRecordService.recordFailure(failureRecord);
    }

    private void applyNotificationDeliverySummary(AiResumeFailureRecordDTO failureRecord,
                                                  AiResumeNotificationDelivery delivery) {
        failureRecord.setNotificationDeliveryId(delivery.getDeliveryId());
        failureRecord.setNotificationSourceType(delivery.getSendSourceType());
        failureRecord.setNotificationChannelCode(delivery.getChannelCode());
        failureRecord.setNotificationRecipient(resolveDeliveryRecipient(delivery));
        failureRecord.setNotificationProviderCode(delivery.getProviderCode());
        failureRecord.setNotificationProviderMessageId(delivery.getProviderMessageId());
        failureRecord.setNotificationReceiptSourceType(delivery.getReceiptSourceType());
    }

    private String resolveDeliveryRecipient(AiResumeNotificationDelivery delivery) {
        if (StringUtils.hasText(delivery.getRecipientName()) && StringUtils.hasText(delivery.getRecipientPhone())) {
            return delivery.getRecipientName().trim() + " / " + delivery.getRecipientPhone().trim();
        }
        if (StringUtils.hasText(delivery.getRecipientName()) && StringUtils.hasText(delivery.getRecipientEmail())) {
            return delivery.getRecipientName().trim() + " / " + delivery.getRecipientEmail().trim();
        }
        if (StringUtils.hasText(delivery.getRecipientPhone())) {
            return delivery.getRecipientPhone().trim();
        }
        if (StringUtils.hasText(delivery.getRecipientEmail())) {
            return delivery.getRecipientEmail().trim();
        }
        return normalize(delivery.getRecipientName());
    }

    private String resolveRecipientName(AdminUser recipientAdmin, AiResumeFailureRecordDTO failureRecord) {
        if (recipientAdmin != null && StringUtils.hasText(recipientAdmin.getUserName())) {
            return recipientAdmin.getUserName().trim();
        }
        return normalize(failureRecord == null ? null : failureRecord.getAssignedAdminName());
    }

    private LocalDateTime resolveReceiptAt(AiResumeNotificationReceiptCallbackDTO callback, String receiptStatus) {
        if ("receipt_failed".equals(receiptStatus)) {
            return null;
        }
        LocalDateTime parsed = parseTime(callback == null ? null : callback.getReceiptAt());
        return parsed == null ? LocalDateTime.now() : parsed;
    }

    private String normalizeSendStatus(String sendStatus) {
        String normalized = normalize(sendStatus);
        return StringUtils.hasText(normalized) ? normalized : "send_failed";
    }

    private String normalizeReceiptStatus(String receiptStatus) {
        String normalized = normalize(receiptStatus);
        if (!StringUtils.hasText(normalized)) {
            return "delivered";
        }
        if ("delivered".equals(normalized) || "receipt_failed".equals(normalized) || "received".equals(normalized)) {
            return normalized;
        }
        throw new BizException("通知回执结果仅支持 delivered、received、receipt_failed");
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), TIME_FORMATTER);
        } catch (Exception e) {
            throw new BizException("receiptAt 仅支持 yyyy-MM-dd'T'HH:mm:ss 格式");
        }
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? null : value.format(TIME_FORMATTER);
    }

    private AiResumeNotificationDispatchResultDTO buildDispatchResult(AiResumeNotificationDelivery delivery) {
        AiResumeNotificationDispatchResultDTO result = new AiResumeNotificationDispatchResultDTO();
        result.setDelivery(delivery);
        result.setSendStatus(delivery == null ? null : delivery.getSendStatus());
        result.setReceiptStatus(delivery == null ? null : delivery.getReceiptStatus());
        result.setFailureReason(delivery == null ? null
                : StringUtils.hasText(delivery.getReceiptFailureReason())
                ? delivery.getReceiptFailureReason()
                : delivery.getSendFailureReason());
        return result;
    }

    private void logDispatch(AiResumeNotificationSendCommand command,
                             AiResumeNotificationDelivery delivery,
                             AiResumeNotificationSendResultDTO sendResult) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("failure_id", delivery.getFailureId());
        context.put("send_source_type", delivery.getSendSourceType());
        context.put("channel_code", delivery.getChannelCode());
        context.put("provider_code", delivery.getProviderCode());
        context.put("provider_message_id", delivery.getProviderMessageId());
        context.put("send_status", delivery.getSendStatus());
        context.put("send_failure_reason", delivery.getSendFailureReason());
        context.put("recipient_name", delivery.getRecipientName());
        context.put("recipient_phone", delivery.getRecipientPhone());
        context.put("recipient_email", delivery.getRecipientEmail());
        context.put("reason", command == null ? null : command.getReason());
        context.put("provider_result", sendResult);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .requestId(command == null ? null : command.getRequestId())
                .moduleCode("system")
                .operationCode("ai_resume_notification_dispatch")
                .targetType("ai_resume_notification_delivery")
                .targetId(delivery.getDeliveryId())
                .afterSnapshot(delivery)
                .extraContext(context)
                .operationResult("send_failed".equals(delivery.getSendStatus()) ? 0 : 1)
                .failReason(delivery.getSendFailureReason())
                .build());
    }

    private void logReceipt(AiResumeNotificationReceiptCallbackDTO callback, AiResumeNotificationDelivery delivery) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("failure_id", delivery.getFailureId());
        context.put("provider_code", delivery.getProviderCode());
        context.put("provider_message_id", delivery.getProviderMessageId());
        context.put("receipt_status", delivery.getReceiptStatus());
        context.put("receipt_failure_reason", delivery.getReceiptFailureReason());
        context.put("receipt_source_type", delivery.getReceiptSourceType());
        context.put("receipt_payload", callback == null ? null : callback.getReceiptPayload());

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .requestId(callback == null ? null : callback.getRequestId())
                .moduleCode("system")
                .operationCode("ai_resume_notification_receipt_callback")
                .targetType("ai_resume_notification_delivery")
                .targetId(delivery.getDeliveryId())
                .afterSnapshot(delivery)
                .extraContext(context)
                .operationResult("receipt_failed".equals(delivery.getReceiptStatus()) ? 0 : 1)
                .failReason(delivery.getReceiptFailureReason())
                .build());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("serialize ai notification receipt payload failed", e);
            return String.valueOf(value);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
