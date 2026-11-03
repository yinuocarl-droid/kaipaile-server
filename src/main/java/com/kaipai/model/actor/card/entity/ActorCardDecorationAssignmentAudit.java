package com.kaipai.model.actor.card.entity;

import lombok.Data;

/** Required mutation audit for card decoration selection, upgrade and copy. */
@Data
public class ActorCardDecorationAssignmentAudit {
    private Long cardId;
    private Long actorUserId;
    private String operation;
    private String generationMode;
    private Long backgroundId;
    private Long fromVersionId;
    private Long toVersionId;
}
