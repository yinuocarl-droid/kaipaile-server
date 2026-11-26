package com.kaipai.model.actor.card.dto;

import lombok.Data;

@Data
public class ActorCardExpandImageReqDTO {
    /** 新素材库主视觉 id；存在时优先于旧 URL。 */
    private Long sourceAssetId;

    /** 用户上传的原始首图 URL */
    private String sourceImageUrl;
}
