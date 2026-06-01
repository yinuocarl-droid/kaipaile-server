package com.kaipai.model.ai.dto;

import jakarta.validation.Valid;
import lombok.Data;

@Data
public class AdminAiImageProviderPublicConfigSaveDTO {

    @Valid
    private AiImageProviderPublicConfigDTO publicConfig;

    private String reason;
}
