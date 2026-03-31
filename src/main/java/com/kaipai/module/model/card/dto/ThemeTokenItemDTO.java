package com.kaipai.module.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ThemeTokenItemDTO {

    private Long templateId;
    private String templateCode;
    private String sceneKey;
    private String templateName;
    private Integer status;
    private String baseThemeJson;
    private LocalDateTime updateTime;
}
