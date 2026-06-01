package com.kaipai.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContactRequestItemDTO {

    private Long requestId;

    private Long holderUserId;

    private String ownerName;

    private String ownerAvatar;

    private Long viewerUserId;

    private String viewerName;

    private String viewerAvatar;

    private Long shareCardId;

    private String templateSceneCode;

    private String templateName;

    private String status;

    private String contactPhone;

    private LocalDateTime requestedAt;

    private LocalDateTime decidedAt;
}



