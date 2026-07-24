package com.kaipai.service.ai.profileimport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.profileimport.DeepSeekProfileTextExtractor;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class DeepSeekProfileTextExtractorTest {
    @Test
    void requestUsesGovernedSchemaPromptOutputLimitAndConfiguredTimeouts() throws Exception {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn("{\"profileCandidates\":[],\"workCandidates\":[]}");
        AiProfileImportConfig config = config();
        config.setConnectTimeoutMs(3210);
        config.setReadTimeoutMs(45670);
        config.setMaxOutputTokens(6789);

        new DeepSeekProfileTextExtractor(transport)
                .extract(config, "sk-memory-only", "演员王火火 [图片]", "req-1");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(transport).post(eq(config.getEndpoint()), eq("sk-memory-only"), payload.capture(),
                eq(3210), eq(45670));
        JsonNode root = new ObjectMapper().readTree(payload.getValue());
        assertEquals(6789, root.path("max_tokens").asInt());
        assertEquals("system", root.path("messages").path(0).path("role").asText());
        String system = root.path("messages").path(0).path("content").asText();
        assertTrue(system.contains("profileCandidates"));
        assertTrue(system.contains("workCandidates"));
        assertTrue(system.contains("sourceText"));
        assertTrue(system.contains("inferred_from_roles"));
        assertTrue(system.contains("[图片]") && system.contains("不得创建素材"));
        assertEquals("演员王火火 [图片]",
                root.path("messages").path(1).path("content").asText());
    }

    @Test
    void invalidResponseGetsOneRepairThenReturns46007() {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn("not json", "still not json");
        DeepSeekProfileTextExtractor extractor = new DeepSeekProfileTextExtractor(transport);

        BizException error = assertThrows(BizException.class,
                () -> extractor.extract(config(), "sk-memory-only", "演员王火火", "req-1"));

        assertEquals(46007, error.getCode());
        verify(transport, times(2)).post(
                eq(config().getEndpoint()), eq("sk-memory-only"), any(), anyInt(), anyInt());
    }

    @Test
    void chatCompletionEnvelopeReturnsStructuredContent() {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(any(), any(), any(), anyInt(), anyInt())).thenReturn(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"profileCandidates\\\":[],"
                        + "\\\"workCandidates\\\":[]}\"}}]}");

        var result = new DeepSeekProfileTextExtractor(transport)
                .extract(config(), "sk-memory-only", "文本", "req-1");

        assertEquals(0, result.path("profileCandidates").size());
    }

    @Test
    void timeoutMapsTo46006() {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new ProfileImportHttpTransport.Timeout());

        BizException error = assertThrows(BizException.class,
                () -> new DeepSeekProfileTextExtractor(transport).extract(config(), "sk", "文本", "req"));

        assertEquals(46006, error.getCode());
    }

    @Test
    void repairTimeoutStillMapsTo46006() {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn("not json")
                .thenThrow(new ProfileImportHttpTransport.Timeout());

        BizException error = assertThrows(BizException.class,
                () -> new DeepSeekProfileTextExtractor(transport)
                        .extract(config(), "sk", "文本", "req"));

        assertEquals(46006, error.getCode());
    }

    @Test
    void repairProviderFailureStillMapsTo46002() {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn("not json")
                .thenThrow(new IllegalStateException("provider failed"));

        BizException error = assertThrows(BizException.class,
                () -> new DeepSeekProfileTextExtractor(transport)
                        .extract(config(), "sk", "文本", "req"));

        assertEquals(46002, error.getCode());
    }

    @Test
    void oversizedProviderResponseIsRejectedBeforeRepairEcho() {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn("x".repeat(70_000));
        AiProfileImportConfig config = config();
        config.setMaxOutputTokens(1000);

        BizException error = assertThrows(BizException.class,
                () -> new DeepSeekProfileTextExtractor(transport)
                        .extract(config, "sk", "文本", "req"));

        assertEquals(46007, error.getCode());
        verify(transport, times(1)).post(any(), any(), any(), anyInt(), anyInt());
    }

    private AiProfileImportConfig config() {
        AiProfileImportConfig config = new AiProfileImportConfig();
        config.setEndpoint("https://api.deepseek.com/chat/completions");
        config.setModelName("deepseek-chat");
        config.setReadTimeoutMs(1000);
        config.setConnectTimeoutMs(1000);
        config.setMaxOutputTokens(8000);
        return config;
    }
}
