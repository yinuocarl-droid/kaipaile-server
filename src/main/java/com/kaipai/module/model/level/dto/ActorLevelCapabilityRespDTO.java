package com.kaipai.module.model.level.dto;

import lombok.Data;

@Data
public class ActorLevelCapabilityRespDTO {

    private Integer maxScenes;

    private Boolean canCustomColor;

    private Boolean canCustomLayout;

    private Integer aiQuotaPerMonth;

    private Boolean paidSkinFreePreview;
}
