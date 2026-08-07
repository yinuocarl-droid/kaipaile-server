package com.kaipai.model.ai.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProfileImportCapabilityRespDTOTest {

    @Test
    void publicContractContainsOnlyTheSixGovernedCapabilityFields() {
        Set<String> fields = Arrays.stream(ProfileImportCapabilityRespDTO.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());
        var serialized = new ObjectMapper().valueToTree(new ProfileImportCapabilityRespDTO(
                true, false, "deepseek", "deepseek-chat", 20000, "暂不可用"));

        assertEquals(Set.of(
                "enabled", "available", "providerCode", "modelName",
                "maxInputLength", "unavailableReason"), fields);
        assertFalse(serialized.has("reason"));
    }
}
