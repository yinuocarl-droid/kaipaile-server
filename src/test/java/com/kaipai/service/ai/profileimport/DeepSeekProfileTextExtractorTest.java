package com.kaipai.service.ai.profileimport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.profileimport.DeepSeekProfileTextExtractor;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import org.junit.jupiter.api.Test;

class DeepSeekProfileTextExtractorTest {
    @Test
    void invalidResponseGetsOneRepairThenReturns46007() {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(any(), any(), any())).thenReturn("not json", "still not json");
        DeepSeekProfileTextExtractor extractor = new DeepSeekProfileTextExtractor(transport);

        BizException error = assertThrows(BizException.class,
                () -> extractor.extract(config(), "sk-memory-only", "演员王火火", "req-1"));

        assertEquals(46007, error.getCode());
        verify(transport, times(2)).post(eq(config().getEndpoint()), eq("sk-memory-only"), any());
    }

    @Test
    void chatCompletionEnvelopeReturnsStructuredContent() {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(any(), any(), any())).thenReturn(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"profileCandidates\\\":[],"
                        + "\\\"workCandidates\\\":[]}\"}}]}");

        var result = new DeepSeekProfileTextExtractor(transport)
                .extract(config(), "sk-memory-only", "文本", "req-1");

        assertEquals(0, result.path("profileCandidates").size());
    }

    @Test
    void timeoutMapsTo46006() {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(any(), any(), any())).thenThrow(new ProfileImportHttpTransport.Timeout());

        BizException error = assertThrows(BizException.class,
                () -> new DeepSeekProfileTextExtractor(transport).extract(config(), "sk", "文本", "req"));

        assertEquals(46006, error.getCode());
    }

    private AiProfileImportConfig config() {
        AiProfileImportConfig config = new AiProfileImportConfig();
        config.setEndpoint("https://api.deepseek.com/chat/completions");
        config.setModelName("deepseek-chat");
        config.setReadTimeoutMs(1000);
        return config;
    }
}
