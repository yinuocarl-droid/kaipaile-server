package com.kaipai.service.actor;
import java.time.Instant;
public interface PrivateActorMediaStorage {
    SignedAccess issueAccessUrl(String bucketCode, String objectKey, java.time.Duration duration);
    void delete(String bucketCode, String objectKey);
    record StoredObjectRef(String storageProvider, String bucketCode, String objectKey, String thumbnailObjectKey) {}
    record SignedAccess(String accessUrl, Instant expiresAt) {}
}
