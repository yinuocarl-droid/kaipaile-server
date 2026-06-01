package com.kaipai.module.server.auth.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.server.ai.provider.TencentCloudApiSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TencentSmsCodeSender {

    private static final String SERVICE = "sms";
    private static final String ACTION_SEND_SMS = "SendSms";

    private final SmsProperties smsProperties;
    private final ObjectMapper objectMapper;

    public SmsCodeSendResult sendCode(SmsCodeSendCommand command) {
        SmsProperties.Tencent tencent = smsProperties.getTencent();
        requireTencentConfig(tencent);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(1000, tencent.getConnectTimeoutMs())))
                    .build();
            Map<String, Object> payload = buildPayload(tencent, command);
            JsonNode root = callTencent(client, tencent, payload);
            JsonNode response = root.path("Response");
            JsonNode sendStatusSet = response.path("SendStatusSet");
            if (!sendStatusSet.isArray() || sendStatusSet.size() == 0) {
                throw new BizException("腾讯云短信未返回发送状态");
            }
            JsonNode firstStatus = sendStatusSet.get(0);
            String code = firstStatus.path("Code").asText("");
            String message = firstStatus.path("Message").asText("");
            String serialNo = firstStatus.path("SerialNo").asText("");
            String requestId = response.path("RequestId").asText("");
            if (!"Ok".equalsIgnoreCase(code) && !"OK".equalsIgnoreCase(code)) {
                throw new BizException("腾讯云短信发送失败：" + compact(message, 160));
            }
            return SmsCodeSendResult.success("tencent", requestId, serialNo, code, message);
        } catch (BizException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException("腾讯云短信发送被中断");
        } catch (Exception error) {
            throw new BizException("腾讯云短信发送失败：" + compact(error.getMessage(), 160));
        }
    }

    private void requireTencentConfig(SmsProperties.Tencent tencent) {
        if (tencent == null
                || !StringUtils.hasText(tencent.getEndpoint())
                || !StringUtils.hasText(tencent.getRegion())
                || !StringUtils.hasText(tencent.getVersion())
                || !StringUtils.hasText(tencent.getSecretId())
                || !StringUtils.hasText(tencent.getSecretKey())
                || !StringUtils.hasText(tencent.getSmsSdkAppId())
                || !StringUtils.hasText(tencent.getSignName())
                || !StringUtils.hasText(tencent.getTemplateId())) {
            throw new BizException("腾讯云短信配置未完成");
        }
    }

    private Map<String, Object> buildPayload(SmsProperties.Tencent tencent, SmsCodeSendCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("SmsSdkAppId", tencent.getSmsSdkAppId().trim());
        payload.put("SignName", tencent.getSignName().trim());
        payload.put("TemplateId", tencent.getTemplateId().trim());
        payload.put("TemplateParamSet", buildTemplateParams(tencent, command));
        payload.put("PhoneNumberSet", List.of("+86" + command.phone()));
        payload.put("SessionContext", StringUtils.hasText(command.scene()) ? command.scene().trim() : "login");
        return payload;
    }

    private List<String> buildTemplateParams(SmsProperties.Tencent tencent, SmsCodeSendCommand command) {
        List<String> params = new ArrayList<>();
        params.add(command.code());
        String mode = StringUtils.hasText(tencent.getTemplateParamMode()) ? tencent.getTemplateParamMode().trim() : "code_ttl";
        if ("code_ttl".equalsIgnoreCase(mode)) {
            params.add(String.valueOf(command.expireMinutes()));
        }
        return params;
    }

    private JsonNode callTencent(HttpClient client, SmsProperties.Tencent tencent, Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        long timestamp = Instant.now().getEpochSecond();
        String authorization = TencentCloudApiSupport.sign(
                tencent.getEndpoint(),
                SERVICE,
                tencent.getSecretId(),
                tencent.getSecretKey(),
                ACTION_SEND_SMS,
                timestamp,
                body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tencent.getEndpoint()))
                .timeout(Duration.ofMillis(Math.max(1000, tencent.getReadTimeoutMs())))
                .header("Authorization", authorization)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-TC-Action", ACTION_SEND_SMS)
                .header("X-TC-Version", tencent.getVersion())
                .header("X-TC-Region", tencent.getRegion())
                .header("X-TC-Timestamp", String.valueOf(timestamp))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException("腾讯云短信 API 返回异常：" + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode error = root.at("/Response/Error");
        if (error != null && !error.isMissingNode()) {
            String code = error.path("Code").asText("");
            String message = error.path("Message").asText("");
            throw new BizException("腾讯云短信 API 错误：" + compact(code + " " + message, 160));
        }
        return root;
    }

    private String compact(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
