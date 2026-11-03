package com.kaipai.model.actor.card.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** Immutable-version reference selected for one actor card until an explicit change or upgrade. */
@Data
public class ActorCardDecorationAssignment {
    private Long cardId;
    private String generationMode;
    private Long backgroundId;
    private Long templateId;
    private Long versionId;
    private Integer templateVersion;
    private Long revision;
    private Long assignedBy;
    private LocalDateTime updatedAt;
}
