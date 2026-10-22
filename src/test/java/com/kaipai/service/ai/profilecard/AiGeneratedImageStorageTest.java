package com.kaipai.service.ai.profilecard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kaipai.common.config.TencentCloudProperties;
import com.qcloud.cos.COSClient;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiGeneratedImageStorageTest {

    @Test
    void signsManagedCosObjectWithoutPersistingTheSignature() throws Exception {
        COSClient client = mock(COSClient.class);
        when(client.generatePresignedUrl(any())).thenReturn(
                new URL("https://test-bucket.cos.ap-shanghai.myqcloud.com/actor-card-expand/result.png?signature=test"));
        AiGeneratedImageStorage storage = new AiGeneratedImageStorage(client, properties());
        String stableUrl = "https://test-bucket.cos.ap-shanghai.myqcloud.com/actor-card-expand/result.png";

        String readUrl = storage.issueReadUrl(stableUrl, Duration.ofMinutes(10));

        assertTrue(storage.isManagedUrl(stableUrl));
        assertEquals(stableUrl + "?signature=test", readUrl);
        verify(client).generatePresignedUrl(any());
    }

    @Test
    void leavesExternalHttpsUrlUnchanged() {
        COSClient client = mock(COSClient.class);
        AiGeneratedImageStorage storage = new AiGeneratedImageStorage(client, properties());
        String externalUrl = "https://provider.example/generated.png";

        assertFalse(storage.isManagedUrl(externalUrl));
        assertEquals(externalUrl, storage.issueReadUrl(externalUrl, Duration.ofMinutes(10)));
        verify(client, never()).generatePresignedUrl(any());
    }

    @Test
    void stripsInjectedHtmlSuffixBeforeSigningManagedCosObject() throws Exception {
        COSClient client = mock(COSClient.class);
        when(client.generatePresignedUrl(any())).thenReturn(
                new URL("https://test-bucket.cos.ap-shanghai.myqcloud.com/actor-card-expand/result.png?signature=test"));
        AiGeneratedImageStorage storage = new AiGeneratedImageStorage(client, properties());
        String cleanUrl = "https://test-bucket.cos.ap-shanghai.myqcloud.com/actor-card-expand/result.png";
        String contaminatedUrl = cleanUrl
                + "'\\\"></title></a><META HTTP-EQUIV=REFRESH CONTENT=0;URL=https://evil.invalid/>";

        assertTrue(storage.isManagedUrl(contaminatedUrl));
        assertEquals(cleanUrl + "?signature=test",
                storage.issueReadUrl(contaminatedUrl, Duration.ofMinutes(10)));
        verify(client).generatePresignedUrl(any());
    }

    private TencentCloudProperties properties() {
        TencentCloudProperties properties = new TencentCloudProperties();
        properties.getCos().setBucketName("test-bucket");
        properties.getCos().setRegion("ap-shanghai");
        return properties;
    }
}
