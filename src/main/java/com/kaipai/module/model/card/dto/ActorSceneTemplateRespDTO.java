package com.kaipai.module.model.card.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActorSceneTemplateRespDTO {

    private String sceneKey;

    private String name;

    private String description;

    private String coverImage;

    private String heroEyebrow;

    private ThemeColors themeColors;

    private String layoutVariant;

    private List<String> contentFocus;

    private String tier;

    private Integer requiredLevel;

    @Data
    public static class ThemeColors {

        private String primary;

        private String accent;

        private String background;

        private String text;

        private String heroText;
    }
}
