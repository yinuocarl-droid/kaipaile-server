package com.kaipai.module.server.ai.profilecard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.server.ai.config.AiImageProviderRuntimeConfig;
import com.kaipai.module.server.ai.provider.TencentCloudApiSupport;
import com.kaipai.module.server.ai.service.AiImageProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TencentOcrAiProfileCardImageQualityInspector implements AiProfileCardImageQualityInspector {

    private static final String PROVIDER_CODE = "tencent-hunyuan";
    private static final String SERVICE = "ocr";
    private static final String DEFAULT_ENDPOINT = "https://ocr.tencentcloudapi.com";
    private static final String DEFAULT_REGION = "ap-guangzhou";
    private static final String DEFAULT_VERSION = "2018-11-19";
    private static final int BLOCKED_CONFIDENCE_THRESHOLD = 50;

    private final AiImageProviderConfigService aiImageProviderConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public AiProfileCardImageQualityInspection inspectCover(String imageUrl, String generationProviderCode) {
        if (!StringUtils.hasText(imageUrl)) {
            return AiProfileCardImageQualityInspection.rejected("封面图片 URL 为空");
        }

        AiImageProviderRuntimeConfig runtime = aiImageProviderConfigService.findRuntimeConfig(PROVIDER_CODE)
                .filter(AiImageProviderRuntimeConfig::enabled)
                .orElse(null);
        if (runtime == null) {
            String message = "腾讯 OCR 未配置，封面质检无法执行";
            log.warn(message);
            return AiProfileCardImageQualityInspection.skipped(message);
        }

        String secretId = runtime.secret("secretId");
        String secretKey = runtime.secret("secretKey");
        if (!StringUtils.hasText(secretId) || !StringUtils.hasText(secretKey)) {
            throw new BizException("腾讯 OCR SecretId/SecretKey 未配置");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(runtime.connectTimeoutMs(10000)))
                    .build();
            JsonNode root = callTencent(client, secretId, secretKey, runtime, imageUrl.trim());
            List<String> snippets = extractBlockedSnippets(root);
            if (!snippets.isEmpty()) {
                String reason = "封面成图检测到文字：" + String.join(" | ", snippets);
                return AiProfileCardImageQualityInspection.rejected(reason);
            }
            return AiProfileCardImageQualityInspection.accept();
        } catch (BizException error) {
            if (isOcrUnavailable(error)) {
                String message = "腾讯 OCR 服务不可用，封面质检无法执行";
                log.warn(message + ": {}", truncate(error.getMessage()));
                return AiProfileCardImageQualityInspection.skipped(message);
            }
            throw error;
        } catch (Exception error) {
            throw new BizException("腾讯 OCR 封面质检失败：" + error.getMessage());
        }
    }

    private JsonNode callTencent(HttpClient client,
                                 String secretId,
                                 String secretKey,
                                 AiImageProviderRuntimeConfig runtime,
                                 String imageUrl) throws Exception {
        String endpoint = DEFAULT_ENDPOINT;
        String region = DEFAULT_REGION;
        Map<String, Object> payload = Map.of(
                "ImageUrl", imageUrl,
                "IsPdf", false,
                "LanguageType", "auto"
        );
        String body = objectMapper.writeValueAsString(payload);
        long timestamp = Instant.now().getEpochSecond();
        String authorization = TencentCloudApiSupport.sign(endpoint, SERVICE, secretId, secretKey, "GeneralBasicOCR", timestamp, body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(runtime.readTimeoutMs(120000)))
                .header("Authorization", authorization)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-TC-Action", "GeneralBasicOCR")
                .header("X-TC-Version", DEFAULT_VERSION)
                .header("X-TC-Region", region)
                .header("X-TC-Timestamp", String.valueOf(timestamp))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException("腾讯 OCR API 返回异常：" + response.statusCode() + " " + truncate(response.body()));
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode error = root.at("/Response/Error");
        if (error != null && !error.isMissingNode()) {
            throw new BizException("腾讯 OCR API 错误：" + truncate(error.toString()));
        }
        return root;
    }

    private List<String> extractBlockedSnippets(JsonNode root) {
        JsonNode detections = root.at("/Response/TextDetections");
        if (detections == null || !detections.isArray()) {
            return List.of();
        }
        List<String> snippets = new ArrayList<>();
        for (JsonNode detection : detections) {
            String text = normalizeText(detection.path("DetectedText").asText(""));
            if (!StringUtils.hasText(text)) {
                continue;
            }
            double confidence = detection.path("Confidence").asDouble(100d);
            if (isBlockedText(text, confidence)) {
                snippets.add(truncateSnippet(text));
            }
            if (snippets.size() >= 3) {
                break;
            }
        }
        return snippets;
    }

    private boolean isBlockedText(String text, double confidence) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (confidence < BLOCKED_CONFIDENCE_THRESHOLD) {
            return false;
        }
        String compact = text.replaceAll("\\s+", "");
        if (!StringUtils.hasText(compact)) {
            return false;
        }
        return containsChinese(compact) || containsAsciiWord(compact);
    }

    private boolean containsChinese(String value) {
        for (int index = 0; index < value.length(); index++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(index));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAsciiWord(String value) {
        int alnumCount = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isLetterOrDigit(current) && current < 128) {
                alnumCount++;
            }
        }
        return alnumCount >= 3;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String truncateSnippet(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > 24 ? value.substring(0, 24) : value;
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private boolean isOcrUnavailable(BizException error) {
        if (error == null || !StringUtils.hasText(error.getMessage())) {
            return false;
        }
        String message = error.getMessage();
        return message.contains("FailedOperation.UnOpenError")
                || message.contains("服务未开通")
                || message.contains("未开通");
    }
}
