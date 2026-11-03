package com.kaipai.integration.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.config.AiImageProviderRuntimeConfig;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TokenHub v3.5 protocol, intentionally separate from Tencent aiart / TC3. */
@Component
@RequiredArgsConstructor
public class TencentTokenHubProfileImageProvider implements AiProfileImageProvider {
    private static final String CODE = "tencent-tokenhub";
    private static final String MODEL = "hy-image-v3.5-preview";
    private static final String PATH = "/v1/wand/hunyuan-image/v35-generation";
    private static final String ENDPOINT = "https://tokenhub.tencentmaas.com" + PATH;
    private final AiImageProviderConfigService configService;
    private final ObjectMapper objectMapper;

    @Override public String providerCode() { return CODE; }
    @Override public String modelCode() { return configService.resolveModelCode(CODE, MODEL); }
    @Override public boolean supportsReferenceImageCount(int count) { return count == 1 || count == 2; }

    /** One synchronous POST only; timeouts are not retried or downgraded to another provider. */
    @Override public AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request) {
        AiImageProviderRuntimeConfig runtime = configService.findRuntimeConfig(CODE)
                .filter(AiImageProviderRuntimeConfig::enabled)
                .orElseThrow(() -> new BizException("TokenHub provider 未启用或未配置"));
        String key = runtime.secret("apiKey");
        if (!StringUtils.hasText(key)) throw new BizException("TokenHub API Key 未配置");
        if (!MODEL.equals(runtime.model(MODEL))) throw new BizException("TokenHub 当前仅支持已验证的 hy-image-v3.5-preview 协议");
        URI endpoint = requireEndpoint(runtime.endpoint(ENDPOINT));
        if (request == null || !StringUtils.hasText(request.taskId())) throw new BizException("TokenHub 需要独立任务标识");
        URI person = requireImageUri(request.sourceImageUrl());
        URI background = StringUtils.hasText(request.backgroundImageUrl()) ? requireImageUri(request.backgroundImageUrl()) : null;
        if (person.equals(background)) throw new BizException("人物图与背景图必须独立");

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(runtime.connectTimeoutMs(10000)))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            int timeout = runtime.readTimeoutMs(240000);
            ProfileImageProviderHttpSupport.downloadSourceImage(client, person.toString(), timeout);
            if (background != null) ProfileImageProviderHttpSupport.downloadSourceImage(client, background.toString(), timeout);
            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of("type", "text", "text", prompt(request, background != null)));
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", person.toString())));
            if (background != null) content.add(Map.of("type", "image_url", "image_url", Map.of("url", background.toString())));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", MODEL);
            // Session isolates conversational context, not a claim of vendor request idempotency.
            payload.put("session", "kaipai-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(request.taskId().getBytes(StandardCharsets.UTF_8))));
            payload.put("messages", List.of(Map.of("role", "user", "content", content)));
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(timeout))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new BizException("TokenHub 图片生成 HTTP " + response.statusCode());
            return parseResult(objectMapper.readTree(response.body()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException("TokenHub 调用被中断，请核对原任务状态，不要重复提交");
        } catch (BizException error) {
            // Deliberately do not propagate source download errors containing URLs or raw response bodies.
            if (error.getMessage().startsWith("TokenHub")) throw error;
            throw new BizException("TokenHub 参考图片读取失败");
        } catch (Exception error) {
            throw new BizException("TokenHub 调用或响应解析失败，请核对原任务状态，不要重复提交");
        }
    }

    private AiProfileImageGenerationResult parseResult(JsonNode root) {
        if (root == null || !root.isObject() || root.hasNonNull("error")) throw new BizException("TokenHub 返回业务错误或无效响应");
        // Never scan assembled_history: it may contain input references or intermediate tool images.
        for (JsonNode choice : root.path("choices")) {
            JsonNode delta = choice.path("delta");
            if (!"image".equals(delta.path("type").asText())
                    || !"generate".equals(delta.path("image").path("source").asText())) continue;
            String value = delta.path("image").path("url").asText("");
            try {
                URI url = URI.create(value);
                if ("https".equals(url.getScheme()) && StringUtils.hasText(url.getHost()) && url.getRawUserInfo() == null)
                    return AiProfileImageGenerationResult.imageUrl(value);
            } catch (IllegalArgumentException ignored) { /* Reject malformed result without echoing it. */ }
        }
        throw new BizException("TokenHub 未返回已生成图片，不能将受理或文本当成生成成功");
    }

    private String prompt(AiProfileImageGenerationRequest request, boolean hasBackground) {
        String reference = hasBackground
                ? "图1仅提供人物身份，图2是指定场景，保留图2空间结构与主色，移除图1原背景。"
                : "图1提供人物身份，保留其面部特征与自然气质，生成协调的完整场景。";
        String framing = hasBackground
                ? "采用右侧腰上中近景半身肖像，不是全身照。头顶位于画面顶部10%至20%，"
                    + "人物视觉占宽约40%至45%，左侧保留45%至55%干净留白。"
                    + "画面底边自然截于腰腹处，躯干和衣物连续延伸出画外；"
                    + "画内不得出现衣摆或人物底部轮廓，不补全腿脚。"
                    + "沿用图2墙面的主色、线条、光向和空间关系，优先不呈现地面；"
                    + "禁止地面横穿人物，禁止悬浮、下沉或半身模型插入地面的效果。"
                    + "图1原始裁边不是人物轮廓，不得复制。"
                : "人物衣物与身体自然延伸至画面外或完整落地，不允许在画面内以矩形直线截断人物。";
        String instructions = "生成9:16竖版全幅真实人像场景首图，人物位于右侧，左侧保留低细节留白。"
                + reference + "人物必须真实处于场景中，匹配透视、光向、色温、遮挡和接触阴影。"
                + framing
                + "不要把原照片当作矩形贴片；no rectangular cutout, no pasted photo, no collage. "
                + "禁止姓名、文字、资料卡、按钮、边框、Logo或水印。 ";
        String supplied = StringUtils.hasText(request.promptText()) ? request.promptText().trim() : "";
        return instructions + supplied.substring(0, Math.min(supplied.length(), 6000));
    }

    private URI requireEndpoint(String value) {
        try {
            URI uri = URI.create(value);
            boolean official = "https".equals(uri.getScheme()) && "tokenhub.tencentmaas.com".equals(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443);
            if ((official || isLocalFixture(uri)) && PATH.equals(uri.getPath())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null) return uri;
        } catch (IllegalArgumentException ignored) { /* Fail closed without echo. */ }
        throw new BizException("TokenHub API 地址必须使用已验证的 v35-generation 接口");
    }
    private URI requireImageUri(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (("https".equals(uri.getScheme()) || isLocalFixture(uri)) && StringUtils.hasText(uri.getHost())
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null) return uri;
        } catch (IllegalArgumentException ignored) { /* Fail closed without echo. */ }
        throw new BizException("TokenHub 需要有效的 HTTPS 参考图片");
    }
    private boolean isLocalFixture(URI uri) {
        return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost());
    }
}
