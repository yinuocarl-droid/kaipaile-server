package com.kaipai.common.util;

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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CosUtil {

    private final COSClient cosClient;
    private final CosConfig cosConfig;

    /** 允许上传的图片类型 */
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /** 允许上传的视频类型 */
    private static final List<String> ALLOWED_VIDEO_TYPES = Arrays.asList(
            "video/mp4", "video/quicktime", "video/x-msvideo"
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
        return upload(file, folder);
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
            cosClient.deleteObject(cosConfig.getBucketName(), key);
        } catch (CosClientException e) {
            log.warn("COS 文件删除失败: {}", fileUrl, e);
        }
    }

    private String upload(MultipartFile file, String folder) {
        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        // key 格式: folder/yyyy/MM/dd/uuid.ext
        String key = String.format("%s/%s/%s%s",
                folder,
                LocalDate.now().toString().replace("-", "/"),
                UUID.randomUUID().toString().replace("-", ""),
                ext);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            PutObjectRequest request = new PutObjectRequest(
                    cosConfig.getBucketName(), key, file.getInputStream(), metadata);
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

    private String buildUrl(String key) {
        return String.format("https://%s.cos.%s.myqcloud.com/%s",
                cosConfig.getBucketName(), cosConfig.getRegion(), key);
    }

    private String extractKeyFromUrl(String fileUrl) {
        String prefix = String.format("https://%s.cos.%s.myqcloud.com/",
                cosConfig.getBucketName(), cosConfig.getRegion());
        return fileUrl.startsWith(prefix) ? fileUrl.substring(prefix.length()) : fileUrl;
    }
}
