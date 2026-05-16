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
    private static final int MAX_PROMPT_LENGTH = 900;
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
        if (runtime.watermark(null) != null) {
            payload.put("LogoAdd", runtime.watermark(true) ? 1 : 0);
        }
        if (runtime.publicConfig().getPromptRewrite() != null) {
            payload.put("Revise", runtime.publicConfig().getPromptRewrite() ? 1 : 0);
        }
        return payload;
    }

    private String buildTencentPrompt(AiProfileImageGenerationRequest request, boolean hasSourceImage) {
        String styleCode = StringUtils.hasText(request.styleCode()) ? request.styleCode().trim() : "";
        String templateSceneCode = StringUtils.hasText(request.templateSceneCode()) ? request.templateSceneCode().trim() : "";
        String identityPolicy = hasSourceImage
                ? "Use the reference image as actor identity source; preserve recognizable face, age impression, hairstyle, natural skin texture and body proportion."
                : "Create a realistic actor portrait background with natural face detail and clean body proportion.";
        String prompt = """
                Create a premium 9:16 vertical full-bleed actor profile-card background layer for a mini program.
                %s
                Scene=%s, style=%s. %s
                The final mini program will render all business content later, so keep the image as one continuous cinematic background with calm readable lower surfaces.
                Do not render readable text, Chinese characters, English letters, numbers, phone, QR code, watermark, logo, fake app UI, hard information cards, section titles, rows, chips, thumbnail frames, video-player controls, outer border, paper edge, poster mat or card shell.
                Keep subject in a hero portrait area with clean eyes, hands, hairline and clothing edges; use premium realistic lighting and refined composition.
                """.formatted(
                identityPolicy,
                templateSceneCode,
                styleCode,
                tencentStyleHint(templateSceneCode, styleCode)
        ).trim().replaceAll("\\s+", " ");
        return truncatePrompt(prompt);
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
