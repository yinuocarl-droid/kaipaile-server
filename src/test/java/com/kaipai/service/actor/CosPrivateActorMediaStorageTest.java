package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kaipai.common.config.TencentCloudProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CosPrivateActorMediaStorageTest {
    @Test void storesUnderOwnerScopedKeyAndReturnsNoPublicUrl() throws Exception {
        COSClient client = mock(COSClient.class);
        TencentCloudProperties properties = properties();
        CosPrivateActorMediaStorage storage = new CosPrivateActorMediaStorage(client, properties);
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {1});

        var result = storage.store(7L, "photo", file);

        assertEquals("cos", result.storageProvider());
        assertEquals("private-bucket", result.bucketCode());
        assertTrue(result.objectKey().startsWith("actor-private/7/photo/"));
        verify(client).putObject(any(PutObjectRequest.class));
    }

    @Test void signsRequestedObjectForOnlyRequestedDuration() throws Exception {
        COSClient client = mock(COSClient.class);
        when(client.generatePresignedUrl(any())).thenReturn(new URL("https://signed.example/a"));
        CosPrivateActorMediaStorage storage = new CosPrivateActorMediaStorage(client, properties());

        var result = storage.issueAccessUrl("private-bucket", "actor-private/7/photo/a.jpg", Duration.ofMinutes(10));

        assertEquals("https://signed.example/a", result.accessUrl());
        assertTrue(result.expiresAt().isAfter(java.time.Instant.now().plusSeconds(590)));
    }

    @Test void missingDedicatedPrivateBucketFailsClosed() {
        TencentCloudProperties properties = new TencentCloudProperties();
        properties.getCos().setBucketName("legacy-public-bucket");
        CosPrivateActorMediaStorage storage = new CosPrivateActorMediaStorage(mock(COSClient.class), properties);
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {1});

        assertThrows(IllegalStateException.class, () -> storage.store(7L, "photo", file));
    }

    private TencentCloudProperties properties() {
        TencentCloudProperties properties = new TencentCloudProperties();
        properties.getCos().setBucketName("private-bucket");
        properties.getCos().setPrivateBucketName("private-bucket");
        properties.getCos().setRegion("ap-guangzhou");
        return properties;
    }
}
