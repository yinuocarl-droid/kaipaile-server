package com.kaipai.integration.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.model.ai.dto.AiImageProviderPublicConfigDTO;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.config.AiImageProviderRuntimeConfig;
import com.kaipai.service.ai.config.AiProfileCardProperties;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiProfileImageProviderMultiReferenceTest {

    @Test
    void serializesPersonAndBackgroundAsTwoRealMultipartImageParts() throws Exception {
        AtomicInteger sourceDownloads = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                if (exchange.getRequestURI().getPath().endsWith(".png")) {
                    sourceDownloads.incrementAndGet();
                    send(exchange, 200, "image/png", new byte[] {1, 2, 3});
                    return;
                }
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
                send(exchange, 200, "application/json",
                        "{\"data\":[{\"b64_json\":\"AQID\"}]}".getBytes(StandardCharsets.UTF_8));
            } catch (Exception error) {
                send(exchange, 500, "text/plain", error.getMessage().getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            AiImageProviderConfigService configService = mock(AiImageProviderConfigService.class);
            when(configService.findRuntimeConfig("openai")).thenReturn(Optional.of(runtime(endpoint)));
            when(configService.resolveModelCode("openai", "gpt-image-1.5")).thenReturn("gpt-image-1.5");
            OpenAiProfileImageProvider provider = new OpenAiProfileImageProvider(
                    new AiProfileCardProperties(), configService, new ObjectMapper());

            provider.generate(new AiProfileImageGenerationRequest(
                    "task-1", "gpt-image-1.5", "actor-card", "person_background_ai",
                    endpoint + "/person.png", endpoint + "/background.png",
                    "keep the person and use the background", null, null));

            assertEquals(2, sourceDownloads.get());
            assertTrue(provider.supportsReferenceImageCount(2));
            String body = requestBody.get();
            assertEquals(2, count(body, "name=\"image[]\""));
            assertTrue(body.contains("filename=\"person.png\""));
            assertTrue(body.contains("filename=\"background.png\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void oldEightArgumentRequestConstructorRemainsSourceCompatible() {
        AiProfileImageGenerationRequest request = new AiProfileImageGenerationRequest(
                "task-legacy", "model", "scene", "style",
                "https://cdn.test/person.png", "prompt", "negative", "{}");

        assertEquals("https://cdn.test/person.png", request.sourceImageUrl());
        assertEquals(null, request.backgroundImageUrl());
    }

    private AiImageProviderRuntimeConfig runtime(String endpoint) {
        AiImageProviderPublicConfigDTO publicConfig = new AiImageProviderPublicConfigDTO();
        publicConfig.setEndpoint(endpoint);
        publicConfig.setModel("gpt-image-1.5");
        publicConfig.setSize("1024x1536");
        publicConfig.setQuality("high");
        publicConfig.setResponseFormat("png");
        publicConfig.setConnectTimeoutMs(2000);
        publicConfig.setReadTimeoutMs(5000);
        return new AiImageProviderRuntimeConfig(
                "openai", "OpenAI", true, true, publicConfig, Map.of("apiKey", "test-key"));
    }

    private int count(String value, String needle) {
        int result = 0;
        int from = 0;
        while ((from = value.indexOf(needle, from)) >= 0) {
            result++;
            from += needle.length();
        }
        return result;
    }

    private void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
