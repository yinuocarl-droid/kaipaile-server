package com.kaipai.module.server.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.ai.dto.AiImageProviderPublicConfigDTO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VolcSeedreamProfileImageProvider implements AiProfileImageProvider {

    private final AiImageProviderConfigService aiImageProviderConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return "volc-seedream";
    }

    @Override
    public String modelCode() {
        return aiImageProviderConfigService.resolveModelCode(providerCode(), "doubao-seedream-4.0");
    }

    @Override
    public AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request) {
        AiImageProviderRuntimeConfig runtime = requireRuntimeConfig();
        String endpoint = runtime.endpoint(null);
        String apiKey = runtime.firstSecret("apiKey", "authToken");
        if (!StringUtils.hasText(endpoint)) {
            throw new BizException("火山 Seedream endpoint 未配置");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException("火山 Seedream API Key 未配置");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(runtime.connectTimeoutMs(10000)))
                    .build();
            Map<String, Object> payload = buildPayload(request, runtime.publicConfig());
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofMillis(runtime.readTimeoutMs(120000)))
                    .header("Authorization", bearer(apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("火山 Seedream 返回异常：" + response.statusCode() + " " + truncate(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            AiProfileImageGenerationResult result = ProfileImageProviderHttpSupport.parseCommonResult(root, endpoint);
            if (result != null) {
                return result;
            }
            throw new BizException("火山 Seedream 结果缺少图片 URL 或 base64");
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("火山 Seedream 调用失败：" + error.getMessage());
        }
    }

    private Map<String, Object> buildPayload(AiProfileImageGenerationRequest request,
                                             AiImageProviderPublicConfigDTO config) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelCode());
        payload.put("prompt", request.promptText());
        payload.put("prompts", List.of(request.promptText()));
        if (StringUtils.hasText(request.sourceImageUrl())) {
            payload.put("reference_images", List.of(request.sourceImageUrl().trim()));
        }
        if (StringUtils.hasText(request.negativePrompt())) {
            payload.put("negative_prompt", request.negativePrompt());
        }
        if (StringUtils.hasText(config.getSize())) {
            payload.put("size", config.getSize().trim());
        }
        if (StringUtils.hasText(config.getResponseFormat())) {
            payload.put("response_format", config.getResponseFormat().trim());
        }
        payload.put("watermark", false);
        if (config.getCount() != null && config.getCount() > 0) {
            payload.put("n", config.getCount());
        }
        payload.putAll(extraParams(config.getExtraParamsJson()));
        return payload;
    }

    private AiImageProviderRuntimeConfig requireRuntimeConfig() {
        return aiImageProviderConfigService.findRuntimeConfig(providerCode())
                .filter(AiImageProviderRuntimeConfig::enabled)
                .orElseThrow(() -> new BizException("火山 Seedream provider 未启用或未配置"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extraParams(String extraParamsJson) throws Exception {
        if (!StringUtils.hasText(extraParamsJson)) {
            return Map.of();
        }
        return objectMapper.readValue(extraParamsJson, Map.class);
    }

    private String bearer(String apiKey) {
        String normalized = apiKey.trim();
        return normalized.regionMatches(true, 0, "Bearer ", 0, 7) ? normalized : "Bearer " + normalized;
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
