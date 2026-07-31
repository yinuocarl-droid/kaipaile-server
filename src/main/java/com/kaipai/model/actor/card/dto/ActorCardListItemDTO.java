package com.kaipai.model.actor.card.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
}

