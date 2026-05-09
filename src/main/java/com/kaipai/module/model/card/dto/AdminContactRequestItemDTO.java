package com.kaipai.module.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminContactRequestItemDTO {

    private Long requestId;
    private Long shareCardId;
    private String templateSceneCode;
    private String templateName;
    private String status;
    private Long holderUserId;
    private String ownerName;
    private String ownerPhone;
    private Long viewerUserId;
    private String viewerName;
    private String viewerPhone;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
}



