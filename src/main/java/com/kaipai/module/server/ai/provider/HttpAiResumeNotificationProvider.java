package com.kaipai.module.server.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.module.model.ai.dto.AiResumeFailureRecordDTO;
import com.kaipai.module.model.ai.dto.AiResumeNotificationSendCommand;
import com.kaipai.module.model.ai.dto.AiResumeNotificationSendResultDTO;
import com.kaipai.module.model.system.entity.AdminUser;
import com.kaipai.module.server.ai.config.AiResumeNotificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HttpAiResumeNotificationProvider implements AiResumeNotificationProvider {

    private final AiResumeNotificationProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return "http";
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
        String requestChannelCode = StringUtils.hasText(recipientPhone) ? "sms"
                : StringUtils.hasText(recipientEmail) ? "email" : "http";
        result.setChannelCode(requestChannelCode);

        if (!StringUtils.hasText(recipientPhone) && !StringUtils.hasText(recipientEmail)) {
            result.setSendStatus("send_failed");
            result.setSendFailureReason("recipient_contact_missing");
            return result;
        }

        String endpoint = normalize(properties.getHttp() == null ? null : properties.getHttp().getEndpoint());
        if (!StringUtils.hasText(endpoint)) {
            result.setSendStatus("send_failed");
            result.setSendFailureReason("provider_http_endpoint_missing");
            return result;
        }

        URI endpointUri;
        try {
            endpointUri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            result.setSendStatus("send_failed");
            result.setSendFailureReason("provider_http_endpoint_invalid");
            return result;
        }

        try {
            String requestBody = objectMapper.writeValueAsString(buildRequestPayload(command, recipientAdmin));
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpointUri)
                    .timeout(Duration.ofMillis(resolveReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
            String authHeader = normalize(properties.getHttp() == null ? null : properties.getHttp().getAuthHeader());
            String authToken = normalize(properties.getHttp() == null ? null : properties.getHttp().getAuthToken());
            if (StringUtils.hasText(authHeader) && StringUtils.hasText(authToken)) {
                requestBuilder.header(authHeader, authToken);
            }

            HttpResponse<String> response = buildHttpClient().send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            return resolveResponseResult(response, result, now, requestChannelCode);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            result.setSendStatus("send_failed");
            result.setSendFailureReason("provider_http_request_exception");
            return result;
        }
    }

    private HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(resolveConnectTimeoutMs()))
                .build();
    }

    private int resolveConnectTimeoutMs() {
        int configured = properties.getHttp() == null ? 0 : properties.getHttp().getConnectTimeoutMs();
        return configured > 0 ? configured : 5000;
    }

    private int resolveReadTimeoutMs() {
        int configured = properties.getHttp() == null ? 0 : properties.getHttp().getReadTimeoutMs();
        return configured > 0 ? configured : 10000;
    }

    private Map<String, Object> buildRequestPayload(AiResumeNotificationSendCommand command, AdminUser recipientAdmin) {
        Map<String, Object> payload = new LinkedHashMap<>();
        AiResumeFailureRecordDTO failureRecord = command == null ? null : command.getFailureRecord();
        payload.put("requestId", normalize(command == null ? null : command.getRequestId()));
        payload.put("sendSourceType", normalize(command == null ? null : command.getSendSourceType()));
        payload.put("reason", normalize(command == null ? null : command.getReason()));
        payload.put("callbackHeader", normalize(properties.getCallbackHeader()));
        payload.put("callbackToken", normalize(properties.getCallbackToken()));
        payload.put("callbackUrl", normalize(properties.getCallbackUrl()));
        payload.put("failure", buildFailurePayload(failureRecord));
        payload.put("recipient", buildRecipientPayload(recipientAdmin, failureRecord));
        return payload;
    }

    private Map<String, Object> buildFailurePayload(AiResumeFailureRecordDTO failureRecord) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (failureRecord == null) {
            return payload;
        }
        payload.put("failureId", normalize(failureRecord.getFailureId()));
        payload.put("failureRequestId", normalize(failureRecord.getRequestId()));
        payload.put("userId", failureRecord.getUserId());
        payload.put("assignedAdminUserId", failureRecord.getAssignedAdminId());
        payload.put("assignedAdminName", normalize(failureRecord.getAssignedAdminName()));
        payload.put("failureType", normalize(failureRecord.getFailureType()));
        payload.put("errorCode", failureRecord.getErrorCode());
        payload.put("errorMessage", normalize(failureRecord.getErrorMessage()));
        payload.put("instruction", normalize(failureRecord.getInstruction()));
        payload.put("createdAt", normalize(failureRecord.getCreatedAt()));
        return payload;
    }

    private Map<String, Object> buildRecipientPayload(AdminUser recipientAdmin, AiResumeFailureRecordDTO failureRecord) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("adminUserId", recipientAdmin == null ? null : recipientAdmin.getAdminUserId());
        payload.put("name", resolveRecipientName(recipientAdmin, failureRecord));
        payload.put("phone", normalize(recipientAdmin == null ? null : recipientAdmin.getPhone()));
        payload.put("email", normalize(recipientAdmin == null ? null : recipientAdmin.getEmail()));
        return payload;
    }

    private String resolveRecipientName(AdminUser recipientAdmin, AiResumeFailureRecordDTO failureRecord) {
        if (recipientAdmin != null && StringUtils.hasText(recipientAdmin.getUserName())) {
            return recipientAdmin.getUserName().trim();
        }
        return normalize(failureRecord == null ? null : failureRecord.getAssignedAdminName());
    }

    private AiResumeNotificationSendResultDTO resolveResponseResult(HttpResponse<String> response,
                                                                    AiResumeNotificationSendResultDTO baseResult,
                                                                    LocalDateTime now,
                                                                    String requestChannelCode) {
        AiResumeNotificationSendResultDTO result = baseResult;
        result.setChannelCode(requestChannelCode);
        if (response == null) {
            result.setSendStatus("send_failed");
            result.setSendFailureReason("provider_http_response_missing");
            return result;
        }

        String responseBody = response.body();
        JsonNode bodyNode = parseJsonSafely(responseBody);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            result.setSendStatus("send_failed");
            result.setSendFailureReason(resolveFailureReason(bodyNode, "provider_http_status_" + response.statusCode()));
            result.setProviderMessageId(resolveProviderMessageId(bodyNode));
            result.setChannelCode(resolveChannelCode(bodyNode, requestChannelCode));
            return result;
        }

        String explicitStatus = normalize(resolveText(bodyNode, "sendStatus", "status"));
        boolean success = resolveSuccess(bodyNode, explicitStatus);
        result.setProviderMessageId(resolveProviderMessageId(bodyNode));
        result.setChannelCode(resolveChannelCode(bodyNode, requestChannelCode));
        if (!success) {
            result.setSendStatus("send_failed");
            result.setSendFailureReason(resolveFailureReason(bodyNode, "provider_http_send_failed"));
            return result;
        }

        result.setSendStatus("sent");
        result.setSentAt(now);
        return result;
    }

    private JsonNode parseJsonSafely(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean resolveSuccess(JsonNode bodyNode, String explicitStatus) {
        if ("send_failed".equals(explicitStatus)) {
            return false;
        }
        if ("sent".equals(explicitStatus)) {
            return true;
        }
        if (bodyNode == null || bodyNode.isMissingNode() || bodyNode.isNull()) {
            return true;
        }
        JsonNode successNode = firstNode(bodyNode, "success");
        if (successNode != null && successNode.isBoolean()) {
            return successNode.booleanValue();
        }
        String code = normalize(resolveText(bodyNode, "code", "statusCode"));
        if (!StringUtils.hasText(code)) {
            return true;
        }
        return "200".equals(code)
                || "0".equals(code)
                || "ok".equalsIgnoreCase(code)
                || "success".equalsIgnoreCase(code);
    }

    private String resolveFailureReason(JsonNode bodyNode, String defaultReason) {
        String message = normalize(resolveText(bodyNode, "failureReason", "error", "message"));
        return StringUtils.hasText(message) ? message : defaultReason;
    }

    private String resolveProviderMessageId(JsonNode bodyNode) {
        return normalize(resolveText(bodyNode, "providerMessageId", "messageId", "taskId", "bizId", "id"));
    }

    private String resolveChannelCode(JsonNode bodyNode, String requestChannelCode) {
        String channelCode = normalize(resolveText(bodyNode, "channelCode", "channel"));
        return StringUtils.hasText(channelCode) ? channelCode : requestChannelCode;
    }

    private String resolveText(JsonNode bodyNode, String... keys) {
        JsonNode node = firstNode(bodyNode, keys);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return null;
    }

    private JsonNode firstNode(JsonNode bodyNode, String... keys) {
        if (bodyNode == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            JsonNode candidate = bodyNode.get(key);
            if (candidate != null && !candidate.isMissingNode() && !candidate.isNull()) {
                return candidate;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
