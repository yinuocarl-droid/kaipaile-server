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

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OpenAiProfileImageProvider implements AiProfileImageProvider {

    private final AiProfileCardProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return "openai";
    }

    @Override
    public String modelCode() {
        return properties.getOpenai().getModel();
    }

    @Override
    public AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request) {
        AiProfileCardProperties.OpenAiProvider config = properties.getOpenai();
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new BizException("OpenAI 图片生成 API Key 未配置");
        }
        if (!StringUtils.hasText(config.getEndpoint())) {
            throw new BizException("OpenAI 图片生成 endpoint 未配置");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            SourceImage sourceImage = downloadSourceImage(client, request.sourceImageUrl(), config);
            String boundary = "----KaipaiAiProfileCard" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = buildMultipartBody(boundary, config, request, sourceImage);

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.getEndpoint()))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Authorization", "Bearer " + config.getApiKey().trim())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("OpenAI 图片生成返回异常：" + response.statusCode());
            }
            return parseOpenAiResult(response.body());
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("OpenAI 图片生成调用失败：" + error.getMessage());
        }
    }

    private SourceImage downloadSourceImage(HttpClient client,
                                            String sourceImageUrl,
                                            AiProfileCardProperties.OpenAiProvider config) throws Exception {
        if (!StringUtils.hasText(sourceImageUrl) || !sourceImageUrl.startsWith("http")) {
            throw new BizException("OpenAI 图生图需要可访问的源图片 URL");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceImageUrl))
                .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().length == 0) {
            throw new BizException("源图片下载失败：" + response.statusCode());
        }
        String contentType = response.headers().firstValue("content-type")
                .map(value -> value.split(";")[0].trim().toLowerCase())
                .orElseGet(() -> guessContentType(sourceImageUrl));
        return new SourceImage(response.body(), contentType, fileNameForContentType(contentType));
    }

    private byte[] buildMultipartBody(String boundary,
                                      AiProfileCardProperties.OpenAiProvider config,
                                      AiProfileImageGenerationRequest request,
                                      SourceImage sourceImage) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeTextPart(output, boundary, "model", modelCode());
        writeTextPart(output, boundary, "prompt", request.promptText());
        writeTextPart(output, boundary, "n", "1");
        if (StringUtils.hasText(config.getSize())) {
            writeTextPart(output, boundary, "size", config.getSize().trim());
        }
        if (StringUtils.hasText(config.getQuality())) {
            writeTextPart(output, boundary, "quality", config.getQuality().trim());
        }
        if (StringUtils.hasText(config.getOutputFormat())) {
            writeTextPart(output, boundary, "output_format", config.getOutputFormat().trim());
        }
        writeFilePart(output, boundary, "image", sourceImage.fileName(), sourceImage.contentType(), sourceImage.bytes());
        output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private AiProfileImageGenerationResult parseOpenAiResult(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        String imageUrl = firstText(root, "/data/0/url");
        if (StringUtils.hasText(imageUrl)) {
            return AiProfileImageGenerationResult.imageUrl(imageUrl.trim());
        }
        String b64Json = firstText(root, "/data/0/b64_json", "/data/0/b64Json");
        if (StringUtils.hasText(b64Json)) {
            return AiProfileImageGenerationResult.imageBytes(Base64.getDecoder().decode(b64Json.trim()), "image/png");
        }
        throw new BizException("OpenAI 图片生成结果缺少 url 或 b64_json");
    }

    private void writeTextPart(ByteArrayOutputStream output, String boundary, String name, String value) throws Exception {
        if (value == null) {
            return;
        }
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFilePart(ByteArrayOutputStream output,
                               String boundary,
                               String name,
                               String fileName,
                               String contentType,
                               byte[] bytes) throws Exception {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
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

    private String guessContentType(String url) {
        String normalized = url.toLowerCase();
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private String fileNameForContentType(String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return "source.jpg";
        }
        if ("image/webp".equals(contentType)) {
            return "source.webp";
        }
        return "source.png";
    }

    private record SourceImage(byte[] bytes, String contentType, String fileName) {
    }
}
