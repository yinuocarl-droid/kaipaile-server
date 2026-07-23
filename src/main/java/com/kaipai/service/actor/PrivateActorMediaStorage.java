package com.kaipai.service.actor;
import java.time.Instant;
import org.springframework.web.multipart.MultipartFile;
public interface PrivateActorMediaStorage {
    StoredObjectRef store(Long userId, String mediaType, MultipartFile file);
    SignedAccess issueAccessUrl(String bucketCode, String objectKey, java.time.Duration duration);
    void delete(String bucketCode, String objectKey);
    record StoredObjectRef(String storageProvider, String bucketCode, String objectKey, String thumbnailObjectKey) {}
    record SignedAccess(String accessUrl, Instant expiresAt) {}
}
