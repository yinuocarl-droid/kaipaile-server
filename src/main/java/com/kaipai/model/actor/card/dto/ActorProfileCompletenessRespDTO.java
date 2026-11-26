package com.kaipai.model.actor.card.dto;

import lombok.Data;

@Data
public class ActorProfileCompletenessRespDTO {
    /** 资料完整度百分比，0-100 */
    private Integer percentage;
    /** 演员卡数量 */
    private Integer cardCount;
    /** 素材数量 */
    private Integer materialCount;
    /** 浏览量 */
    private Integer viewCount;
}
