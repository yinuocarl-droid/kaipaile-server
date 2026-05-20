package com.kaipai.module.server.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.ai.dto.AiImageProviderPublicConfigDTO;
import com.kaipai.module.server.ai.config.AiImageProviderRuntimeConfig;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;
import com.kaipai.module.server.ai.service.AiImageProviderConfigService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TencentHunyuanProfileImageProviderTest {

    @Test
    void generateShouldCallTencentWithoutRestrictedHostHeaderFailure() throws Exception {
        AtomicInteger submitCalls = new AtomicInteger();
        AtomicInteger queryCalls = new AtomicInteger();
        HttpServer server = startServer((exchange) -> {
            String path = exchange.getRequestURI().getPath();
            if ("/source.png".equals(path)) {
                send(exchange, 200, "application/octet-stream", new byte[]{1, 2, 3, 4});
                return;
            }

            String action = exchange.getRequestHeaders().getFirst("X-TC-Action");
            exchange.getRequestBody().readAllBytes();
            if ("SubmitTextToImageJob".equals(action)) {
                submitCalls.incrementAndGet();
                sendJson(exchange, "{\"Response\":{\"JobId\":\"job-1\"}}");
                return;
            }
            if ("QueryTextToImageJob".equals(action)) {
                queryCalls.incrementAndGet();
                sendJson(exchange, "{\"Response\":{\"JobStatusCode\":\"5\",\"ResultImage\":[\"/generated.png\"]}}");
                return;
            }
            sendJson(exchange, "{\"Response\":{\"Error\":{\"Message\":\"unexpected action\"}}}");
        });

        try {
            String endpoint = endpoint(server);
            TencentHunyuanProfileImageProvider provider = newProvider(endpoint);

            AiProfileImageGenerationResult result = provider.generate(request(endpoint + "/source.png"));

            assertEquals(endpoint + "/generated.png", result.imageUrl());
            assertEquals(1, submitCalls.get());
            assertEquals(1, queryCalls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateShouldRejectNonImageSourceBeforeCallingTencent() throws Exception {
        AtomicInteger tencentCalls = new AtomicInteger();
        HttpServer server = startServer((exchange) -> {
            String path = exchange.getRequestURI().getPath();
            if ("/source.txt".equals(path)) {
                send(exchange, 200, "text/plain", "not an image".getBytes(StandardCharsets.UTF_8));
                return;
            }
            tencentCalls.incrementAndGet();
            sendJson(exchange, "{\"Response\":{\"JobId\":\"job-1\"}}");
        });

        try {
            String endpoint = endpoint(server);
            TencentHunyuanProfileImageProvider provider = newProvider(endpoint);

            BizException error = assertThrows(BizException.class, () -> provider.generate(request(endpoint + "/source.txt")));

            assertTrue(error.getMessage().contains("text/plain"));
            assertEquals(0, tencentCalls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateShouldSubmitPromptOnlyPayloadWhenSourceImageUrlIsBlank() throws Exception {
        AtomicInteger submitCalls = new AtomicInteger();
        AtomicInteger sourceCalls = new AtomicInteger();
        AtomicReference<String> submitBody = new AtomicReference<>();
        ObjectMapper objectMapper = new ObjectMapper();
        HttpServer server = startServer((exchange) -> {
            String path = exchange.getRequestURI().getPath();
            if ("/source.png".equals(path)) {
                sourceCalls.incrementAndGet();
                send(exchange, 200, "image/png", new byte[]{1, 2, 3, 4});
                return;
            }

            String action = exchange.getRequestHeaders().getFirst("X-TC-Action");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if ("SubmitTextToImageJob".equals(action)) {
                submitCalls.incrementAndGet();
                submitBody.set(body);
                sendJson(exchange, "{\"Response\":{\"JobId\":\"job-1\"}}");
                return;
            }
            if ("QueryTextToImageJob".equals(action)) {
                sendJson(exchange, "{\"Response\":{\"JobStatusCode\":\"5\",\"ResultImage\":[\"/generated.png\"]}}");
                return;
            }
            sendJson(exchange, "{\"Response\":{\"Error\":{\"Message\":\"unexpected action\"}}}");
        });

        try {
            String endpoint = endpoint(server);
            TencentHunyuanProfileImageProvider provider = newProvider(endpoint);

            AiProfileImageGenerationResult result = provider.generate(request(" "));

            assertEquals(endpoint + "/generated.png", result.imageUrl());
            assertEquals(1, submitCalls.get());
            assertEquals(0, sourceCalls.get());
            JsonNode payload = objectMapper.readTree(submitBody.get());
            String prompt = payload.path("Prompt").asText();
            assertFalse(payload.has("Images"));
            assertEquals(0, payload.path("LogoAdd").asInt());
            assertEquals(1, payload.path("Revise").asInt());
            assertTrue(prompt.length() <= 1200);
            assertTrue(prompt.contains("9:16"));
            assertTrue(prompt.contains("2160x3840"));
            assertTrue(prompt.contains("构图"));
            assertTrue(prompt.contains("风格"));
            assertTrue(prompt.contains("背景"));
            assertTrue(prompt.contains("Plain, unmarked, symbol-free"));
            assertTrue(prompt.trim().endsWith("Plain, unmarked, symbol-free."));
            assertTrue(prompt.contains("不要可读文字") || prompt.contains("不要文字") || prompt.contains("无人物主体"));
            assertFalse(prompt.contains("Layout directive"));
            assertFalse(prompt.contains("profile-card"));
            assertFalse(prompt.contains("mini-program UI zones"));
            assertFalse(prompt.contains("facts provider"));
            assertFalse(prompt.contains("Skills Provider"));
            assertFalse(prompt.contains("full provider prompt that Tencent rejects"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateShouldSubmitReferenceImagePayloadForResumeAndGalleryPages() throws Exception {
        AtomicInteger submitCalls = new AtomicInteger();
        AtomicInteger sourceCalls = new AtomicInteger();
        List<String> submitBodies = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        HttpServer server = startServer((exchange) -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith(".png")) {
                sourceCalls.incrementAndGet();
                send(exchange, 200, "image/png", new byte[]{1, 2, 3, 4});
                return;
            }

            String action = exchange.getRequestHeaders().getFirst("X-TC-Action");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if ("SubmitTextToImageJob".equals(action)) {
                submitCalls.incrementAndGet();
                submitBodies.add(body);
                sendJson(exchange, "{\"Response\":{\"JobId\":\"job-1\"}}");
                return;
            }
            if ("QueryTextToImageJob".equals(action)) {
                sendJson(exchange, "{\"Response\":{\"JobStatusCode\":\"5\",\"ResultImage\":[\"/generated.png\"]}}");
                return;
            }
            sendJson(exchange, "{\"Response\":{\"Error\":{\"Message\":\"unexpected action\"}}}");
        });

        try {
            String endpoint = endpoint(server);
            TencentHunyuanProfileImageProvider provider = newProvider(endpoint);

            String resumeTailReferenceUrl = endpoint + "/cover-tail-band.png";
            String galleryTailReferenceUrl = endpoint + "/resume-tail-band.png";

            AiProfileImageGenerationResult resumeResult = provider.generate(request(resumeTailReferenceUrl, """
                    {
                      "fixedLayout": {
                        "pageType": "resume",
                        "subjectBox": "布局只接续上一页底部氛围，不复制人物、文字、符号",
                        "identitySafeArea": "标题区保持安静，留给后续原生内容",
                        "safeSurfaceTone": "延续上一页底部的色彩、光线、材质和空间方向",
                        "background": "沿用上一页底部的色彩、光线、材质和空间方向，不复制人物、文字、符号，作为继续衔接的低细节背景"
                      }
                    }
                    """));

            AiProfileImageGenerationResult galleryResult = provider.generate(request(galleryTailReferenceUrl, """
                    {
                      "fixedLayout": {
                        "pageType": "gallery",
                        "subjectBox": "布局只接续上一页底部氛围，不复制人物、文字、符号",
                        "identitySafeArea": "标题区保持安静，留给后续原生内容",
                        "safeSurfaceTone": "延续上一页底部的色彩、光线、材质和空间方向",
                        "background": "沿用上一页底部的色彩、光线、材质和空间方向，不复制人物、文字、符号，作为继续衔接的低细节背景"
                      }
                    }
                    """));

            assertEquals(endpoint + "/generated.png", resumeResult.imageUrl());
            assertEquals(endpoint + "/generated.png", galleryResult.imageUrl());
            assertEquals(2, submitCalls.get());
            assertEquals(2, sourceCalls.get());
            assertEquals(2, submitBodies.size());

            assertReferenceBandPayload(objectMapper.readTree(submitBodies.get(0)), resumeTailReferenceUrl, "resume");
            assertReferenceBandPayload(objectMapper.readTree(submitBodies.get(1)), galleryTailReferenceUrl, "gallery");
        } finally {
            server.stop(0);
        }
    }

    private TencentHunyuanProfileImageProvider newProvider(String endpoint) {
        AiImageProviderConfigService configService = mock(AiImageProviderConfigService.class);
        when(configService.findRuntimeConfig("tencent-hunyuan")).thenReturn(Optional.of(runtimeConfig(endpoint)));
        when(configService.resolveModelCode("tencent-hunyuan", "hunyuan-image")).thenReturn("hunyuan-image-3.0");
        return new TencentHunyuanProfileImageProvider(configService, new ObjectMapper());
    }

    private AiImageProviderRuntimeConfig runtimeConfig(String endpoint) {
        AiImageProviderPublicConfigDTO publicConfig = new AiImageProviderPublicConfigDTO();
        publicConfig.setEndpoint(endpoint);
        publicConfig.setRegion("ap-guangzhou");
        publicConfig.setModel("hunyuan-image-3.0");
        publicConfig.setModelVersion("2022-12-29");
        publicConfig.setResolution("720:1280");
        publicConfig.setCount(1);
        publicConfig.setConnectTimeoutMs(2000);
        publicConfig.setReadTimeoutMs(5000);
        publicConfig.setPollIntervalMs(1);
        publicConfig.setMaxPollAttempts(1);
        return new AiImageProviderRuntimeConfig(
                "tencent-hunyuan",
                "Tencent Hunyuan",
                true,
                true,
                publicConfig,
                Map.of("secretId", "AKID_TEST", "secretKey", "SECRET_TEST")
        );
    }

    private AiProfileImageGenerationRequest request(String sourceImageUrl) {
        return request(sourceImageUrl, """
                {
                  "fixedLayout": {
                    "subjectBox": "hero right side, x=1120-2050, y=120-1420; face center near x=1580,y=520; robe may overlap softly but not cover text-safe zones",
                    "identitySafeArea": "hero left side, x=120-1080, y=120-1320 must remain clean warm ink-wash negative space",
                    "background": "warm ivory full-bleed ink-wash texture, misty period architecture, bridge, bamboo and abstract seal accents without readable characters, no paper sheet edge",
                    "regions": {
                      "facts": "design x=83-368 y=579-763 on 750x1334; provider x=239-1060 y=1667-2197 on 2160x3840; quiet warm matte surface, no fake labels",
                      "skills": "design x=420-675 y=579-763 on 750x1334; provider x=1210-1944 y=1667-2197 on 2160x3840; quiet warm matte surface, no fake chips",
                      "works": "design x=84-666 y=802-914 on 750x1334; provider x=242-1918 y=2310-2632 on 2160x3840; quiet warm matte wide surface, no rows",
                      "photos": "design x=80-671 y=929-1045 on 750x1334; provider x=230-1932 y=2675-3009 on 2160x3840; quiet warm matte strip, no thumbnail frames",
                      "intro": "design x=81-362 y=1077-1236 on 750x1334; provider x=233-1043 y=3101-3558 on 2160x3840; quiet warm matte intro surface",
                      "video": "design x=416-679 y=1077-1236 on 750x1334; provider x=1198-1956 y=3101-3558 on 2160x3840; quiet warm matte video surface, no video-player UI"
                    }
                  }
                }
                """);
    }

    private AiProfileImageGenerationRequest request(String sourceImageUrl, String promptJson) {
        return new AiProfileImageGenerationRequest(
                "test-task",
                "hunyuan-image-3.0",
                "classic",
                "classic",
                sourceImageUrl,
                "full provider prompt that Tencent rejects ".repeat(80),
                "",
                promptJson
        );
    }

    private void assertReferenceBandPayload(JsonNode payload, String referenceImageUrl, String pageType) {
        assertTrue(payload.has("Images"));
        assertEquals(referenceImageUrl, payload.path("Images").get(0).asText());
        assertEquals(0, payload.path("LogoAdd").asInt());
        assertEquals(1, payload.path("Revise").asInt());
        String prompt = payload.path("Prompt").asText();
        assertTrue(prompt.contains("Plain, unmarked, symbol-free"));
        assertTrue(prompt.trim().endsWith("Plain, unmarked, symbol-free."));
        assertTrue(prompt.contains("沿用上一页底部的色彩、光线、材质和空间方向")
                || prompt.contains("延续上一页底部的色彩、光线、材质和空间方向")
                || prompt.contains("连续性参考带"));
        assertTrue(prompt.contains("不复制人物、文字") || prompt.contains("不要复制人物、文字"));
        assertTrue(prompt.contains("Logo") || prompt.contains("logo"));
        assertTrue(prompt.contains("二维码"));
        assertTrue(prompt.contains("前景布局") || prompt.contains("UI 形状") || prompt.contains("UI形状"));
        assertTrue(prompt.contains("No actor portrait or human subject")
                || prompt.contains("无人物主体")
                || prompt.contains("不要人物"));
        assertFalse(prompt.contains("Place the actor"));
        assertFalse(prompt.contains("Layout directive"));
        assertFalse(prompt.contains("profile-card"));
        assertFalse(prompt.contains("mini-program UI zones"));
        assertFalse(prompt.contains("facts provider"));
        assertFalse(prompt.contains("Skills Provider"));
        assertFalse(prompt.contains("full provider prompt that Tencent rejects"));
        assertTrue(prompt.contains(pageType)
                || ("resume".equals(pageType) && prompt.contains("履历"))
                || ("gallery".equals(pageType) && prompt.contains("影像")));
    }

    private HttpServer startServer(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", (exchange) -> {
            try {
                handler.handle(exchange);
            } catch (Exception error) {
                send(exchange, 500, "text/plain", error.getMessage().getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        return server;
    }

    private String endpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void sendJson(HttpExchange exchange, String json) throws IOException {
        send(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
