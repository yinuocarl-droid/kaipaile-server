package com.kaipai.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActorMyShareCardItemDTO {

    private Long cardId;

    private Long configId;

    private Long profileUserId;

    private Long templateId;

    private String templateSceneCode;

    private String layoutVariant;

    private String primaryColor;

    private String accentColor;

    private String backgroundColor;

    private Boolean defaultCard;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}



