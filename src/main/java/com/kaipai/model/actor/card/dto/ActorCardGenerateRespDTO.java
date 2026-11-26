package com.kaipai.model.actor.card.dto;

import lombok.Data;

@Data
public class ActorCardGenerateRespDTO {
    private String taskId;
    /** pending | running | done | failed */
    private String status;
    private String previewUrl;
    private String failureReason;
}
