package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TemplateCreateDTO {

    @NotBlank
    private String templateCode;

    @NotBlank
    private String sceneKey;

    @NotBlank
    private String templateName;

    private String description;

    private String layoutVariant;

    private String tier;

    private Integer requiredLevel;

    private Boolean membershipRequired;

    private String baseThemeJson;

    private String artifactPresetJson;
}
