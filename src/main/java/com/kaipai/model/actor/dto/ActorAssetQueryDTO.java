package com.kaipai.model.actor.dto;

import lombok.Data;

@Data
public class ActorAssetQueryDTO {
    private int page = 1;
    private int size = 10;
    private String mediaType;
    private String categoryCode;
    private String processStatus;
    private String keyword;
}
