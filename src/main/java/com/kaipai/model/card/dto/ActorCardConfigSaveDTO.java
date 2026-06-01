package com.kaipai.model.card.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActorCardConfigSaveDTO {

    private Long shareCardId;

    private String layoutVariant;

    private String primaryColor;

    private String accentColor;

    private String backgroundColor;

    private List<Long> highlightedExperiences;

    private List<String> highlightedPhotos;

    private List<String> tagOrder;

    private String preferredArtifact;

}



