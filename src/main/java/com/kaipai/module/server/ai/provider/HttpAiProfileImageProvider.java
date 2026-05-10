package com.kaipai.module.server.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.server.ai.config.AiProfileCardProperties;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HttpAiProfileImageProvider implements AiProfileImageProvider {

    private final AiProfileCardProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return "http";
    }

    @Override
    public String modelCode() {
        return properties.getHttp().getModel();
    }

    @Override
    public AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request) {
        AiProfileCardProperties.HttpProvider config = properties.getHttp();
        if (!StringUtils.hasText(config.getEndpoint())) {
            throw new BizException("AI 分享图 HTTP provider endpoint 未配置");
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", request.taskId());
            payload.put("model", modelCode());
            payload.put("templateSceneCode", request.templateSceneCode());
            payload.put("styleCode", request.styleCode());
            payload.put("sourceImageUrl", request.sourceImageUrl());
            payload.put("prompt", request.promptText());
            payload.put("negativePrompt", request.negativePrompt());
            payload.put("promptJson", request.promptJson());

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.getEndpoint()))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (StringUtils.hasText(config.getAuthToken())) {
                builder.header(
                        StringUtils.hasText(config.getAuthHeader()) ? config.getAuthHeader().trim() : "Authorization",
                        config.getAuthToken().trim());
            }

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("AI 分享图 HTTP provider 返回异常：" + response.statusCode());
            }
            return parseResult(response.body());
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("AI 分享图 HTTP provider 调用失败：" + error.getMessage());
        }
    }

    private AiProfileImageGenerationResult parseResult(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        String imageUrl = firstText(root,
                "/imageUrl",
                "/url",
                "/data/imageUrl",
                "/data/url",
                "/result/imageUrl",
                "/result/url",
                "/data/0/imageUrl",
                "/data/0/url");
        if (StringUtils.hasText(imageUrl)) {
            return AiProfileImageGenerationResult.imageUrl(imageUrl.trim());
        }

        String b64Json = firstText(root,
                "/b64Json",
                "/b64_json",
                "/data/b64Json",
                "/data/b64_json",
                "/result/b64Json",
                "/result/b64_json",
                "/data/0/b64_json",
                "/data/0/b64Json");
        if (StringUtils.hasText(b64Json)) {
            return AiProfileImageGenerationResult.imageBytes(Base64.getDecoder().decode(b64Json.trim()), "image/png");
        }
        throw new BizException("AI 分享图 HTTP provider 缺少 imageUrl 或 b64Json");
    }

    private String firstText(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = root.at(pointer);
            if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
                return node.asText();
            }
        }
        return null;
    }
}
