package com.kaipai.model.actor.card.entity;

import lombok.Data;

/** Mutable draft and publication head; immutable snapshots live separately. */
@Data
public class ActorCardDecorationDraft {
    private Long templateId;
    private Long revision;
    private String schemaJson;
    private Integer lastVersionNo;
    private Integer activeVersionNo;
}
