package com.kaipai.integration.storage;

import com.kaipai.common.config.TencentCloudProperties;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.ResultCode;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CosUtil {

    private final COSClient cosClient;
    private final TencentCloudProperties tencentCloudProperties;

    private static final long MB = 1024L * 1024L;
    private static final long AVATAR_MAX_SIZE = 2 * MB;
    private static final long PHOTO_MAX_SIZE = 5 * MB;
    private static final long LICENSE_MAX_SIZE = 5 * MB;
    private static final long VIDEO_MAX_SIZE = 100 * MB;
    private static final long PDF_MAX_SIZE = 20 * MB;

    /** 允许上传的图片类型 */
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /** 允许上传的视频类型 */
    private static final List<String> ALLOWED_VIDEO_TYPES = Arrays.asList(
            "video/mp4", "video/quicktime", "video/x-msvideo"
    );

    /** 允许上传的 PDF 类型；部分小程序会以 octet-stream 发送，最终仍以扩展名和文件头校验为准 */
    private static final List<String> ALLOWED_PDF_TYPES = Arrays.asList(
            "application/pdf", "application/x-pdf", "application/octet-stream"
    );

    /**
     * 上传图片，返回访问 URL
     *
     * @param file     文件
     * @param folder   存储目录，如 "avatar"、"photo"
     * @return 访问 URL
     */
    public String uploadImage(MultipartFile file, String folder) {
        validateFileType(file, ALLOWED_IMAGE_TYPES);
        validateFileSize(file, imageMaxSize(folder), imageMaxSizeMessage(folder));
        return upload(file, folder);
    }

    /**
     * 上传视频，返回访问 URL
     *
     * @param file   文件
     * @param folder 存储目录，如 "video"
     * @return 访问 URL
     */
    public String uploadVideo(MultipartFile file, String folder) {
        validateFileType(file, ALLOWED_VIDEO_TYPES);
        validateFileSize(file, VIDEO_MAX_SIZE, "视频大小不能超过100MB");
        return upload(file, folder);
    }

    /**
     * 上传 PDF 文件，返回访问 URL
     *
     * @param file   PDF 文件
     * @param folder 存储目录
     * @return 访问 URL
     */
    public String uploadPdf(MultipartFile file, String folder) {
        validatePdfFile(file);
        validateFileSize(file, PDF_MAX_SIZE, "PDF 简历不能超过20MB");
        return upload(file, folder, "application/pdf", ".pdf");
    }

    /**
     * 上传服务端生成的二进制文件，返回访问 URL
     */
    public String uploadBytes(byte[] bytes, String contentType, String folder, String extension) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "生成文件为空");
        }
        String normalizedExtension = normalizeExtension(extension);
        String key = buildKey(folder, normalizedExtension);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(contentType);
            PutObjectRequest request = new PutObjectRequest(
                    bucketName(), key, inputStream, metadata);
            cosClient.putObject(request);
            return buildUrl(key);
        } catch (IOException | CosClientException e) {
            log.error("COS 生成文件上传失败", e);
            throw new BizException(ResultCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 删除文件
     *
     * @param fileUrl 文件完整 URL
     */
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        try {
            // 从 URL 中提取 key
            String key = extractKeyFromUrl(fileUrl);
            cosClient.deleteObject(bucketName(), key);
        } catch (CosClientException e) {
            log.warn("COS 文件删除失败: {}", fileUrl, e);
        }
    }

    private String upload(MultipartFile file, String folder) {
        return upload(file, folder, file.getContentType(), null);
    }

    private String upload(MultipartFile file, String folder, String contentType, String fallbackExtension) {
        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : normalizeExtension(fallbackExtension);
        // key 格式: folder/yyyy/MM/dd/uuid.ext
        String key = buildKey(folder, ext);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);
            PutObjectRequest request = new PutObjectRequest(
                    bucketName(), key, file.getInputStream(), metadata);
            cosClient.putObject(request);
            return buildUrl(key);
        } catch (IOException | CosClientException e) {
            log.error("COS 文件上传失败", e);
            throw new BizException(ResultCode.FILE_UPLOAD_FAILED);
        }
    }

    private void validateFileType(MultipartFile file, List<String> allowedTypes) {
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    private void validateFileSize(MultipartFile file, long maxSize, String message) {
        if (file.getSize() > maxSize) {
            throw new BizException(ResultCode.FILE_SIZE_EXCEEDED.getCode(), message);
        }
    }

    private void validatePdfFile(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String contentType = file.getContentType();
        boolean namedPdf = originalName != null && originalName.toLowerCase().endsWith(".pdf");
        boolean supportedType = contentType == null || ALLOWED_PDF_TYPES.contains(contentType);
        if (!namedPdf || !supportedType || !hasPdfMagic(file)) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    private boolean hasPdfMagic(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(5);
            return header.length == 5
                    && header[0] == '%'
                    && header[1] == 'P'
                    && header[2] == 'D'
                    && header[3] == 'F'
                    && header[4] == '-';
        } catch (IOException ignored) {
            return false;
        }
    }

    private long imageMaxSize(String folder) {
        return switch (folder) {
            case "avatar" -> AVATAR_MAX_SIZE;
            case "license" -> LICENSE_MAX_SIZE;
            case "photo" -> PHOTO_MAX_SIZE;
            default -> PHOTO_MAX_SIZE;
        };
    }

    private String imageMaxSizeMessage(String folder) {
        return switch (folder) {
            case "avatar" -> "头像图片不能超过2MB";
            case "license" -> "营业执照图片不能超过5MB";
            case "photo" -> "作品图片不能超过5MB";
            default -> "图片大小不能超过5MB";
        };
    }

    private String buildKey(String folder, String extension) {
        return String.format("%s/%s/%s%s",
                folder,
                LocalDate.now().toString().replace("-", "/"),
                UUID.randomUUID().toString().replace("-", ""),
                normalizeExtension(extension));
    }

    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        return extension.startsWith(".") ? extension : "." + extension;
    }

    private String buildUrl(String key) {
        return String.format("https://%s.cos.%s.myqcloud.com/%s",
                bucketName(), region(), key);
    }

    private String extractKeyFromUrl(String fileUrl) {
        String prefix = String.format("https://%s.cos.%s.myqcloud.com/",
                bucketName(), region());
        return fileUrl.startsWith(prefix) ? fileUrl.substring(prefix.length()) : fileUrl;
    }

    private String bucketName() {
        return tencentCloudProperties.getCos().getBucketName();
    }

    private String region() {
        return tencentCloudProperties.getCos().getRegion();
    }
}
