package com.kaipai.model.actor.card.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class ActorCardMediaBindingItemRespDTO {

    private Long itemId;
    private Long assetId;
    private String legacyUrl;
    private String originalName;
    private String mediaType;
    private String categoryCode;
    private String processStatus;
    private Integer sortOrder;
    private String previewUrl;
    private Instant expiresAt;
}
