package com.kaipai.module.model.card.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActorSceneTemplateRespDTO {

    private Long templateId;

    private String templateSceneCode;

    private String name;

    private String description;

    private String coverImage;

    private String heroEyebrow;

    private ThemeColors themeColors;

    private String layoutVariant;

    private List<String> contentFocus;

    private String tier;

    private Integer requiredLevel;

    private Integer requiredInviteCount;

    private PageConfig pageConfig = new PageConfig();

    @Data
    public static class ThemeColors {

        private String primary;

        private String accent;

        private String background;

        private String text;

        private String heroText;
    }

    @Data
    public static class PageConfig {

        private String layoutPreset;

        private String surface;

        private String density;

        private String heroStyle;

        private Sections sections = new Sections();

        private Actions actions = new Actions();
    }

    @Data
    public static class Sections {

        private Boolean profile;

        private Boolean stats;

        private Boolean timeline;

        private Boolean contactCta;
    }

    @Data
    public static class Actions {

        private String primary;

        private String secondary;
    }
}



