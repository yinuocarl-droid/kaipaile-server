package com.kaipai.model.actor.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActorCardListItemDTO {
    private Long id;
    private String status;
    private String title;
    private String style;
    private String coverImageUrl;
    private Integer completionPercentage;
    private Integer publishedVersion;
    private LocalDateTime publishedAt;
    private LocalDateTime lastUpdate;
    /** 必填步骤已完成，可进入生成预览；只对草稿为 true。 */
    private Boolean generateReady;
    /** 已有生成预览，可进入预览并发布；只对草稿为 true。 */
    private Boolean publishReady;
}

