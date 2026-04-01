package com.kaipai.module.model.ai.dto;

import lombok.Data;

@Data
public class ActorAiQuotaRespDTO {

    private Long userId;

    private String quotaType;

    private Integer totalQuota;

    private Integer usedCount;

    private String periodType;

    private String periodStart;
}
