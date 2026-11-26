package com.kaipai.model.actor.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 00-265 后台演员卡治理列表项；完成度与封面与名片夹列表同源。 */
@Data
public class AdminActorCardGovernanceItemDTO {
    private Long cardId;
    private Long userId;
    private String ownerName;
    private String ownerPhone;
    private String status;
    private String title;
    private String style;
    private Integer currentStep;
    private Integer completionPercentage;
    private String coverImageUrl;
    private Integer publishedVersion;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime lastUpdate;
}
