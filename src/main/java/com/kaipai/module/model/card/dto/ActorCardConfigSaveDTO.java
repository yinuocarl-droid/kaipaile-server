package com.kaipai.module.model.card.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ActorCardConfigSaveDTO {

    @NotNull(message = "actorId 不能为空")
    private Long actorId;

    @NotNull(message = "sceneKey 不能为空")
    private String sceneKey;

    private String layoutVariant;

    private String primaryColor;

    private String accentColor;

    private String backgroundColor;

    private List<Long> highlightedExperiences;

    private List<String> highlightedPhotos;

    private List<String> tagOrder;
}
