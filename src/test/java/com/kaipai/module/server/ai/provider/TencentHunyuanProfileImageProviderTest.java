package com.kaipai.module.server.ai.provider;

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
            assertFalse(submitBody.get().contains("Images"));
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
        return new AiProfileImageGenerationRequest(
                "test-task",
                "hunyuan-image-3.0",
                "classic",
                "classic",
                sourceImageUrl,
                "profile image",
                "",
                "{}"
        );
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
