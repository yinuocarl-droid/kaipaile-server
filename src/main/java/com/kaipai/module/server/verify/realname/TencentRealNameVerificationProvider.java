package com.kaipai.module.server.verify.realname;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TencentRealNameVerificationProvider {

    private static final String SERVICE = "faceid";
    private static final String ACTION_ID_CARD_OCR_VERIFICATION = "IdCardOCRVerification";
    private static final Set<String> DEFINITIVE_FAILURE_CODES = Set.of("-1", "-2", "-3");

    private final RealNameVerificationProperties properties;
    private final ObjectMapper objectMapper;

    public RealNameVerificationResult verify(RealNameVerificationCommand command) {
        RealNameVerificationProperties.Tencent tencent = properties.getTencent();
        requireTencentConfig(tencent);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(1000, tencent.getConnectTimeoutMs())))
                    .build();
            Map<String, Object> payload = buildPayload(command);
            JsonNode root = callTencent(client, tencent, payload);
            JsonNode response = root.path("Response");
            String requestId = response.path("RequestId").asText("");
            String resultCode = response.path("Result").asText("");
            String resultMessage = response.path("Description").asText("");
            if ("0".equals(resultCode)) {
                return RealNameVerificationResult.matched("tencent", requestId, resultCode, resultMessage);
            }
            if (DEFINITIVE_FAILURE_CODES.contains(resultCode)) {
                return RealNameVerificationResult.mismatch("tencent", requestId, resultCode, resultMessage);
            }
            return RealNameVerificationResult.manualReview("tencent", requestId, resultCode, resultMessage);
        } catch (BizException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException("腾讯云实名核验被中断");
        } catch (Exception error) {
            throw new BizException("腾讯云实名核验失败：" + compact(error.getMessage(), 160));
        }
    }

    private void requireTencentConfig(RealNameVerificationProperties.Tencent tencent) {
        if (tencent == null
                || !StringUtils.hasText(tencent.getEndpoint())
                || !StringUtils.hasText(tencent.getVersion())
                || !StringUtils.hasText(tencent.getSecretId())
                || !StringUtils.hasText(tencent.getSecretKey())) {
            throw new BizException("腾讯云实名核验配置未完成");
        }
    }

    private Map<String, Object> buildPayload(RealNameVerificationCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Name", command.realName());
        payload.put("IdCard", command.idCardNo());
        return payload;
    }

    private JsonNode callTencent(HttpClient client,
                                 RealNameVerificationProperties.Tencent tencent,
                                 Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        long timestamp = Instant.now().getEpochSecond();
        String authorization = TencentCloudApiSupport.sign(
                tencent.getEndpoint(),
                SERVICE,
                tencent.getSecretId(),
                tencent.getSecretKey(),
                ACTION_ID_CARD_OCR_VERIFICATION,
                timestamp,
                body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tencent.getEndpoint()))
                .timeout(Duration.ofMillis(Math.max(1000, tencent.getReadTimeoutMs())))
                .header("Authorization", authorization)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-TC-Action", ACTION_ID_CARD_OCR_VERIFICATION)
                .header("X-TC-Version", tencent.getVersion())
                .header("X-TC-Timestamp", String.valueOf(timestamp))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException("腾讯云实名核验 API 返回异常：" + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode error = root.at("/Response/Error");
        if (error != null && !error.isMissingNode()) {
            String code = error.path("Code").asText("");
            String message = error.path("Message").asText("");
            throw new BizException("腾讯云实名核验 API 错误：" + compact(code + " " + message, 160));
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
