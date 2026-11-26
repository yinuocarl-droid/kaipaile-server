package com.kaipai.service.ai.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import com.kaipai.service.ai.ProfileImportConnectionTester;
import com.kaipai.service.ai.profileimport.ProfileImportHttpTransport;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileImportConnectionTesterImpl implements ProfileImportConnectionTester {
    private static final String PROBE_SYSTEM_PROMPT =
            "Return only one valid JSON object. Do not return Markdown or explanatory text.";
    private static final String PROBE_USER_PROMPT =
            "Return a JSON object containing a boolean probe field.";

    private final ObjectMapper mapper;
    private final ProfileImportHttpTransport transport;

    @Override
    public void test(AiProfileImportConfig config, String apiKey) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", config.getModelName(),
                    "messages", List.of(
                            Map.of("role", "system", "content", PROBE_SYSTEM_PROMPT),
                            Map.of("role", "user", "content", PROBE_USER_PROMPT)),
                    "response_format", Map.of("type", "json_object"),
                    "max_tokens", 32,
                    "temperature", 0));
            String response = transport.post(
                    config.getEndpoint(), apiKey, body,
                    positive(config.getConnectTimeoutMs(), 5000),
                    positive(config.getReadTimeoutMs(), 60000));
            requireStructuredObject(response);
        } catch (Exception error) {
            throw new BizException("DeepSeek 连接测试失败");
        }
    }

    private void requireStructuredObject(String response) throws Exception {
        JsonNode envelope = parseSingleJsonValue(response);
        if (envelope == null || !envelope.isObject()
                || !envelope.path("choices").isArray()
                || envelope.path("choices").isEmpty()) {
            throw new IllegalArgumentException("invalid provider envelope");
        }
        JsonNode content = envelope.path("choices").path(0).path("message").path("content");
        if (!content.isTextual() || content.textValue().isBlank()) {
            throw new IllegalArgumentException("missing provider content");
        }
        JsonNode structuredContent = parseSingleJsonValue(content.textValue());
        if (structuredContent == null || !structuredContent.isObject()) {
            throw new IllegalArgumentException("provider content is not a JSON object");
        }
    }

    private JsonNode parseSingleJsonValue(String value) throws Exception {
        if (value == null) {
            return null;
        }
        try (JsonParser parser = mapper.getFactory().createParser(value)) {
            JsonNode node = mapper.readTree(parser);
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("provider response has trailing JSON content");
            }
            return node;
        }
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}

