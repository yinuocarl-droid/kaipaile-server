package com.kaipai.integration.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.config.TencentCloudProperties;
import com.kaipai.integration.ai.provider.TencentCloudApiSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TencentIdCardVerificationClient {

    private static final String SERVICE = "faceid";
    private static final String ACTION = "IdCardVerification";

    private final TencentIdCardVerificationProperties properties;
    private final TencentCloudProperties tencentCloudProperties;
    private final ObjectMapper objectMapper;

    public boolean enabled() {
        return properties.isEnabled();
    }

    public TencentIdCardVerificationResult verify(String realName, String idCardNo) {
        requireConfigured();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getConnectTimeoutMs())))
                    .build();
            String body = objectMapper.writeValueAsString(Map.of(
                    "Name", realName,
                    "IdCard", idCardNo
            ));
            long timestamp = Instant.now().getEpochSecond();
            String authorization = TencentCloudApiSupport.sign(
                    properties.getEndpoint(),
                    SERVICE,
                    resolveSecretId().trim(),
                    resolveSecretKey().trim(),
                    ACTION,
                    timestamp,
                    body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEndpoint()))
                    .timeout(Duration.ofMillis(Math.max(1000, properties.getReadTimeoutMs())))
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-TC-Action", ACTION)
                    .header("X-TC-Version", properties.getVersion())
                    .header("X-TC-Timestamp", String.valueOf(timestamp))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("腾讯云身份证核验 API 返回异常：" + response.statusCode() + " " + truncate(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode error = root.at("/Response/Error");
            if (error != null && !error.isMissingNode()) {
                throw new BizException("腾讯云身份证核验 API 错误：" + truncate(error.toString()));
            }
            JsonNode responseNode = root.path("Response");
            String resultCode = responseNode.path("Result").asText("");
            if (!StringUtils.hasText(resultCode)) {
                throw new BizException("腾讯云身份证核验 API 未返回 Result：" + truncate(response.body()));
            }
            return new TencentIdCardVerificationResult(
                    resultCode,
                    responseNode.path("Description").asText(""),
                    responseNode.path("RequestId").asText("")
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException("腾讯云身份证核验请求被中断");
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("腾讯云身份证核验调用失败：" + error.getMessage());
        }
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(resolveSecretId()) || !StringUtils.hasText(resolveSecretKey())) {
            throw new BizException("腾讯云身份证核验 SecretId/SecretKey 未配置");
        }
        if (!StringUtils.hasText(properties.getEndpoint())) {
            throw new BizException("腾讯云身份证核验 endpoint 未配置");
        }
        if (!StringUtils.hasText(properties.getVersion())) {
            throw new BizException("腾讯云身份证核验 version 未配置");
        }
    }

    private String resolveSecretId() {
        return StringUtils.hasText(properties.getSecretId())
                ? properties.getSecretId()
                : tencentCloudProperties.getSecretId();
    }

    private String resolveSecretKey() {
        return StringUtils.hasText(properties.getSecretKey())
                ? properties.getSecretKey()
                : tencentCloudProperties.getSecretKey();
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
