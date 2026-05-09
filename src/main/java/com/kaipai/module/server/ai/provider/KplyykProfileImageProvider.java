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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KplyykProfileImageProvider implements AiProfileImageProvider {

    private final AiProfileCardProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return "kplyyk";
    }

    @Override
    public String modelCode() {
        return properties.getKplyyk().getModel();
    }

    @Override
    public AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request) {
        AiProfileCardProperties.KplyykProvider config = properties.getKplyyk();
        if (!StringUtils.hasText(config.getEndpoint())) {
            throw new BizException("KPLYYK 生图管理 API endpoint 未配置");
        }
        if (!StringUtils.hasText(config.getAuthToken())) {
            throw new BizException("KPLYYK 生图管理 API 密钥未配置");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            SourceImage sourceImage = downloadSourceImage(client, request.sourceImageUrl(), config);
            JsonNode submitted = submitEditTask(client, request, sourceImage, config);
            AiProfileImageGenerationResult immediateResult = parseImageResult(submitted);
            if (immediateResult != null) {
                return immediateResult;
            }

            String taskId = firstText(submitted, "/task_id", "/taskId", "/data/task_id", "/data/taskId");
            if (!StringUtils.hasText(taskId)) {
                throw new BizException("KPLYYK 生图管理 API 未返回 task_id");
            }
            return pollTask(client, taskId.trim(), config);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException("KPLYYK 生图任务轮询被中断");
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("KPLYYK 生图管理 API 调用失败：" + error.getMessage());
        }
    }

    private JsonNode submitEditTask(HttpClient client,
                                    AiProfileImageGenerationRequest request,
                                    SourceImage sourceImage,
                                    AiProfileCardProperties.KplyykProvider config) throws Exception {
        String boundary = "----KaipaiKplyykImage" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeTextPart(output, boundary, "model", modelCode());
        writeTextPart(output, boundary, "prompt", request.promptText());
        writeTextPart(output, boundary, "size", config.getSize());
        writeTextPart(output, boundary, "quality", config.getQuality());
        writeTextPart(output, boundary, "n", String.valueOf(Math.max(1, config.getCount())));
        writeFilePart(output, boundary, "image", sourceImage.fileName(), sourceImage.contentType(), sourceImage.bytes());
        output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.getEndpoint()))
                .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                .header(authHeader(config), authValue(config.getAuthToken()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(output.toByteArray()))
                .build();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException("KPLYYK 生图管理 API 提交异常：" + response.statusCode() + " " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    private AiProfileImageGenerationResult pollTask(HttpClient client,
                                                    String taskId,
                                                    AiProfileCardProperties.KplyykProvider config) throws Exception {
        String taskUrl = appendPath(config.getEndpoint(), URLEncoder.encode(taskId, StandardCharsets.UTF_8));
        for (int attempt = 0; attempt < Math.max(1, config.getMaxPollAttempts()); attempt++) {
            Thread.sleep(Math.max(300, config.getPollIntervalMs()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(taskUrl))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header(authHeader(config), authValue(config.getAuthToken()))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("KPLYYK 生图任务查询异常：" + response.statusCode() + " " + response.body());
            }
            JsonNode task = objectMapper.readTree(response.body());
            String status = firstText(task, "/status", "/data/status");
            if ("succeeded".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) {
                AiProfileImageGenerationResult result = parseImageResult(task);
                if (result != null) {
                    return result;
                }
                throw new BizException("KPLYYK 生图任务完成但未返回图片");
            }
            if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                throw new BizException("KPLYYK 生图任务失败：" + resolveTaskError(task));
            }
        }
        throw new BizException("KPLYYK 生图任务超过等待时间");
    }

    private SourceImage downloadSourceImage(HttpClient client,
                                            String sourceImageUrl,
                                            AiProfileCardProperties.KplyykProvider config) throws Exception {
        if (!StringUtils.hasText(sourceImageUrl) || !sourceImageUrl.startsWith("http")) {
            throw new BizException("KPLYYK 图生图需要可访问的源图片 URL");
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

    private AiProfileImageGenerationResult parseImageResult(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        String imageUrl = firstText(root,
                "/imageUrl",
                "/url",
                "/data/0/url",
                "/data/0/imageUrl",
                "/result/data/0/url",
                "/result/data/0/imageUrl",
                "/data/result/data/0/url",
                "/data/result/data/0/imageUrl");
        if (StringUtils.hasText(imageUrl)) {
            return AiProfileImageGenerationResult.imageUrl(imageUrl.trim());
        }

        String b64Json = firstText(root,
                "/b64Json",
                "/b64_json",
                "/data/0/b64_json",
                "/data/0/b64Json",
                "/result/data/0/b64_json",
                "/result/data/0/b64Json",
                "/data/result/data/0/b64_json",
                "/data/result/data/0/b64Json");
        if (StringUtils.hasText(b64Json)) {
            return AiProfileImageGenerationResult.imageBytes(decodeBase64Image(b64Json.trim()), "image/png");
        }
        return null;
    }

    private String resolveTaskError(JsonNode task) {
        String message = firstText(task,
                "/error/body/error/message",
                "/error/body/error/detail",
                "/error/body/message",
                "/error/body/detail",
                "/error/message",
                "/message",
                "/detail");
        if (StringUtils.hasText(message)) {
            return message;
        }
        JsonNode error = task.at("/error");
        return error == null || error.isMissingNode() ? "未知错误" : error.toString();
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

    private String authValue(String token) {
        String normalized = token.trim();
        return normalized.regionMatches(true, 0, "Bearer ", 0, 7) ? normalized : "Bearer " + normalized;
    }

    private String authHeader(AiProfileCardProperties.KplyykProvider config) {
        return StringUtils.hasText(config.getAuthHeader()) ? config.getAuthHeader().trim() : "Authorization";
    }

    private byte[] decodeBase64Image(String value) {
        int commaIndex = value.indexOf(',');
        String normalized = commaIndex >= 0 ? value.substring(commaIndex + 1) : value;
        return Base64.getDecoder().decode(normalized);
    }

    private String appendPath(String base, String path) {
        return base.replaceAll("/+$", "") + "/" + path;
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
