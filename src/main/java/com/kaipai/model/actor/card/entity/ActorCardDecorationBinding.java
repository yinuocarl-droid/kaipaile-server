package com.kaipai.model.actor.card.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** Current platform binding for the default source or a real background id. */
@Data
public class ActorCardDecorationBinding {
    private String sourceType;
    private Long sourceId;
    private Long revision;
    private Long templateId;
    private Long versionId;
    private Integer templateVersion;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
