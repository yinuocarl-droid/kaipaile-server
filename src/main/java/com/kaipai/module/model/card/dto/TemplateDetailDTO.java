package com.kaipai.module.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TemplateDetailDTO {

    private Long templateId;
    private String templateCode;
    private String sceneKey;
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
    private String createUserName;
    private LocalDateTime createTime;
    private String updateUserName;
    private LocalDateTime lastUpdate;
    private List<TemplatePublishLogItemDTO> publishLogs;
}
