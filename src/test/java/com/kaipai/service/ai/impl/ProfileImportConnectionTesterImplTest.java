package com.kaipai.service.ai.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import com.kaipai.service.ai.profileimport.ProfileImportHttpTransport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProfileImportConnectionTesterImplTest {
    private static final String VALID_PROVIDER_RESPONSE = """
            {"choices":[{"message":{"content":"{\\"probe\\":true}"}}]}
            """;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendsFixedNoUserDataStructuredJsonProbeThroughSharedTransport() throws Exception {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(anyString(), anyString(), anyString(), eq(3210), eq(45670)))
                .thenReturn(VALID_PROVIDER_RESPONSE);
        AiProfileImportConfig config = config();
        config.setConnectTimeoutMs(3210);
        config.setReadTimeoutMs(45670);
        String apiKey = "sk-never-in-probe-body";

        assertDoesNotThrow(() -> new ProfileImportConnectionTesterImpl(mapper, transport)
                .test(config, apiKey));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(transport).post(eq(config.getEndpoint()), eq(apiKey), body.capture(), eq(3210), eq(45670));
        JsonNode probe = mapper.readTree(body.getValue());
        assertEquals("deepseek-chat", probe.path("model").asText());
        assertEquals("json_object", probe.path("response_format").path("type").asText());
        assertEquals(0, probe.path("temperature").asInt());
        assertEquals(2, probe.path("messages").size());
        assertEquals("system", probe.path("messages").path(0).path("role").asText());
        assertEquals("user", probe.path("messages").path(1).path("role").asText());
        assertFalse(body.getValue().contains(apiKey));
        assertFalse(body.getValue().contains("王火火"));
        assertFalse(body.getValue().contains("rawText"));
        assertFalse(body.getValue().contains("sourceText"));
    }

    @Test
    void rejectsEveryTwoHundredResponseWithoutAnObjectInMessageContent() {
        List<String> invalidResponses = List.of(
                "",
                "not-json",
                "[]",
                "{}",
                "{\"error\":{\"message\":\"bad key\"}}",
                "{\"choices\":[]}",
                "{\"choices\":[{}]}",
                "{\"choices\":[{\"message\":{}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"\"}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"[]\"}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"true\"}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"{}[]\"}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}[]");

        for (String response : invalidResponses) {
            ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
            when(transport.post(anyString(), anyString(), anyString(), eq(3000), eq(30000)))
                    .thenReturn(response);

            BizException error = assertThrows(BizException.class,
                    () -> new ProfileImportConnectionTesterImpl(mapper, transport)
                            .test(config(), "sk-memory"), response);

            assertEquals("DeepSeek 连接测试失败", error.getMessage());
        }
    }

    @Test
    void sharedTransportFailureIsReportedAsConnectionFailure() {
        ProfileImportHttpTransport transport = mock(ProfileImportHttpTransport.class);
        when(transport.post(anyString(), anyString(), anyString(), eq(3000), eq(30000)))
                .thenThrow(new IllegalArgumentException("private DNS answer"));

        BizException error = assertThrows(BizException.class,
                () -> new ProfileImportConnectionTesterImpl(mapper, transport)
                        .test(config(), "sk-memory"));

        assertEquals("DeepSeek 连接测试失败", error.getMessage());
    }

    private AiProfileImportConfig config() {
        AiProfileImportConfig config = new AiProfileImportConfig();
        config.setEndpoint("https://api.deepseek.com/chat/completions");
        config.setModelName("deepseek-chat");
        config.setConnectTimeoutMs(3000);
        config.setReadTimeoutMs(30000);
        config.setMaxOutputTokens(8000);
        return config;
    }
}
