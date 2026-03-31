package com.kaipai.module.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateItemDTO {

    private Long templateId;
    private String templateCode;
    private String sceneKey;
    private String templateName;
    private String tier;
    private Integer requiredLevel;
    private Boolean membershipRequired;
    private Integer status;
    private Integer sortNo;
    private LocalDateTime updateTime;
}
