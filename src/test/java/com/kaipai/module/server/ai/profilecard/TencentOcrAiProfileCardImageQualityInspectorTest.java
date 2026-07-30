package com.kaipai.service.ai.profilecard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.service.ai.AiImageProviderConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void imageNoTextResponseShouldBeAccepted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TencentOcrAiProfileCardImageQualityInspector inspector = new TencentOcrAiProfileCardImageQualityInspector(
                mock(AiImageProviderConfigService.class),
                objectMapper);
        var root = objectMapper.readTree("""
                {
                  "Response": {
                    "Error": {
                      "Code": "FailedOperation.ImageNoText",
                      "Message": "upstream message changed"
                    },
                    "RequestId": "ocr-image-no-text-request"
                  }
                }
                """);

        AiProfileCardImageQualityInspection inspection = ReflectionTestUtils.invokeMethod(
                inspector,
                "inspectTencentResponse",
                root);

        assertTrue(inspection.accepted());
        assertFalse(inspection.retryable());
    }

    @Test
    void emptyTextDetectionsShouldBeAccepted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TencentOcrAiProfileCardImageQualityInspector inspector = new TencentOcrAiProfileCardImageQualityInspector(
                mock(AiImageProviderConfigService.class),
                objectMapper);
        var root = objectMapper.readTree("""
                {
                  "Response": {
                    "TextDetections": [],
                    "RequestId": "ocr-empty-text-detections-request"
                  }
                }
                """);

        AiProfileCardImageQualityInspection inspection = ReflectionTestUtils.invokeMethod(
                inspector,
                "inspectTencentResponse",
                root);

        assertTrue(inspection.accepted());
        assertFalse(inspection.retryable());
    }

    @Test
    void highConfidenceTextDetectionShouldBeRejected() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TencentOcrAiProfileCardImageQualityInspector inspector = new TencentOcrAiProfileCardImageQualityInspector(
                mock(AiImageProviderConfigService.class),
                objectMapper);
        var root = objectMapper.readTree("""
                {
                  "Response": {
                    "TextDetections": [
                      {
                        "DetectedText": "演员招募",
                        "Confidence": 99
                      }
                    ],
                    "RequestId": "ocr-high-confidence-text-request"
                  }
                }
                """);

        AiProfileCardImageQualityInspection inspection = ReflectionTestUtils.invokeMethod(
                inspector,
                "inspectTencentResponse",
                root);

        assertFalse(inspection.accepted());
        assertTrue(inspection.retryable());
        assertTrue(inspection.reason().contains("演员招募"));
    }

    @Test
    void otherTencentApiErrorShouldRemainFailure() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TencentOcrAiProfileCardImageQualityInspector inspector = new TencentOcrAiProfileCardImageQualityInspector(
                mock(AiImageProviderConfigService.class),
                objectMapper);
        var root = objectMapper.readTree("""
                {
                  "Response": {
                    "Error": {
                      "Code": "FailedOperation.ImageDecodeFailed",
                      "Message": "照片中未检测到文本"
                    },
                    "RequestId": "ocr-image-decode-failed-request"
                  }
                }
                """);

        BizException error = assertThrows(
                BizException.class,
                () -> ReflectionTestUtils.invokeMethod(inspector, "inspectTencentResponse", root));

        assertTrue(error.getMessage().contains("ImageDecodeFailed"));
    }

    @Test
    void similarImageNoTextCodeShouldRemainFailure() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TencentOcrAiProfileCardImageQualityInspector inspector = new TencentOcrAiProfileCardImageQualityInspector(
                mock(AiImageProviderConfigService.class),
                objectMapper);
        var root = objectMapper.readTree("""
                {
                  "Response": {
                    "Error": {
                      "Code": "FailedOperation.ImageNoTextUnexpected",
                      "Message": "照片中未检测到文本"
                    },
                    "RequestId": "ocr-similar-image-no-text-request"
                  }
                }
                """);

        BizException error = assertThrows(
                BizException.class,
                () -> ReflectionTestUtils.invokeMethod(inspector, "inspectTencentResponse", root));

        assertTrue(error.getMessage().contains("ImageNoTextUnexpected"));
    }
}
