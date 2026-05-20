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
        boolean coverPage = "cover".equalsIgnoreCase(pageType);
        String background = tencentBackgroundHint(templateSceneCode, styleCode, layoutText(fixedLayout, "background", ""));
        String pageLabel = tencentPageLabel(pageType);
        String referenceInstruction = coverPage
                ? (hasSourceImage
                ? "参考图1是用户源图，只用于人物身份与自然气质参考，不要复制背景文字、标签或版式。"
                : "当前没有可用身份参考图，按封面人物气质生成。")
                : (hasSourceImage
                ? "参考图1是上一页底部裁切出的连续性参考带，只延续色彩、光线、材质和空间方向，不复制人物、文字、Logo、二维码或前景布局。"
                : "当前没有可用连续性参考带，仅按文字连续性生成背景气质。");
        String composition = coverPage
                ? "构图：演员位于右侧，左侧留空给后续信息层，底部保留安静过渡区。"
                : "构图：顶部约 15% 延续上一页底部的色彩、光线、材质和空间方向，无人物主体，页面以背景承载为主。";
        String subject = coverPage
                ? "页面职责：演员封面背景，允许保留身份感，但不要加入可读文字或多余装饰。"
                : ("resume".equalsIgnoreCase(pageType)
                ? "页面职责：履历页背景，以资料承载为主，不要重复封面级人物主视觉。"
                : "页面职责：影像页背景，以照片墙和视频入口承载为主，不要重复封面级人物主视觉。");
        String prompt = """
                生成一张 9:16 全幅%s，输出 2160x3840。
                %s
                %s
                %s
                风格：%s
                背景：%s
                安全要求：背景必须全幅铺满，连续性区域只用于延续背景氛围，不要复制人物、文字、Logo、标签、二维码或 UI 形状。
                不要可读文字、水印、Logo、标签、二维码或任何 UI 形状。
                Plain, unmarked, symbol-free.
                """.formatted(
                pageLabel,
                referenceInstruction,
                composition,
                subject,
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

    private String tencentPageLabel(String pageType) {
        String normalized = StringUtils.hasText(pageType) ? pageType.trim().toLowerCase(Locale.ROOT) : "cover";
        if ("resume".equals(normalized)) {
            return "履历页背景图";
        }
        if ("gallery".equals(normalized)) {
            return "影像页背景图";
        }
        return "封面背景图";
    }

    private String tencentStyleHint(String templateSceneCode, String styleCode) {
        String normalized = (styleCode + " " + templateSceneCode).toLowerCase(Locale.ROOT);
        if (normalized.contains("costume")) {
            return "古风电影感，暖象牙和墨绿为主，丝绸衣料、薄雾江南、木桥与竹影作为背景气氛。";
        }
        if (normalized.contains("urban")) {
            return "都市时装感，深灰冷调，柔和城市或影棚深度，克制霓虹边光。";
        }
        if (normalized.contains("classic")) {
            return "经典棚拍感，暖灰和米白为主，真实柔光，稳重干净。";
        }
        if (normalized.contains("commercial")) {
            return "商业棚拍感，明亮中性，清爽软盒光，现代广告气质。";
        }
        if (normalized.contains("artistic")) {
            return "艺术电影感，画廊氛围、戏剧性阴影、低饱和石墨与橄榄调。";
        }
        return "高级演员资料册背景，真实、克制、干净。";
    }

    private String tencentBackgroundHint(String templateSceneCode, String styleCode, String background) {
        String normalized = (styleCode + " " + templateSceneCode).toLowerCase(Locale.ROOT);
        if (normalized.contains("costume")) {
            return "暖象牙色的墨色纹理、薄雾山水和低饱和暖红抽象点缀。";
        }
        if (normalized.contains("urban")) {
            return "深灰、钢蓝和冷白的层次，带柔和城市或影棚深度。";
        }
        if (normalized.contains("commercial")) {
            return "干净的白灰和浅香槟层次，保持明亮、通透、低噪点。";
        }
        if (normalized.contains("artistic")) {
            return "画廊感的纹理墙面、克制阴影和低饱和色块。";
        }
        if (normalized.contains("classic")) {
            return "暖灰、米白和淡香槟的柔和层次，带轻微胶片颗粒。";
        }
        if (StringUtils.hasText(background)) {
            return compactText(sanitizeForTencentPrompt(background), 130);
        }
        return "低细节、全幅铺开的中性色背景，只保留连续氛围。";
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
