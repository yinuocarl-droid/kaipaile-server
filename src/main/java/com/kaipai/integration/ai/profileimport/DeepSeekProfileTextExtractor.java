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
    private static final String SYSTEM_PROMPT = """
            你是演员职业资料结构化提取器。只输出合法 JSON 对象，不输出 Markdown 或解释。
            顶层必须包含 profileCandidates、workCandidates、ignoredMediaPlaceholderCount、unmappedSegments、warnings。
            profileCandidates 的 fieldKey 只允许：public_name, gender, age, height, current_city, weight,
            origin_place, school_name, major_name, language_tags, specialty_tags, role_type_tags,
            professional_ability_tags, intro, birth_year, birth_month, birth_day, birth_precision。
            每个档案候选必须包含 candidateId、fieldKey、candidateValue、confidence(0到1)、sourceText、
            sourceType、warning。sourceText 必须逐字来自用户输入，不得改写证据。
            workCandidates 每项必须包含 candidateId、projectName 和 fields。可选扁平字段只允许：roleName,
            publishStatus, workTypeCode, roleLevelCode, shootYear, shootMonth, platform, syncSoundStatus,
            collaborators, achievementText, description。每个非空扁平字段都必须在 fields 中提供
            candidateValue、confidence、sourceText、sourceType、warning，candidateValue 必须与扁平值一致。
            不得补造时间、状态、类型、榜单、热度、播放量、合作演员或数字；原文未给出则返回 null。
            籍贯只能写 origin_place，绝不能写 current_city。2004.9 必须拆为 birth_year=2004、
            birth_month=9、birth_day 不生成、birth_precision=month，不得伪造某月1日。
            只有至少两部不同作品提供一致女性角色证据且没有男性角色反向证据时，才允许生成
            gender=female，并必须标记 sourceType=inferred_from_roles、warning=根据多条作品角色推断，请确认。
            不得依据姓名、头像、院校或专业推断性别。
            [图片]、[视频] 仅计入 ignoredMediaPlaceholderCount，不得创建素材、媒体 URL 或作品。
            sourceType 只允许 explicit、direct、derived_from_birth、inferred_from_roles。
            publishStatus 只允许 aired、upcoming、stage、horizontal、other 或 null。
            workTypeCode 只允许 short_drama、horizontal_short_drama、stage_play、musical、tv_column_drama、
            film_tv、micro_film、horizontal、stage、other 或 null。
            syncSoundStatus 只允许 sync、dubbed、unknown 或 null。
            """;

    private final ProfileImportHttpTransport transport;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeepSeekProfileTextExtractor(ProfileImportHttpTransport transport) {
        this.transport = transport;
    }

    public JsonNode extract(AiProfileImportConfig config, String apiKey, String rawText, String requestId) {
        String response;
        try {
            response = post(config, apiKey, payload(rawText, config, false));
            requireBoundedResponse(response, config);
        } catch (BizException error) {
            throw error;
        } catch (ProfileImportHttpTransport.Timeout error) {
            throw new BizException(46006, "智能导入模型响应超时");
        } catch (Exception error) {
            throw new BizException(46002, "DeepSeek 服务不可用");
        }
        try {
            return parse(response);
        } catch (Exception firstError) {
            String repaired;
            try {
                repaired = post(config, apiKey, payload(response, config, true));
                requireBoundedResponse(repaired, config);
            } catch (BizException error) {
                throw error;
            } catch (ProfileImportHttpTransport.Timeout error) {
                throw new BizException(46006, "智能导入模型响应超时");
            } catch (Exception error) {
                throw new BizException(46002, "DeepSeek 服务不可用");
            }
            try {
                return parse(repaired);
            } catch (Exception invalidRepair) {
                throw new BizException(46007, "智能导入结果无法解析");
            }
        }
    }

    private String post(AiProfileImportConfig config, String apiKey, String payload) {
        return transport.post(
                config.getEndpoint(), apiKey, payload,
                positive(config.getConnectTimeoutMs(), 5000),
                positive(config.getReadTimeoutMs(), 60000));
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
        return firstLine >= 0 && closing > firstLine
                ? trimmed.substring(firstLine + 1, closing).trim()
                : trimmed;
    }

    private String payload(String text, AiProfileImportConfig config, boolean repair) {
        try {
            String userContent = repair
                    ? "仅修复以下内容为符合系统合同的合法 JSON，不改变任何事实：\n" + text
                    : text;
            return mapper.writeValueAsString(Map.of(
                    "model", config.getModelName(),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userContent)),
                    "response_format", Map.of("type", "json_object"),
                    "max_tokens", positive(config.getMaxOutputTokens(), 8000),
                    "temperature", 0));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private void requireBoundedResponse(String response, AiProfileImportConfig config) {
        int maxChars = Math.min(1_000_000, Math.max(16_384, positive(config.getMaxOutputTokens(), 8000) * 16));
        if (response == null || response.length() > maxChars) {
            throw new BizException(46007, "智能导入结果无法解析");
        }
    }
}
