package com.kaipai.model.actor.card.entity;
import lombok.Data;
/** Editor-only background; never an AI result or a card's locked input snapshot. */
@Data
public class ActorCardDecorationEditorContext {
    private Long templateId;
    private Long revision;
    private Long backgroundId;
}
