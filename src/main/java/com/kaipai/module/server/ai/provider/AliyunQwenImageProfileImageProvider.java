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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AliyunQwenImageProfileImageProvider implements AiProfileImageProvider {

    private static final String DEFAULT_MODEL = "qwen-image-edit";

    private final AiImageProviderConfigService aiImageProviderConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return "aliyun-qwen-image";
    }

    @Override
    public String modelCode() {
        return aiImageProviderConfigService.resolveModelCode(providerCode(), DEFAULT_MODEL);
    }

    @Override
    public AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request) {
        AiImageProviderRuntimeConfig runtime = requireRuntimeConfig();
        String endpoint = runtime.endpoint("https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation");
        String apiKey = runtime.firstSecret("apiKey", "dashscopeApiKey", "authToken");
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException("阿里云百炼 API Key 未配置");
        }
        if (!StringUtils.hasText(endpoint)) {
            throw new BizException("阿里云百炼 endpoint 未配置");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(runtime.connectTimeoutMs(10000)))
                    .build();
            Map<String, Object> payload = buildPayload(request, runtime.publicConfig());
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofMillis(runtime.readTimeoutMs(120000)))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("阿里云百炼图片生成返回异常：" + response.statusCode() + " " + truncate(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            String taskStatus = ProfileImageProviderHttpSupport.firstText(root, "/output/task_status");
            if ("FAILED".equalsIgnoreCase(taskStatus)) {
                throw new BizException("阿里云百炼图片生成失败：" + truncate(response.body()));
            }
            AiProfileImageGenerationResult result = ProfileImageProviderHttpSupport.parseCommonResult(root, endpoint);
            if (result != null) {
                return result;
            }
            throw new BizException("阿里云百炼图片生成结果缺少图片 URL 或 base64");
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("阿里云百炼图片生成调用失败：" + error.getMessage());
        }
    }

    private Map<String, Object> buildPayload(AiProfileImageGenerationRequest request,
                                             AiImageProviderPublicConfigDTO config) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelCode());

        List<Map<String, Object>> content = new ArrayList<>();
        if (StringUtils.hasText(request.sourceImageUrl())) {
            content.add(Map.of("image", request.sourceImageUrl().trim()));
        }
        content.add(Map.of("text", request.promptText()));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", content);

        payload.put("input", Map.of("messages", List.of(message)));

        Map<String, Object> parameters = new LinkedHashMap<>();
        if (StringUtils.hasText(request.negativePrompt())) {
            parameters.put("negative_prompt", request.negativePrompt());
        }
        if (StringUtils.hasText(config.getSize())) {
            parameters.put("size", config.getSize().trim().replace("x", "*"));
        }
        parameters.put("watermark", false);
        if (StringUtils.hasText(config.getResponseFormat())) {
            parameters.put("response_format", config.getResponseFormat().trim());
        }
        if (config.getCount() != null && config.getCount() > 0) {
            parameters.put("n", config.getCount());
        }
        parameters.putAll(extraParams(config.getExtraParamsJson()));
        if (!parameters.isEmpty()) {
            payload.put("parameters", parameters);
        }
        return payload;
    }

    private AiImageProviderRuntimeConfig requireRuntimeConfig() {
        return aiImageProviderConfigService.findRuntimeConfig(providerCode())
                .filter(AiImageProviderRuntimeConfig::enabled)
                .orElseThrow(() -> new BizException("阿里云百炼 provider 未启用或未配置"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extraParams(String extraParamsJson) throws Exception {
        if (!StringUtils.hasText(extraParamsJson)) {
            return Map.of();
        }
        return objectMapper.readValue(extraParamsJson, Map.class);
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
