package com.kaipai.integration.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaipai.common.exception.BizException;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationResult;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

final class ProfileImageProviderHttpSupport {

    private ProfileImageProviderHttpSupport() {
    }

    static SourceImage downloadSourceImage(HttpClient client, String sourceImageUrl, int readTimeoutMs) throws Exception {
        if (!StringUtils.hasText(sourceImageUrl) || !sourceImageUrl.startsWith("http")) {
            throw new BizException("图生图需要可访问的源图片 URL");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceImageUrl))
                .timeout(Duration.ofMillis(readTimeoutMs))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().length == 0) {
            throw new BizException("源图片下载失败：" + response.statusCode());
        }
        String guessedContentType = guessContentType(sourceImageUrl);
        String contentType = response.headers().firstValue("content-type")
                .map(value -> value.split(";")[0].trim().toLowerCase())
                .filter(value -> !"application/octet-stream".equals(value) || !StringUtils.hasText(guessedContentType))
                .orElse(guessedContentType);
        if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
            throw new BizException("源图片 URL 返回内容不是图片：" + (StringUtils.hasText(contentType) ? contentType : "未知类型"));
        }
        return new SourceImage(response.body(), contentType, fileNameForContentType(contentType));
    }

    static AiProfileImageGenerationResult parseCommonResult(JsonNode root, String endpoint) {
        String imageUrl = firstText(root,
                "/imageUrl",
                "/image_url",
                "/url",
                "/data/imageUrl",
                "/data/image_url",
                "/data/url",
                "/data/0/url",
                "/data/0/imageUrl",
                "/data/0/image_url",
                "/images/0/url",
                "/images/0/imageUrl",
                "/images/0/image_url",
                "/output/url",
                "/output/imageUrl",
                "/output/image_url",
                "/output/results/0/url",
                "/output/results/0/image_url",
                "/output/results/0/image",
                "/output/choices/0/message/content/0/image",
                "/output/choices/0/message/content/1/image",
                "/result/data/0/url",
                "/result/data/0/imageUrl",
                "/result/output/0/url",
                "/Response/ResultImage/0");
        if (StringUtils.hasText(imageUrl)) {
            return AiProfileImageGenerationResult.imageUrl(resolveImageUrl(imageUrl.trim(), endpoint));
        }

        String b64Json = firstText(root,
                "/b64Json",
                "/b64_json",
                "/data/b64Json",
                "/data/b64_json",
                "/data/0/b64_json",
                "/data/0/b64Json",
                "/images/0/b64_json",
                "/output/results/0/b64_json",
                "/Response/ResultImage/0/Base64");
        if (StringUtils.hasText(b64Json)) {
            return AiProfileImageGenerationResult.imageBytes(decodeBase64Image(b64Json.trim()), "image/png");
        }
        return null;
    }

    static String firstText(JsonNode root, String... pointers) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        for (String pointer : pointers) {
            JsonNode node = root.at(pointer);
            if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
                return node.asText();
            }
        }
        return null;
    }

    static byte[] decodeBase64Image(String value) {
        int commaIndex = value.indexOf(',');
        String normalized = commaIndex >= 0 ? value.substring(commaIndex + 1) : value;
        return Base64.getDecoder().decode(normalized);
    }

    static String resolveImageUrl(String imageUrl, String endpoint) {
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }
        if (imageUrl.startsWith("//")) {
            return "https:" + imageUrl;
        }
        if (!StringUtils.hasText(endpoint)) {
            return imageUrl;
        }
        return URI.create(endpoint).resolve(imageUrl).toString();
    }

    static String guessContentType(String url) {
        String normalized = url.toLowerCase();
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        return "";
    }

    static String fileNameForContentType(String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return "source.jpg";
        }
        if ("image/webp".equals(contentType)) {
            return "source.webp";
        }
        return "source.png";
    }

    record SourceImage(byte[] bytes, String contentType, String fileName) {
    }
}
