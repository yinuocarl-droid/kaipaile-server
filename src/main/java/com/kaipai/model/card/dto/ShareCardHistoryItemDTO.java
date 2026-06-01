package com.kaipai.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShareCardHistoryItemDTO {

    private Long profileUserId;

    private Long shareCardId;

    private String templateSceneCode;

    private String actorName;

    private String actorAvatar;

    private String templateName;

    private String intro;

    private String contactLabel;

    private LocalDateTime viewedAt;
}



