package com.kaipai.model.actor.card.dto;

import lombok.Data;

@Data
public class ActorCardExpandImageRespDTO {
    private String taskId;
    /** pending | running | done | failed */
    private String status;
    private String originalUrl;
    private String expandedUrl;
    private String failureReason;
}
