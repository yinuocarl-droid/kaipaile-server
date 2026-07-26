package com.kaipai.service.ai.profileimport;

import com.kaipai.common.exception.BizException;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Validates editable Prompt bodies and their code-owned rendering contract. */
@Component
public final class ProfileImportPromptPolicy {

    private static final int MIN_SYSTEM_BODY_LENGTH = 200;
    private static final int MAX_SYSTEM_BODY_LENGTH = 16000;
    private static final int MIN_REPAIR_BODY_LENGTH = 20;
    private static final int MAX_REPAIR_BODY_LENGTH = 1000;
    private static final int MAX_RENDERED_SYSTEM_LENGTH = 20000;
    private static final List<String> FORBIDDEN_TOKENS =
            List.of("${", "#{", "{{", "}}", "<%", "%>");

    private final ProfileImportPromptContract contract;

    public ProfileImportPromptPolicy(ProfileImportPromptContract contract) {
        this.contract = contract;
    }

    /** Validates scene, template ownership, and code-owned version compatibility. */
    public void validateTemplateAndVersion(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version) {
        if (template == null
                || version == null
                || !StringUtils.hasText(template.getTemplateCode())
                || !isExactSupportedScene(template.getScene())
                || template.getTemplateId() == null
                || !Objects.equals(template.getTemplateId(), version.getTemplateId())
                || !contract.supports(version.getSchemaVersion(), version.getContractVersion())) {
            throw invalid();
        }
    }

    /** Validates both editable bodies without ever including rejected content in the error. */
    public void validateBodies(String systemBody, String repairBody) {
        if (!hasAllowedCharacters(systemBody)
                || !hasAllowedCharacters(repairBody)
                || !hasNormalizedCodePointLengthBetween(
                        systemBody, MIN_SYSTEM_BODY_LENGTH, MAX_SYSTEM_BODY_LENGTH)
                || !hasNormalizedCodePointLengthBetween(
                        repairBody, MIN_REPAIR_BODY_LENGTH, MAX_REPAIR_BODY_LENGTH)
                || containsForbiddenToken(systemBody)
                || containsForbiddenToken(repairBody)) {
            throw invalid();
        }
    }

    /** Validates the upper bound after the immutable System contract has been appended. */
    public void validateRenderedSystem(String renderedSystem) {
        if (!hasAllowedCharacters(renderedSystem)
                || !hasNormalizedCodePointLengthBetween(
                        renderedSystem, 0, MAX_RENDERED_SYSTEM_LENGTH)) {
            throw invalid();
        }
    }

    private boolean isExactSupportedScene(String scene) {
        if (scene == null) return false;
        try {
            return scene.equals(ProfileImportSceneGuard.requireSupported(scene));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean hasNormalizedCodePointLengthBetween(
            String value, int minimum, int maximum) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        int length = normalized.codePointCount(0, normalized.length());
        return length >= minimum && length <= maximum;
    }

    private boolean hasAllowedCharacters(String value) {
        if (value == null) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
                continue;
            }
            if (Character.isLowSurrogate(character)
                    || (Character.isISOControl(character)
                            && character != '\n'
                            && character != '\r'
                            && character != '\t')) {
                return false;
            }
        }
        return true;
    }

    private boolean containsForbiddenToken(String value) {
        return FORBIDDEN_TOKENS.stream().anyMatch(value::contains);
    }

    private BizException invalid() {
        return ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_INVALID.toException();
    }
}
