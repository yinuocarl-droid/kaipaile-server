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

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiGeneratedImageStorage {

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final COSClient cosClient;
    private final CosConfig cosConfig;

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

    private String normalizeContentType(String contentType) {
        String normalized = StringUtils.hasText(contentType) ? contentType.trim().toLowerCase() : "image/png";
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

    private String buildUrl(String key) {
        return String.format("https://%s.cos.%s.myqcloud.com/%s",
                cosConfig.getBucketName(), cosConfig.getRegion(), key);
    }
}
