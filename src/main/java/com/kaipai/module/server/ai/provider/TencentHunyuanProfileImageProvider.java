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
import java.util.ArrayList;
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
        payload.put("LogoAdd", 0);
        payload.put("Revise", 0);
        return payload;
    }

    private String buildTencentPrompt(AiProfileImageGenerationRequest request, boolean hasSourceImage) {
        String styleCode = StringUtils.hasText(request.styleCode()) ? request.styleCode().trim() : "";
        String templateSceneCode = StringUtils.hasText(request.templateSceneCode()) ? request.templateSceneCode().trim() : "";
        JsonNode promptRoot = parsePromptJson(request.promptJson());
        JsonNode fixedLayout = promptRoot.path("fixedLayout");
        JsonNode flowTheme = promptRoot.path("flowTheme");
        String flowBackgroundColor = layoutText(
                flowTheme,
                "backgroundColor",
                layoutText(fixedLayout, "flowBackgroundColor", "#eee3cf"));
        String background = tencentBackgroundHint(templateSceneCode, styleCode, layoutText(fixedLayout, "background", ""));
        String renderPolicy = compactText(String.join("; ",
                layoutText(fixedLayout, "textFreePolicy", "no typography anywhere"),
                layoutText(fixedLayout, "finalTextPolicy", "do not render text, labels, captions, watermarks, QR code, or fake app components"),
                layoutText(fixedLayout, "backgroundFramePolicy", "full-bleed background only")), 1000);
        String qualityChecklist = compactText(qualityChecklistText(promptRoot.path("qualityChecklist")), 900);
        String referenceInstruction = hasSourceImage
                ? "参考图1是用户源图，只用于人物身份与自然气质参考，不要复制或生成背景文字、标签、水印或版式。"
                : "当前没有可用身份参考图，按封面人物气质生成，但不要加入任何文字、数字、水印、Logo、标签或二维码。";
        String prompt = """
                生成一张 9:16 全幅无字演员详情页背景底图，输出 2160x3840。
                %s
                构图：演员位于右侧，左侧保持干净、低细节、无字符的纹理背景；封面底部和外侧边缘自然过渡到固定主题背景色 %s，方便下方资料内容继续延展。
                页面职责：只提供第一屏视觉背景底图；图中不要出现姓名、资料、标签、按钮、卡片、列表、排版块或任何假 UI。
                风格：%s
                背景：%s
                版式禁区：%s
                质检标准：%s
                安全要求：背景必须全幅铺满；全图禁止出现任何可读字符或疑似字符，包括中文、英文、数字、标题、姓名、海报字、书法字、印章字、签名、AI生成/图片由AI生成、Logo、水印、标签、二维码、联系方式、假 UI、文章片段、标题栏、文字块、底部文案或任何前景组件。
                特别注意：画面下半屏、底部留白、边角和纹理区域只能是空背景或抽象纹理，不能出现文字块、文章片段、书页印字、招牌、标签、哈希标签、字幕条或任何像字的笔画。
                Plain background image only, no typography, no captions, no watermark, no logo.
                """.formatted(
                referenceInstruction,
                flowBackgroundColor,
                tencentStyleHint(templateSceneCode, styleCode),
                background,
                renderPolicy,
                qualityChecklist
        ).trim().replaceAll("\\s+", " ");
        return truncatePrompt(prompt);
    }

    private String qualityChecklistText(JsonNode checklist) {
        if (checklist == null || !checklist.isArray()) {
            return "";
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : checklist) {
            String text = sanitizeForTencentPrompt(item.asText(""));
            if (StringUtils.hasText(text)) {
                items.add(compactText(text, 160));
            }
            if (items.size() >= 6) {
                break;
            }
        }
        return String.join("; ", items);
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
        return "高级演员详情页背景底图，真实、克制、干净。";
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
        return "低细节、全幅铺开的中性色背景，只保留稳定氛围。";
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
                .replace("seal-script", "abstract motifs")
                .replace("calligraphy", "ink texture")
                .replace("stamp marks", "abstract color marks")
                .replace("readable characters", "recognizable symbols")
                .replace("readable text", "recognizable typography")
                .replace("profile-card", "background")
                .replace("poster", "background")
                .replace("share", "casting")
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
