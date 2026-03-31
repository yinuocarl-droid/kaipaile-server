package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TemplateUpdateDTO {

    @NotNull
    private Long templateId;

    private String templateName;
    private String description;
    private String layoutVariant;
    private String tier;
    private Integer requiredLevel;
    private Boolean membershipRequired;
    private String baseThemeJson;
    private String artifactPresetJson;
    private Integer status;
    private Integer sortNo;
}
