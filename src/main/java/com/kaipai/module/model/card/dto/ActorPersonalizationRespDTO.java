package com.kaipai.module.model.card.dto;

import com.kaipai.module.model.fortune.dto.FortuneReportRespDTO;
import com.kaipai.module.model.level.dto.ActorLevelInfoRespDTO;
import com.kaipai.module.model.level.dto.ActorShareCapabilityRespDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActorPersonalizationRespDTO {

    private List<ActorSceneTemplateRespDTO> templates = new ArrayList<>();

    private PersonalizationProfile profile = new PersonalizationProfile();

    private ThemeTokenSet theme = new ThemeTokenSet();

    private ActorShareCapabilityRespDTO capability;

    private List<ShareArtifact> artifacts = new ArrayList<>();

    @Data
    public static class PersonalizationProfile {

        private Long actorId;

        private ActorLevelInfoRespDTO levelInfo;

        private String membershipTier;

        private String sceneKey;

        private ActorSceneTemplateRespDTO template;

        private String templateId;

        private ActorCardConfigRespDTO customConfig;

        private FortuneProfile fortuneProfile = new FortuneProfile();

        private SharePreferences sharePreferences = new SharePreferences();
    }

    @Data
    public static class FortuneProfile {

        private FortuneReportRespDTO report;

        private String luckyColor;

        private List<String> keywords = new ArrayList<>();

        private String tone;

        private List<String> visualTags = new ArrayList<>();
    }

    @Data
    public static class SharePreferences {

        private String preferredArtifact;

        private Boolean enableFortuneTheme;

        private String preferredTone;
    }

    @Data
    public static class ThemeTokenSet {

        private String themeId;

        private String primary;

        private String accent;

        private String background;

        private String surface;

        private String surfaceStrong;

        private String textPrimary;

        private String textSecondary;

        private String heroText;

        private String buttonStyle;

        private String mood;

        private String posterPreset;

        private String cardPreset;
    }

    @Data
    public static class ShareArtifact {

        private String type;

        private String label;

        private String title;

        private String subtitle;

        private String path;

        private String shareImageUrl;

        private boolean locked;

        private String lockReason;

        private ThemeTokenSet theme;

        private ActorShareCapabilityRespDTO capability;
    }
}
