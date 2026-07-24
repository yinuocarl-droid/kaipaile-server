package com.kaipai.model.actor.dto;

import lombok.Data;

@Data
public class ActorWorkAssetRespDTO {

    private Long assetId;
    private String usageCode;
    private Integer sortNo;
    private String mediaType;
    private String categoryCode;
    private String originalName;
    private String processStatus;
}
