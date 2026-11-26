package com.kaipai.service.ai.profileimport;

import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import java.util.EnumSet;
import java.util.Set;

public enum ProfileImportPromptReasonCode {
    INITIAL_RELEASE,
    QUALITY_ADJUSTMENT,
    CONFIG_ALIGNMENT,
    QUALITY_REGRESSION,
    INCIDENT_ROLLBACK,
    DRAFT_SUPERSEDED,
    DRAFT_INVALID,
    DRAFT_CREATED_CURRENT,
    DRAFT_CREATED_HISTORY,
    DRAFT_UPDATED,
    TEST_EXECUTED;

    private static final Set<ProfileImportPromptReasonCode> PUBLISH =
            EnumSet.of(INITIAL_RELEASE, QUALITY_ADJUSTMENT, CONFIG_ALIGNMENT);
    private static final Set<ProfileImportPromptReasonCode> RESTORE =
            EnumSet.of(QUALITY_REGRESSION, INCIDENT_ROLLBACK);
    private static final Set<ProfileImportPromptReasonCode> ABANDON =
            EnumSet.of(DRAFT_SUPERSEDED, DRAFT_INVALID);

    public static ProfileImportPromptReasonCode requirePublish(String raw) {
        return require(raw, PUBLISH);
    }

    public static ProfileImportPromptReasonCode requireRestore(String raw) {
        return require(raw, RESTORE);
    }

    public static ProfileImportPromptReasonCode requireAbandon(String raw) {
        return require(raw, ABANDON);
    }

    private static ProfileImportPromptReasonCode require(
            String raw, Set<ProfileImportPromptReasonCode> allowed) {
        try {
            ProfileImportPromptReasonCode value = valueOf(raw == null ? "" : raw.trim());
            if (allowed.contains(value)) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // Rejected values must never be echoed in the stable error.
        }
        throw ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_INVALID.toException();
    }
}
