package com.kaipai.model.ai.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class ProfileImportPromptStrictWriteDTO {

    @JsonIgnore
    private final Set<String> unexpectedFields = new LinkedHashSet<>();

    @JsonAnySetter
    public void captureUnexpectedField(String fieldName, JsonNode ignoredValue) {
        unexpectedFields.add(fieldName);
    }

    @JsonIgnore
    public void requireNoUnexpectedFields() {
        if (!unexpectedFields.isEmpty()) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_INVALID.toException();
        }
    }
}
