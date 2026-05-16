package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AdminAiImageProviderSaveDTO {

    private String providerCode;

    private String displayName;

    private Boolean enabled;

    private Integer priority;

    private AiImageProviderPublicConfigDTO publicConfig;

    private Map<String, String> secrets;

    private String reason;
}
