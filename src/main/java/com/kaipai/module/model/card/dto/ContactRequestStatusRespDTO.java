package com.kaipai.module.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContactRequestStatusRespDTO {

    private Long requestId;

    private Long holderUserId;

    private Long viewerUserId;

    private Long shareCardId;

    private String templateSceneCode;

    private String templateName;

    private String status;

    private Boolean hasContactPhone;

    private Boolean canViewPhone;

    private String contactPhone;

    private LocalDateTime requestedAt;

    private LocalDateTime decidedAt;
}



