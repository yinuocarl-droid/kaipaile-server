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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
    private static final int MAX_PROMPT_LENGTH = 1200;
    private static final DateTimeFormatter TC3_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
        payload.put("Revise", Boolean.TRUE.equals(runtime.publicConfig().getPromptRewrite()) ? 1 : 0);
        return payload;
    }

    private String buildTencentPrompt(AiProfileImageGenerationRequest request, boolean hasSourceImage) {
        String styleCode = StringUtils.hasText(request.styleCode()) ? request.styleCode().trim() : "";
        String templateSceneCode = StringUtils.hasText(request.templateSceneCode()) ? request.templateSceneCode().trim() : "";
        JsonNode fixedLayout = parsePromptJson(request.promptJson()).path("fixedLayout");
        String pageType = layoutText(fixedLayout, "pageType", "cover");
        boolean coverPage = "cover".equals(pageType);
        String subjectBox = layoutText(fixedLayout, "subjectBox",
                "hero right side, face near upper-right, body must not cover left text-safe area");
        String identitySafeArea = layoutText(fixedLayout, "identitySafeArea",
                "hero left side must remain clean negative space");
        String safeSurfaceTone = compactText(layoutText(fixedLayout, "safeSurfaceTone",
                "low-detail matte background surfaces"), 120);
        String background = tencentBackgroundHint(templateSceneCode, styleCode, layoutText(fixedLayout, "background", ""));
        if (!coverPage) {
            String prompt = """
                    Agent layout is mandatory. Generate only a clean 9:16 full-bleed non-portrait editorial background image for album pageType=%s, output 2160x3840.
                    This page is for native mini-program information/photo modules. Do not create any actor portrait, human face, body, silhouette, head, skin, hair, eyes or duplicated cover subject.
                    Keep the title-safe area empty and quiet: %s.
                    Keep all module regions as continuous %s for later native overlays, with no boxes, frames, panels, photo slots, thumbnails, interface shapes or document edges.
                    The coordinate descriptions above are generation constraints only, never visible content. %s Background material: %s.
                    Strictly no readable text, Chinese characters, English letters, numbers, calligraphy, seal-script words, phone, QR code, watermark, logo, fake app UI, hard cards, section titles, rows, chips, thumbnail frames, video-player controls, border, paper edge or card shell.
                    """.formatted(
                    pageType,
                    identitySafeArea,
                    safeSurfaceTone,
                    tencentStyleHint(templateSceneCode, styleCode),
                    background
            ).trim().replaceAll("\\s+", " ");
            return truncatePrompt(prompt);
        }
        String identityPolicy = hasSourceImage
                ? "Use reference image as actor identity source; preserve face, age impression, hairstyle, skin texture and body proportion."
                : "Create a realistic actor portrait with natural face detail and clean body proportion.";
        String prompt = """
                Agent layout is mandatory. Generate only a clean 9:16 full-bleed cinematic actor portrait background image, output 2160x3840. It must not look like a poster, document, app screen, resume sheet or card.
                Absolute no-text rule: do not create any Chinese characters, calligraphy, seal-script glyphs, English letters, numbers, title graphics, handwritten marks, signature marks, captions, logos, watermarks or readable/pretend-readable text anywhere in the image.
                %s
                Place the actor only in this fixed subject area: %s.
                Keep this fixed title-safe area empty and quiet: %s.
                All remaining left, center and lower areas must stay as continuous %s for later native overlays, with no boxes, frames, panels or interface shapes.
                The coordinate descriptions above are generation constraints only, never visible content. %s Background material: %s.
                Strictly no readable text, Chinese characters, English letters, numbers, calligraphy, seal-script words, phone, QR code, watermark, logo, fake app UI, hard cards, section titles, rows, chips, thumbnail frames, video-player controls, border, paper edge or card shell.
                """.formatted(
                identityPolicy,
                subjectBox,
                identitySafeArea,
                safeSurfaceTone,
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
            return fallback;
        }
        String value = fixedLayout.path(field).asText("");
        return StringUtils.hasText(value) ? value.trim() : fallback;
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
            return "Use refined modern costume-drama editorial styling, elegant traditional wardrobe, cinematic texture, restrained premium atmosphere, never cheap fantasy.";
        }
        if (normalized.contains("urban")) {
            return "Use a modern urban fashion editorial tone, quiet city or studio hero scene, polished contemporary wardrobe and confident natural expression.";
        }
        if (normalized.contains("classic")) {
            return "Use a clean commercial studio portrait tone, bright premium background, approachable expression and advertising-ready composition.";
        }
        return "Use a high-end actor portfolio visual tone tailored to the selected scene, realistic and polished.";
    }

    private String tencentBackgroundHint(String templateSceneCode, String styleCode, String background) {
        String normalized = (styleCode + " " + templateSceneCode).toLowerCase(Locale.ROOT);
        if (normalized.contains("costume")) {
            return "warm ivory full-bleed ink-wash texture, misty garden architecture, bridge and bamboo silhouettes, plain abstract cinnabar wash shapes only, no stamp marks and no calligraphy";
        }
        if (normalized.contains("urban")) {
            return "controlled city or studio depth, soft haze, charcoal gradients and restrained cool rim light";
        }
        if (normalized.contains("commercial")) {
            return "premium studio backdrop, soft grey or white gradients, restrained champagne or blue accent light";
        }
        if (normalized.contains("artistic")) {
            return "textured gallery wall, controlled dramatic shadows, muted olive, stone and rust accents, soft film grain";
        }
        if (StringUtils.hasText(background)) {
            return compactText(background, 130);
        }
        return "warm studio texture, soft analog film grain, subtle neutral matte surfaces and restrained atmosphere";
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
        String authorization = sign(endpoint, secretId, secretKey, action, timestamp, body);
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

    private String sign(String endpoint,
                        String secretId,
                        String secretKey,
                        String action,
                        long timestamp,
                        String body) throws Exception {
        String host = URI.create(endpoint).getHost();
        String date = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC).format(TC3_DATE);
        String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
                + "host:" + host + "\n"
                + "x-tc-action:" + action.toLowerCase(Locale.ROOT) + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String canonicalRequest = "POST\n/\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + sha256Hex(body);
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
        byte[] secretDate = hmac256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac256(secretDate, SERVICE);
        byte[] secretSigning = hmac256(secretService, "tc3_request");
        String signature = bytesToHex(hmac256(secretSigning, stringToSign));
        return "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private AiImageProviderRuntimeConfig requireRuntimeConfig() {
        return aiImageProviderConfigService.findRuntimeConfig(providerCode())
                .filter(AiImageProviderRuntimeConfig::enabled)
                .orElseThrow(() -> new BizException("腾讯混元 provider 未启用或未配置"));
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return bytesToHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] hmac256(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
