package com.kaipai.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateItemDTO {

    private Long templateId;
    private String templateCode;
    private String templateSceneCode;
    private String templateName;
    private String tier;
    private Integer requiredLevel;
    private Integer requiredInviteCount;
    private Boolean unlockRequired;
    private Integer status;
    private Integer sortNo;
    private LocalDateTime updateTime;
}



