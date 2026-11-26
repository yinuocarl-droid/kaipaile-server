package com.kaipai.service.actor;

import com.kaipai.common.config.TencentCloudProperties;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.ResultCode;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import java.time.Duration;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CosPrivateActorMediaStorage implements PrivateActorMediaStorage {
    private static final Set<String> MEDIA_TYPES = Set.of("photo", "video", "pdf");
    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/quicktime", "video/x-msvideo");
    private static final Set<String> PDF_TYPES = Set.of("application/pdf", "application/x-pdf", "application/octet-stream");
    private static final long MB = 1024L * 1024L;
    private final COSClient cosClient;
    private final TencentCloudProperties properties;

    @Override
    public StoredObjectRef store(Long userId, String mediaType, MultipartFile file) {
        if (userId == null || !MEDIA_TYPES.contains(mediaType)) throw new BizException("不支持的素材类型");
        validate(mediaType, file);
        String objectKey = objectKey(userId, mediaType, file.getOriginalFilename());
        String privateBucket = bucket();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            cosClient.putObject(new PutObjectRequest(privateBucket, objectKey, file.getInputStream(), metadata));
            return new StoredObjectRef("cos", privateBucket, objectKey, null);
        } catch (Exception error) {
            throw new BizException(ResultCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public StoredObjectRef storeGenerated(Long userId, String mediaType, byte[] bytes, String contentType, String extension) {
        if (userId == null || bytes == null || bytes.length == 0) throw new BizException("生成素材为空");
        String privateBucket = bucket();
        String objectKey = objectKey(userId, mediaType, "generated" + extension);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType(contentType);
        cosClient.putObject(new PutObjectRequest(privateBucket, objectKey, new ByteArrayInputStream(bytes), metadata));
        return new StoredObjectRef("cos", privateBucket, objectKey, null);
    }

    @Override
    public SignedAccess issueAccessUrl(String bucketCode, String objectKey, Duration duration) {
        Instant expiresAt = Instant.now().plus(duration);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketCode, objectKey, HttpMethodName.GET);
        request.setExpiration(Date.from(expiresAt));
        return new SignedAccess(cosClient.generatePresignedUrl(request).toString(), expiresAt);
    }

    @Override
    public void delete(String bucketCode, String objectKey) {
        cosClient.deleteObject(bucketCode, objectKey);
    }

    private String objectKey(Long userId, String mediaType, String originalName) {
        String extension = "";
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0 && dot < originalName.length() - 1) extension = originalName.substring(dot).toLowerCase();
        }
        return "actor-private/" + userId + "/" + mediaType + "/"
                + LocalDate.now().toString().replace('-', '/') + "/"
                + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    private void validate(String mediaType, MultipartFile file) {
        String contentType = file.getContentType();
        long maxSize;
        boolean allowed;
        if ("photo".equals(mediaType)) { allowed = PHOTO_TYPES.contains(contentType); maxSize = 10 * MB; }
        else if ("video".equals(mediaType)) { allowed = VIDEO_TYPES.contains(contentType); maxSize = 100 * MB; }
        else { allowed = PDF_TYPES.contains(contentType) && hasPdfName(file.getOriginalFilename()) && hasPdfMagic(file); maxSize = 20 * MB; }
        if (!allowed) throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        if (file.getSize() > maxSize) throw new BizException(ResultCode.FILE_SIZE_EXCEEDED);
    }

    private boolean hasPdfName(String name) { return name != null && name.toLowerCase().endsWith(".pdf"); }
    private boolean hasPdfMagic(MultipartFile file) {
        try (var input = file.getInputStream()) {
            byte[] header = input.readNBytes(5);
            return header.length == 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
        } catch (Exception ignored) { return false; }
    }

    private String bucket() {
        String bucket = properties.getCos().getPrivateBucketName();
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalStateException("Private actor asset bucket is not configured");
        }
        return bucket;
    }
}
