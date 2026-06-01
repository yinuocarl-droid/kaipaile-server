package com.kaipai.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ThemeTokenItemDTO {

    private Long templateId;
    private String templateCode;
    private String templateSceneCode;
    private String templateName;
    private Integer status;
    private String baseThemeJson;
    private LocalDateTime updateTime;
}



