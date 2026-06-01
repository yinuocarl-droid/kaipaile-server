package com.kaipai.model.card.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TemplateCreateDTO {

    @NotBlank
    private String templateCode;

    @NotBlank
    private String templateSceneCode;

    @NotBlank
    private String templateName;

    private String description;

    private String layoutVariant;

    private String tier;

    private Integer requiredLevel;

    private Integer requiredInviteCount;

    private Boolean unlockRequired;

    private String baseThemeJson;

    private String artifactPresetJson;
}



