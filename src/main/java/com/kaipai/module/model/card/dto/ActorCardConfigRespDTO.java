package com.kaipai.module.model.card.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActorCardConfigRespDTO {

    private Long actorId;

    private String sceneKey;

    private String layoutVariant;

    private String primaryColor;

    private String accentColor;

    private String backgroundColor;

    private List<Long> highlightedExperiences;

    private List<String> highlightedPhotos;

    private List<String> tagOrder;
}
