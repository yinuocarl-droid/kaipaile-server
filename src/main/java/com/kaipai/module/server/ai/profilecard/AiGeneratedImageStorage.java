package com.kaipai.module.server.ai.profilecard;

import com.kaipai.common.config.CosConfig;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.ResultCode;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiGeneratedImageStorage {

    private static final long MAX_GENERATED_IMAGE_BYTES = 25L * 1024 * 1024;
    private static final Duration DOWNLOAD_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DOWNLOAD_READ_TIMEOUT = Duration.ofSeconds(120);

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final COSClient cosClient;
    private final CosConfig cosConfig;

    public String uploadFromUrl(String imageUrl, String folder) {
        String normalizedUrl = requireHttpImageUrl(imageUrl);
        DownloadedImage downloadedImage = downloadImage(normalizedUrl);
        return upload(downloadedImage.bytes(), resolveDownloadedContentType(downloadedImage.contentType(), normalizedUrl), folder);
    }

    public CroppedImageBand uploadBottomBandFromUrl(String imageUrl, String folder, double bandRatio) {
        String normalizedUrl = requireHttpImageUrl(imageUrl);
        DownloadedImage downloadedImage = downloadImage(normalizedUrl);
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(downloadedImage.bytes()));
            if (source == null) {
                throw new BizException("生成图片内容无法解析");
            }
            double normalizedBandRatio = clampBandRatio(bandRatio);
            int cropHeight = Math.max(1, Math.min(source.getHeight(), (int) Math.round(source.getHeight() * normalizedBandRatio)));
            int cropTop = Math.max(0, source.getHeight() - cropHeight);
            BufferedImage band = source.getSubimage(0, cropTop, source.getWidth(), cropHeight);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(band, "png", output)) {
                throw new BizException("连续性参考带编码失败");
            }
            String bandUrl = upload(output.toByteArray(), "image/png", folder);
            return new CroppedImageBand(bandUrl, source.getWidth(), source.getHeight(), cropTop, cropHeight, normalizedBandRatio);
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            log.warn("AI 生成图片连续性裁切失败: {}", normalizedUrl, error);
            throw new BizException("连续性参考带生成失败：" + error.getMessage());
        }
    }

    public boolean isManagedUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return false;
        }
        return imageUrl.trim().startsWith(buildUrlPrefix());
    }

    public String upload(byte[] bytes, String contentType, String folder) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException("生成图片内容为空");
        }
        String normalizedContentType = normalizeContentType(contentType);
        String key = buildKey(folder, normalizedContentType);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(normalizedContentType);
            PutObjectRequest request = new PutObjectRequest(
                    cosConfig.getBucketName(),
                    key,
                    new ByteArrayInputStream(bytes),
                    metadata);
            cosClient.putObject(request);
            return buildUrl(key);
        } catch (CosClientException error) {
            log.error("AI 生成图片上传 COS 失败", error);
            throw new BizException(ResultCode.FILE_UPLOAD_FAILED);
        }
    }

    private DownloadedImage downloadImage(String normalizedUrl) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(DOWNLOAD_CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedUrl))
                    .timeout(DOWNLOAD_READ_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || bytes == null || bytes.length == 0) {
                throw new BizException("生成图片下载失败：" + response.statusCode());
            }
            if (bytes.length > MAX_GENERATED_IMAGE_BYTES) {
                throw new BizException("生成图片不能超过25MB");
            }
            String contentType = response.headers()
                    .firstValue("content-type")
                    .map(value -> value.split(";")[0].trim().toLowerCase(Locale.ROOT))
                    .orElse("");
            return new DownloadedImage(bytes, contentType);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException("生成图片下载被中断");
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            log.warn("AI 生成图片下载失败: {}", normalizedUrl, error);
            throw new BizException("生成图片下载失败：" + error.getMessage());
        }
    }

    private String requireHttpImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            throw new BizException("生成图片 URL 为空");
        }
        String normalized = imageUrl.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new BizException("生成图片 URL 必须是 http/https 地址");
        }
        return normalized;
    }

    private String resolveDownloadedContentType(String contentType, String imageUrl) {
        if ("image/jpg".equals(contentType)) {
            return "image/jpeg";
        }
        if (SUPPORTED_IMAGE_TYPES.contains(contentType)) {
            return contentType;
        }
        String guessed = guessContentType(imageUrl);
        if (SUPPORTED_IMAGE_TYPES.contains(guessed)) {
            return guessed;
        }
        if (!StringUtils.hasText(contentType) || "application/octet-stream".equals(contentType)) {
            return "image/png";
        }
        throw new BizException("生成图片类型不支持：" + contentType);
    }

    private String normalizeContentType(String contentType) {
        String normalized = StringUtils.hasText(contentType) ? contentType.trim().toLowerCase() : "image/png";
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if (!SUPPORTED_IMAGE_TYPES.contains(normalized)) {
            throw new BizException("生成图片类型不支持：" + normalized);
        }
        return normalized;
    }

    private String buildKey(String folder, String contentType) {
        String normalizedFolder = StringUtils.hasText(folder) ? folder.trim() : "ai-profile-card";
        return String.format("%s/%s/%s%s",
                normalizedFolder,
                LocalDate.now().toString().replace("-", "/"),
                UUID.randomUUID().toString().replace("-", ""),
                extensionFor(contentType));
    }

    private String extensionFor(String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return ".jpg";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".png";
    }

    private String guessContentType(String url) {
        String normalized = url.toLowerCase(Locale.ROOT).split("\\?", 2)[0];
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        return "";
    }

    private String buildUrl(String key) {
        return buildUrlPrefix() + key;
    }

    private String buildUrlPrefix() {
        return String.format("https://%s.cos.%s.myqcloud.com/",
                cosConfig.getBucketName(), cosConfig.getRegion());
    }

    private double clampBandRatio(double bandRatio) {
        if (Double.isNaN(bandRatio) || Double.isInfinite(bandRatio)) {
            return 0.15d;
        }
        return Math.max(0.12d, Math.min(0.15d, bandRatio));
    }

    public record CroppedImageBand(
            String imageUrl,
            int sourceWidth,
            int sourceHeight,
            int cropTop,
            int cropHeight,
            double ratio
    ) {

        public String bandRect() {
            return "x=0,y=%d,w=%d,h=%d,ratio=%.4f".formatted(cropTop, sourceWidth, cropHeight, ratio);
        }
    }

    private record DownloadedImage(
            byte[] bytes,
            String contentType
    ) {
    }
}
