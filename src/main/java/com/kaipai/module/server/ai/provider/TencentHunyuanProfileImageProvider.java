package com.kaipai.module.server.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.server.ai.config.AiImageProviderRuntimeConfig;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;
import com.kaipai.module.server.ai.service.AiImageProviderConfigService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TencentHunyuanProfileImageProvider implements AiProfileImageProvider {

    private static final String SERVICE = "aiart";
    private static final String DEFAULT_ENDPOINT = "https://aiart.tencentcloudapi.com";
    private static final String DEFAULT_REGION = "ap-guangzhou";
    private static final String DEFAULT_VERSION = "2022-12-29";
    private static final int MAX_PROMPT_LENGTH = 4096;

    private final AiImageProviderConfigService aiImageProviderConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return "tencent-hunyuan";
    }

    @Override
    public String modelCode() {
        return aiImageProviderConfigService.resolveModelCode(providerCode(), "hunyuan-image");
    }

    @Override
    public AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request) {
        AiImageProviderRuntimeConfig runtime = requireRuntimeConfig();
        String secretId = runtime.secret("secretId");
        String secretKey = runtime.secret("secretKey");
        if (!StringUtils.hasText(secretId) || !StringUtils.hasText(secretKey)) {
            throw new BizException("腾讯混元 SecretId/SecretKey 未配置");
        }
        String endpoint = runtime.endpoint(DEFAULT_ENDPOINT);
        String region = runtime.region(DEFAULT_REGION);
        String version = StringUtils.hasText(runtime.publicConfig().getModelVersion())
                ? runtime.publicConfig().getModelVersion().trim()
                : DEFAULT_VERSION;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(runtime.connectTimeoutMs(10000)))
                    .build();
            String sourceImageUrl = normalizeSourceImageUrl(request.sourceImageUrl());
            boolean hasSourceImage = sourceImageUrl != null;
            if (hasSourceImage) {
                ProfileImageProviderHttpSupport.downloadSourceImage(client, sourceImageUrl, runtime.readTimeoutMs(120000));
            }

            Map<String, Object> submitPayload = buildSubmitPayload(request, runtime, sourceImageUrl, hasSourceImage);
            JsonNode submitted = callTencent(client, endpoint, region, version, secretId, secretKey,
                    "SubmitTextToImageJob", submitPayload, runtime.readTimeoutMs(120000));
            String jobId = ProfileImageProviderHttpSupport.firstText(submitted, "/Response/JobId", "/JobId");
            if (!StringUtils.hasText(jobId)) {
                AiProfileImageGenerationResult immediate = ProfileImageProviderHttpSupport.parseCommonResult(submitted, endpoint);
                if (immediate != null) {
                    return immediate;
                }
                throw new BizException("腾讯混元未返回 JobId：" + truncate(submitted.toString()));
            }
            return pollJob(client, endpoint, region, version, secretId, secretKey, jobId.trim(), runtime);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException("腾讯混元任务轮询被中断");
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("腾讯混元图片生成调用失败：" + error.getMessage());
        }
    }

    private Map<String, Object> buildSubmitPayload(AiProfileImageGenerationRequest request,
                                                   AiImageProviderRuntimeConfig runtime,
                                                   String sourceImageUrl,
                                                   boolean hasSourceImage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Prompt", buildTencentPrompt(request, hasSourceImage));
        String resolution = StringUtils.hasText(runtime.publicConfig().getResolution())
                ? runtime.publicConfig().getResolution().trim()
                : runtime.size("720:1280").replace("x", ":");
        payload.put("Resolution", resolution);
        if (hasSourceImage) {
            payload.put("Images", List.of(sourceImageUrl));
        }
        payload.put("LogoAdd", runtime.watermark(false) ? 1 : 0);
        payload.put("Revise", Boolean.FALSE.equals(runtime.publicConfig().getPromptRewrite()) ? 0 : 1);
        return payload;
    }

    private String buildTencentPrompt(AiProfileImageGenerationRequest request, boolean hasSourceImage) {
        String styleCode = StringUtils.hasText(request.styleCode()) ? request.styleCode().trim() : "";
        String templateSceneCode = StringUtils.hasText(request.templateSceneCode()) ? request.templateSceneCode().trim() : "";
        JsonNode fixedLayout = parsePromptJson(request.promptJson()).path("fixedLayout");
        String pageType = layoutText(fixedLayout, "pageType", "cover");
        boolean coverPage = "cover".equals(pageType);
        String background = tencentBackgroundHint(templateSceneCode, styleCode, layoutText(fixedLayout, "background", ""));
        if (!coverPage) {
            String prompt = """
                    Create a premium 9:16 full-bleed editorial background for %s, output 2160x3840.
                    No actor portrait or human subject. Keep the image open for later mini-program overlays.
                    Style: %s. Background: %s.
                    Plain, unmarked, symbol-free. No readable text, pseudo-text, logos, watermarks, labels, stamps, UI shapes, or QR patterns.
                    """.formatted(
                    pageType,
                    tencentStyleHint(templateSceneCode, styleCode),
                    background
            ).trim().replaceAll("\\s+", " ");
            return truncatePrompt(prompt);
        }
        String identityPolicy = hasSourceImage
                ? "Use the reference image only for identity."
                : "Create a realistic actor portrait.";
        String prompt = """
                Create a premium 9:16 full-bleed actor portrait background, output 2160x3840.
                %s
                Composition: actor on the right, left side open for later overlays.
                Style: %s. Background: %s.
                Plain, unmarked, symbol-free. No readable text, pseudo-text, logos, watermarks, labels, stamps, UI shapes, or QR patterns.
                """.formatted(
                identityPolicy,
                tencentStyleHint(templateSceneCode, styleCode),
                background
        ).trim().replaceAll("\\s+", " ");
        return truncatePrompt(prompt);
    }

    private JsonNode parsePromptJson(String promptJson) {
        if (!StringUtils.hasText(promptJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(promptJson);
        } catch (Exception error) {
            return objectMapper.createObjectNode();
        }
    }

    private String layoutText(JsonNode fixedLayout, String field, String fallback) {
        if (fixedLayout == null || fixedLayout.isMissingNode()) {
            return sanitizeForTencentPrompt(fallback);
        }
        String value = fixedLayout.path(field).asText("");
        return sanitizeForTencentPrompt(StringUtils.hasText(value) ? value.trim() : fallback);
    }

    private String compactText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String tencentStyleHint(String templateSceneCode, String styleCode) {
        String normalized = (styleCode + " " + templateSceneCode).toLowerCase(Locale.ROOT);
        if (normalized.contains("costume")) {
            return "Elegant period-drama portrait, warm ink texture, realistic costume, restrained premium mood.";
        }
        if (normalized.contains("urban")) {
            return "Modern urban portrait, soft studio depth, polished fashion mood.";
        }
        if (normalized.contains("classic")) {
            return "Clean studio portrait, bright neutral background, approachable and realistic.";
        }
        return "Premium actor portrait, realistic and polished.";
    }

    private String tencentBackgroundHint(String templateSceneCode, String styleCode, String background) {
        String normalized = (styleCode + " " + templateSceneCode).toLowerCase(Locale.ROOT);
        if (normalized.contains("costume")) {
            return "warm ivory ink texture, misty garden depth, bridge and bamboo silhouettes, soft red accents";
        }
        if (normalized.contains("urban")) {
            return "soft city or studio depth, charcoal gradients, restrained cool rim light";
        }
        if (normalized.contains("commercial")) {
            return "premium studio backdrop, soft grey or white gradients";
        }
        if (normalized.contains("artistic")) {
            return "textured gallery wall, controlled shadows, muted olive and rust accents";
        }
        if (StringUtils.hasText(background)) {
            return compactText(sanitizeForTencentPrompt(background), 130);
        }
        return "warm studio texture, soft film grain, neutral matte surfaces";
    }

    private String sanitizeForTencentPrompt(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replace("text-safe", "overlay-safe")
                .replace("title-safe", "top overlay")
                .replace("abstract seal accents", "abstract warm-red motifs")
                .replace("abstract seal shapes", "abstract warm-red motifs")
                .replace("seal-script", "decorative")
                .replace("calligraphy", "decorative strokes")
                .replace("stamp marks", "marked emblems")
                .replace("readable characters", "recognizable symbols")
                .replace("readable text", "recognizable typography")
                .replaceAll("\\s+", " ");
    }

    private String truncatePrompt(String prompt) {
        if (!StringUtils.hasText(prompt) || prompt.length() <= MAX_PROMPT_LENGTH) {
            return prompt;
        }
        return prompt.substring(0, MAX_PROMPT_LENGTH);
    }

    private String normalizeSourceImageUrl(String sourceImageUrl) {
        return StringUtils.hasText(sourceImageUrl) ? sourceImageUrl.trim() : null;
    }

    private AiProfileImageGenerationResult pollJob(HttpClient client,
                                                   String endpoint,
                                                   String region,
                                                   String version,
                                                   String secretId,
                                                   String secretKey,
                                                   String jobId,
                                                   AiImageProviderRuntimeConfig runtime) throws Exception {
        int attempts = Math.max(1, runtime.maxPollAttempts(240));
        int intervalMs = Math.max(500, runtime.pollIntervalMs(1500));
        for (int index = 0; index < attempts; index++) {
            Thread.sleep(intervalMs);
            JsonNode task = callTencent(client, endpoint, region, version, secretId, secretKey,
                    "QueryTextToImageJob", Map.of("JobId", jobId), runtime.readTimeoutMs(120000));
            String status = ProfileImageProviderHttpSupport.firstText(task,
                    "/Response/JobStatusCode",
                    "/Response/JobStatus",
                    "/JobStatusCode");
            if ("5".equals(status) || "DONE".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status)) {
                AiProfileImageGenerationResult result = ProfileImageProviderHttpSupport.parseCommonResult(task, endpoint);
                if (result != null) {
                    return result;
                }
                throw new BizException("腾讯混元任务完成但未返回图片");
            }
            if ("4".equals(status) || "6".equals(status) || "7".equals(status)
                    || "FAILED".equalsIgnoreCase(status) || "FAIL".equalsIgnoreCase(status)) {
                throw new BizException("腾讯混元任务失败：" + truncate(task.toString()));
            }
        }
        throw new BizException("腾讯混元任务超过等待时间");
    }

    private JsonNode callTencent(HttpClient client,
                                 String endpoint,
                                 String region,
                                 String version,
                                 String secretId,
                                 String secretKey,
                                 String action,
                                 Map<String, Object> payload,
                                 int readTimeoutMs) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        long timestamp = Instant.now().getEpochSecond();
        String authorization = TencentCloudApiSupport.sign(endpoint, SERVICE, secretId, secretKey, action, timestamp, body);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(readTimeoutMs))
                .header("Authorization", authorization)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-TC-Action", action)
                .header("X-TC-Version", version)
                .header("X-TC-Region", region)
                .header("X-TC-Timestamp", String.valueOf(timestamp))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException("腾讯混元 API 返回异常：" + response.statusCode() + " " + truncate(response.body()));
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode error = root.at("/Response/Error");
        if (error != null && !error.isMissingNode()) {
            throw new BizException("腾讯混元 API 错误：" + truncate(error.toString()));
        }
        return root;
    }

    private AiImageProviderRuntimeConfig requireRuntimeConfig() {
        return aiImageProviderConfigService.findRuntimeConfig(providerCode())
                .filter(AiImageProviderRuntimeConfig::enabled)
                .orElseThrow(() -> new BizException("腾讯混元 provider 未启用或未配置"));
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
