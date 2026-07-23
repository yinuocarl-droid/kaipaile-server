package com.kaipai.integration.ai.profileimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import com.kaipai.service.ai.profileimport.ProfileImportHttpTransport;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekProfileTextExtractor {
    private final ProfileImportHttpTransport transport;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeepSeekProfileTextExtractor(ProfileImportHttpTransport transport) {
        this.transport = transport;
    }

    public JsonNode extract(AiProfileImportConfig config, String apiKey, String rawText, String requestId) {
        String response;
        try {
            response = transport.post(config.getEndpoint(), apiKey, payload(rawText, config.getModelName()));
        } catch (ProfileImportHttpTransport.Timeout error) {
            throw new BizException(46006, "智能导入模型响应超时");
        } catch (Exception error) {
            throw new BizException(46002, "DeepSeek 服务不可用");
        }
        try {
            return parse(response);
        } catch (Exception firstError) {
            try {
                return parse(transport.post(config.getEndpoint(), apiKey,
                        payload("仅修复为合法 JSON，不改变内容：" + response, config.getModelName())));
            } catch (Exception secondError) {
                throw new BizException(46007, "智能导入结果无法解析");
            }
        }
    }

    private JsonNode parse(String value) throws Exception {
        JsonNode node = mapper.readTree(value);
        if (node != null && node.path("choices").isArray() && !node.path("choices").isEmpty()) {
            String content = node.path("choices").path(0).path("message").path("content").asText();
            node = mapper.readTree(stripCodeFence(content));
        }
        if (node == null || !node.isObject()
                || !node.path("profileCandidates").isArray()
                || !node.path("workCandidates").isArray()) {
            throw new Exception("response does not match the extraction envelope");
        }
        return node;
    }

    private String stripCodeFence(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLine = trimmed.indexOf('\n');
        int closing = trimmed.lastIndexOf("```");
        return firstLine >= 0 && closing > firstLine ? trimmed.substring(firstLine + 1, closing).trim() : trimmed;
    }

    private String payload(String text, String model) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", text)),
                    "response_format", Map.of("type", "json_object")));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
