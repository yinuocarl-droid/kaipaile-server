package com.kaipai.module.model.card.dto;

import lombok.Data;

@Data
public class TemplateUpdateDTO {

    private Long templateId;

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



