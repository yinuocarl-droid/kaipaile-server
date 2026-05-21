package com.kaipai.module.server.ai.profilecard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.server.ai.service.AiImageProviderConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TencentOcrAiProfileCardImageQualityInspectorTest {

    @Test
    void inspectCoverShouldReportUnavailableWhenTencentOcrIsNotConfigured() {
        AiImageProviderConfigService configService = mock(AiImageProviderConfigService.class);
        when(configService.findRuntimeConfig("tencent-hunyuan")).thenReturn(Optional.empty());
        TencentOcrAiProfileCardImageQualityInspector inspector = new TencentOcrAiProfileCardImageQualityInspector(
                configService,
                new ObjectMapper());

        AiProfileCardImageQualityInspection inspection = inspector.inspectCover(
                "https://example.com/generated.png",
                "tencent-hunyuan");

        assertFalse(inspection.accepted());
        assertFalse(inspection.retryable());
        assertTrue(inspection.reason().contains("无法执行"));
    }

    @Test
    void blockedTextShouldMatchChineseAndAsciiWordsWithHighConfidence() {
        TencentOcrAiProfileCardImageQualityInspector inspector = new TencentOcrAiProfileCardImageQualityInspector(
                mock(AiImageProviderConfigService.class),
                new ObjectMapper());

        boolean chineseBlocked = ReflectionTestUtils.invokeMethod(inspector, "isBlockedText", "中文乱码", 88d);
        boolean asciiBlocked = ReflectionTestUtils.invokeMethod(inspector, "isBlockedText", "TEXT123", 88d);
        boolean lowConfidenceNoise = ReflectionTestUtils.invokeMethod(inspector, "isBlockedText", "ab", 30d);

        assertTrue(chineseBlocked);
        assertTrue(asciiBlocked);
        assertFalse(lowConfidenceNoise);
    }

    @Test
    void ocrUnavailableErrorShouldBeTreatedAsUnavailable() {
        TencentOcrAiProfileCardImageQualityInspector inspector = new TencentOcrAiProfileCardImageQualityInspector(
                mock(AiImageProviderConfigService.class),
                new ObjectMapper());

        boolean unavailable = ReflectionTestUtils.invokeMethod(
                inspector,
                "isOcrUnavailable",
                new BizException("腾讯 OCR API 错误：{\"Code\":\"FailedOperation.UnOpenError\",\"Message\":\"服务未开通\"}"));

        assertTrue(unavailable);
    }
}
