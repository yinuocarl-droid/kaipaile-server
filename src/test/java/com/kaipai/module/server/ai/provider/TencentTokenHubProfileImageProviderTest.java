package com.kaipai.integration.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.ai.dto.AiImageProviderPublicConfigDTO;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.config.AiImageProviderRuntimeConfig;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Contract fixture derives from the B11 real v35 response, never old aiart or chat APIs. */
class TencentTokenHubProfileImageProviderTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private String base;
    private final List<JsonNode> bodies = new ArrayList<>();
    private final List<String> auth = new ArrayList<>();
    private int downloads;
    private int responseStatus = 200;
    private String response = """
            {"assembled_history":[{"role":"tool","content":[{"type":"image_url","image_url":{"url":"https://input.invalid/not-output.png"}}]}],
             "choices":[{"delta":{"type":"text","text":"done"}},
                        {"delta":{"type":"image","image":{"source":"generate","url":"https://result.invalid/generated.png?signature=keep","width":1152,"height":2048}}}],
             "request_id":"fixture-request","tokenhub_usage":{"total_tokens":15000}}
            """;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body;
            String contentType;
            int status;
            if (exchange.getRequestMethod().equals("GET")) {
                downloads++;
                body = new byte[]{1, 2, 3}; contentType = "image/png"; status = 200;
            } else {
                assertEquals("/v1/wand/hunyuan-image/v35-generation", exchange.getRequestURI().getPath());
                assertNull(exchange.getRequestHeaders().getFirst("X-TC-Action"));
                auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
                bodies.add(json.readTree(exchange.getRequestBody().readAllBytes()));
                body = response.getBytes(StandardCharsets.UTF_8);contentType = "application/json";status = responseStatus;
            }
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();base = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void dualWriterPreservesDistinctReferencesAndSignedQueries() {
        var provider = provider("test-private-key", true, "hy-image-v3.5-preview", endpoint());
        var result = provider.generate(request("task-1", true));
        assertEquals("https://result.invalid/generated.png?signature=keep", result.imageUrl());
        assertEquals(2, downloads);assertEquals(1, bodies.size());
        assertEquals(List.of("Bearer test-private-key"), auth);
        var body = bodies.get(0);
        assertEquals("hy-image-v3.5-preview", body.path("model").asText());
        assertEquals(1, body.path("messages").size());
        assertEquals("user", body.at("/messages/0/role").asText());
        assertEquals(3, body.at("/messages/0/content").size());
        assertEquals(base + "/person.png?signature=person", body.at("/messages/0/content/1/image_url/url").asText());
        assertEquals(base + "/scene.png?signature=scene", body.at("/messages/0/content/2/image_url/url").asText());
        assertTrue(body.at("/messages/0/content/0/text").asText().contains("no rectangular cutout"));
        assertFalse(body.has("Images"));assertFalse(body.has("Resolution"));
        assertTrue(provider.supportsReferenceImageCount(1));assertTrue(provider.supportsReferenceImageCount(2));
        assertFalse(provider.supportsReferenceImageCount(0));assertFalse(provider.supportsReferenceImageCount(3));
    }
    @Test void singleReferenceDoesNotInventBackground() {
        provider("key", true, "hy-image-v3.5-preview", endpoint()).generate(request("task-single", false));
        assertEquals(1, downloads);assertEquals(2, bodies.get(0).at("/messages/0/content").size());
    }
    @Test void dualReferenceUsesWaistUpFramingInsteadOfGroundedFullBody() {
        provider("key", true, "hy-image-v3.5-preview", endpoint()).generate(request("framing-two", true));
        String text = bodies.get(0).at("/messages/0/content/0/text").asText();
        assertTrue(text.contains("腰上中近景半身肖像"));
        assertTrue(text.contains("画面底边自然截于腰腹处"));
        assertTrue(text.contains("躯干和衣物连续延伸出画外"));
        assertTrue(text.contains("优先不呈现地面"));
        assertTrue(text.contains("图1原始裁边不是人物轮廓"));
        assertFalse(text.contains("或完整落地"));
        assertTrue(text.contains("test-prompt"));
        assertEquals(1, bodies.size());
    }
    @Test void singleReferenceKeepsItsExistingDefaultComposition() {
        provider("key", true, "hy-image-v3.5-preview", endpoint()).generate(request("framing-one", false));
        String text = bodies.get(0).at("/messages/0/content/0/text").asText();
        assertFalse(text.contains("腰上中近景半身肖像"));
        assertTrue(text.contains("自然延伸至画面外或完整落地"));
        assertEquals(2, bodies.get(0).at("/messages/0/content").size());
    }
    @Test void sessionsAreDeterministicPerTaskButDistinctAcrossTasks() {
        var provider = provider("key", true, "hy-image-v3.5-preview", endpoint());
        provider.generate(request("task-one", false));provider.generate(request("task-two", false));
        provider.generate(request("task-one", false));
        assertEquals(bodies.get(0).path("session"), bodies.get(2).path("session"));
        assertNotEquals(bodies.get(0).path("session"), bodies.get(1).path("session"));
    }
    @Test void absentKeyFailsBeforeAnyNetwork() {
        assertThrows(BizException.class, () -> provider("", true, "hy-image-v3.5-preview", endpoint()).generate(request("t", true)));
        assertEquals(0, downloads);assertTrue(bodies.isEmpty());
    }
    @Test void disabledConfigFailsBeforeAnyNetwork() {
        assertThrows(BizException.class, () -> provider("key", false, "hy-image-v3.5-preview", endpoint()).generate(request("t", true)));
        assertEquals(0, downloads);assertTrue(bodies.isEmpty());
    }
    @Test void unsupportedModelCannotSilentlyUseAnotherProtocol() {
        assertThrows(BizException.class, () -> provider("key", true, "hunyuan-image-3.0", endpoint()).generate(request("t", true)));
        assertEquals(0, downloads);assertTrue(bodies.isEmpty());
    }
    @Test void otherHostCannotReceiveTokenHubKey() {
        assertThrows(BizException.class, () -> provider("key", true, "hy-image-v3.5-preview", "https://other.invalid/v35-generation").generate(request("t", true)));
        assertEquals(0, downloads);
    }
    @Test void errorDoesNotRetryOrLeakBodySecretsAndSignedUrls() {
        responseStatus=429;response="{\"error\":{\"message\":\"test-private-key https://private.invalid/a?sign=secret\"}}";
        var error=assertThrows(BizException.class, () -> provider("test-private-key",true,"hy-image-v3.5-preview",endpoint()).generate(request("t",true)));
        assertEquals(1,bodies.size());assertFalse(error.getMessage().contains("test-private-key"));
        assertFalse(error.getMessage().contains("private.invalid"));assertTrue(error.getMessage().contains("429"));
    }
    @Test void onlyHistoryImageIsNotAnOutput() {
        response="{\"assembled_history\":[{\"role\":\"tool\",\"content\":[{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://input.invalid/photo.png\"}}]}]}";
        assertThrows(BizException.class, () -> provider("key",true,"hy-image-v3.5-preview",endpoint()).generate(request("t",false)));
        assertEquals(1,bodies.size());
    }
    @Test void businessErrorCannotBeMistakenForGeneratedImage() {
        response="{\"error\":{\"message\":\"private\"},\"choices\":[{\"delta\":{\"type\":\"image\",\"image\":{\"source\":\"generate\",\"url\":\"https://result.invalid/a.png\"}}}]}";
        assertThrows(BizException.class, () -> provider("key",true,"hy-image-v3.5-preview",endpoint()).generate(request("t",false)));
    }
    @Test void malformedResponseDoesNotLeakRawJson() {
        response="private-key invalid json";
        var e=assertThrows(BizException.class, () -> provider("key",true,"hy-image-v3.5-preview",endpoint()).generate(request("t",false)));
        assertFalse(e.getMessage().contains("private-key"));assertEquals(1,bodies.size());
    }
    @Test void nonHttpsOutputIsRejected() {
        response="{\"choices\":[{\"delta\":{\"type\":\"image\",\"image\":{\"source\":\"generate\",\"url\":\"http://result.invalid/a.png\"}}}]}";
        assertThrows(BizException.class, () -> provider("key",true,"hy-image-v3.5-preview",endpoint()).generate(request("t",false)));
    }
    @Test void missingTaskIdentityIsRejectedBeforeSubmission() {
        assertThrows(BizException.class, () -> provider("key",true,"hy-image-v3.5-preview",endpoint()).generate(request(" ",true)));
        assertTrue(bodies.isEmpty());
    }

    private String endpoint() { return base + "/v1/wand/hunyuan-image/v35-generation"; }
    private TencentTokenHubProfileImageProvider provider(String key, boolean enabled, String model, String endpoint) {
        var config=new AiImageProviderPublicConfigDTO();config.setEndpoint(endpoint);config.setModel(model);
        config.setReadTimeoutMs(2000);config.setConnectTimeoutMs(2000);
        var service=mock(AiImageProviderConfigService.class);
        when(service.findRuntimeConfig("tencent-tokenhub")).thenReturn(Optional.of(new AiImageProviderRuntimeConfig("tencent-tokenhub","TokenHub",enabled,true,config,Map.of("apiKey",key))));
        when(service.resolveModelCode("tencent-tokenhub","hy-image-v3.5-preview")).thenReturn(model);
        return new TencentTokenHubProfileImageProvider(service,json);
    }
    private AiProfileImageGenerationRequest request(String taskId, boolean background) {
        return new AiProfileImageGenerationRequest(taskId,"hy-image-v3.5-preview","actor_card_main_visual","classic",base+"/person.png?signature=person",background?base+"/scene.png?signature=scene":null,"test-prompt",null,null);
    }
}
