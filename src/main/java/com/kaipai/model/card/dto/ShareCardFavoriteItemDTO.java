package com.kaipai.model.card.dto;

import lombok.Data;

@Data
public class ShareCardFavoriteItemDTO {
    private Long shareCardId;
    private Long ownerUserId;
    private Long profileUserId;
    private String actorName;
    private String actorAvatar;
    private String templateSceneCode;
    private String templateName;
    private String intro;
    private String contactLabel;
}
