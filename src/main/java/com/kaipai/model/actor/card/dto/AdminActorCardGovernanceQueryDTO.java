package com.kaipai.model.actor.card.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 00-265 后台演员卡治理列表查询条件。 */
@Data
public class AdminActorCardGovernanceQueryDTO {

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    @Max(100)
    private long pageSize = 20;

    private Long cardId;

    /** 持卡人用户 ID */
    private Long userId;

    /** published | draft；为空表示全部 */
    private String status;

    /** 风格模板场景码（兼容旧值 ancient）；为空表示全部 */
    private String style;
}
