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
    private final COSClient cosClient;
    private final TencentCloudProperties properties;

    @Override
    public StoredObjectRef store(Long userId, String mediaType, MultipartFile file) {
        if (userId == null || !MEDIA_TYPES.contains(mediaType)) throw new BizException("不支持的素材类型");
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

    private String bucket() {
        String bucket = properties.getCos().getPrivateBucketName();
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalStateException("Private actor asset bucket is not configured");
        }
        return bucket;
    }
}
