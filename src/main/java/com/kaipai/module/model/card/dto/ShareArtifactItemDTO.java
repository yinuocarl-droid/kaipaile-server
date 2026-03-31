package com.kaipai.module.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShareArtifactItemDTO {

    private Long templateId;
    private String templateCode;
    private String sceneKey;
    private String templateName;
    private Integer status;
    private String artifactPresetJson;
    private LocalDateTime updateTime;
}
