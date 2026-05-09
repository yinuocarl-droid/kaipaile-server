package com.kaipai.module.server.ai.adapter;

import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.ai.dto.AiResumeErrorCode;
import com.kaipai.module.model.ai.dto.AiResumePolishReqDTO;
import com.kaipai.module.model.ai.dto.AiResumePolishRespDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class RuleBasedResumePatchAdapter {

    private static final int MAX_PATCH_COUNT = 3;
    private static final List<String> BLOCKED_KEYWORDS = List.of("色情", "裸聊", "约炮", "涉政", "政治宣传", "暴力宣扬");

    public AdaptedResult adapt(AiResumePolishReqDTO dto) {
        String instruction = normalizeText(dto.getInstruction());
        if (!StringUtils.hasText(instruction)) {
            throw new BizException(AiResumeErrorCode.CONTEXT_INVALID, "请输入 AI 润色要求");
        }
        ensureNoBlockedContent(instruction);

        AiResumePolishReqDTO.ContextDTO context = dto.getContext() == null ? new AiResumePolishReqDTO.ContextDTO() : dto.getContext();
        List<AiResumePolishReqDTO.EditableFieldDTO> editableFields = context.getEditableFields() == null ? List.of() : context.getEditableFields();
        if (editableFields.isEmpty()) {
            throw new BizException(AiResumeErrorCode.CONTEXT_INVALID, "当前档案没有可润色的文本字段");
        }

        List<AiResumePolishRespDTO.PatchDTO> patches = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        AiResumePolishReqDTO.EditableFieldDTO introField = editableFields.stream()
                .filter(item -> "intro".equals(item.getFieldType()) && StringUtils.hasText(item.getFieldKey()))
                .findFirst()
                .orElse(null);
        if (introField != null) {
            patches.add(buildIntroPatch(context, introField, instruction));
        }

        boolean includeExperience = shouldIncludeExperience(instruction, patches.isEmpty());
        if (includeExperience) {
            for (AiResumePolishReqDTO.EditableFieldDTO field : editableFields) {
                if (patches.size() >= MAX_PATCH_COUNT) {
                    warnings.add("本轮仅返回最相关的 3 条润色建议，其余字段请继续追问。");
                    break;
                }
                if (!"work_experience_description".equals(field.getFieldType()) || !StringUtils.hasText(field.getFieldKey())) {
                    continue;
                }
                patches.add(buildExperiencePatch(field));
            }
        }

        if (patches.isEmpty()) {
            throw new BizException(AiResumeErrorCode.CONTEXT_INVALID, "当前档案没有可生成的 AI patch");
        }

        return new AdaptedResult(buildReply(patches, instruction), patches, warnings);
    }

    private AiResumePolishRespDTO.PatchDTO buildIntroPatch(AiResumePolishReqDTO.ContextDTO context,
                                                           AiResumePolishReqDTO.EditableFieldDTO field,
                                                           String instruction) {
        String current = normalizeText(field.getCurrentValue());
        String polished = StringUtils.hasText(current)
                ? polishExistingIntro(current, context, instruction)
                : buildIntroFromContext(context);
        return buildPatch(field, current, polished, "强化专业表达与项目匹配信息");
    }

    private AiResumePolishRespDTO.PatchDTO buildExperiencePatch(AiResumePolishReqDTO.EditableFieldDTO field) {
        String current = normalizeText(field.getCurrentValue());
        String projectName = defaultText(field.getProjectName(), "项目拍摄");
        String roleName = defaultText(field.getRoleName(), "角色");
        String shootDate = defaultText(field.getShootDate(), "近期");
        String polished = StringUtils.hasText(current)
                ? String.format("%s在%s中担任%s，%s。",
                prefixWithTime(shootDate),
                projectName,
                roleName,
                trimSentenceEnding(current))
                : String.format("%s参与%s拍摄，担任%s。",
                prefixWithTime(shootDate),
                projectName,
                roleName);
        return buildPatch(field, current, normalizeText(polished), "补足经历描述的信息密度与现场协作感");
    }

    private AiResumePolishRespDTO.PatchDTO buildPatch(AiResumePolishReqDTO.EditableFieldDTO field,
                                                      String beforeValue,
                                                      String afterValue,
                                                      String reason) {
        AiResumePolishRespDTO.PatchDTO patch = new AiResumePolishRespDTO.PatchDTO();
        patch.setPatchId("airp_patch_" + UUID.randomUUID().toString().replace("-", ""));
        patch.setFieldType(field.getFieldType());
        patch.setFieldKey(field.getFieldKey());
        patch.setLabel(defaultText(field.getLabel(), field.getFieldType()));
        patch.setTargetId(field.getTargetId());
        patch.setBeforeValue(beforeValue);
        patch.setAfterValue(afterValue);
        patch.setReason(reason);
        patch.setStatus("pending");
        return patch;
    }

    private boolean shouldIncludeExperience(String instruction, boolean introMissing) {
        String lowered = instruction.toLowerCase(Locale.ROOT);
        return introMissing
                || lowered.contains("整体")
                || lowered.contains("全部")
                || lowered.contains("经历")
                || lowered.contains("拍摄")
                || lowered.contains("简历");
    }

    private String buildReply(List<AiResumePolishRespDTO.PatchDTO> patches, String instruction) {
        String fields = patches.stream().map(AiResumePolishRespDTO.PatchDTO::getLabel).distinct().reduce((left, right) -> left + "、" + right).orElse("档案文本");
        if (instruction.contains("专业")) {
            return "我已经按更专业的演员简历口径整理好了这轮建议，主要覆盖：" + fields + "。";
        }
        return "我已经根据你的要求生成了结构化润色建议，当前覆盖：" + fields + "。";
    }

    private String polishExistingIntro(String current, AiResumePolishReqDTO.ContextDTO context, String instruction) {
        String base = trimSentenceEnding(current);
        String skills = context.getSkillTypes() == null || context.getSkillTypes().isEmpty()
                ? "短剧、广告等项目"
                : String.join("、", context.getSkillTypes()) + "项目";
        String city = StringUtils.hasText(context.getCity()) ? context.getCity().trim() + "演员" : "演员";
        if (instruction.contains("简洁") || base.length() > 72) {
            return normalizeText(String.format("%s，擅长%s相关表演与呈现。", city, skills));
        }
        return normalizeText(String.format("%s，%s，擅长%s。", city, base, skills));
    }

    private String buildIntroFromContext(AiResumePolishReqDTO.ContextDTO context) {
        String city = StringUtils.hasText(context.getCity()) ? context.getCity().trim() + "演员" : "演员";
        String skills = context.getSkillTypes() == null || context.getSkillTypes().isEmpty()
                ? "短剧、广告等项目"
                : String.join("、", context.getSkillTypes()) + "项目";
        String languages = context.getLanguages() == null || context.getLanguages().isEmpty()
                ? ""
                : "，可使用" + String.join("、", context.getLanguages()) + "进行沟通";
        return normalizeText(String.format("%s，擅长%s相关表演与呈现%s。", city, skills, languages));
    }

    private String prefixWithTime(String shootDate) {
        return StringUtils.hasText(shootDate) ? shootDate.trim() + "期间" : "近期";
    }

    private String trimSentenceEnding(String value) {
        String normalized = normalizeText(value);
        while (normalized.endsWith("。") || normalized.endsWith("；") || normalized.endsWith("，")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    public String detectBlockedKeyword(String instruction) {
        String lowered = normalizeText(instruction).toLowerCase(Locale.ROOT);
        return BLOCKED_KEYWORDS.stream().filter(lowered::contains).findFirst().orElse(null);
    }

    private void ensureNoBlockedContent(String instruction) {
        if (StringUtils.hasText(detectBlockedKeyword(instruction))) {
            throw new BizException(AiResumeErrorCode.CONTENT_BLOCKED, "命中敏感内容，未生成 patch");
        }
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    public record AdaptedResult(String reply, List<AiResumePolishRespDTO.PatchDTO> patches, List<String> warnings) {
    }
}
