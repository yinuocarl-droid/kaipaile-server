package com.kaipai.module.model.card.dto;

import com.kaipai.module.model.actor.dto.ActorProfileDTO;
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

    private ActorProfileDTO actorSnapshot;

    private List<ShareArtifact> artifacts = new ArrayList<>();

    @Data
    public static class PersonalizationProfile {

        private Long profileUserId;

        private Long shareCardId;

        private ActorLevelInfoRespDTO levelInfo;

        private String capabilityTier;

        private String templateSceneCode;

        private ActorSceneTemplateRespDTO template;

        private String templateId;

        private ActorCardConfigRespDTO customConfig;

        private SharePreferences sharePreferences = new SharePreferences();
    }

    @Data
    public static class SharePreferences {

        private String preferredArtifact;

    }

    @Data
    public static class ThemeTokenSet {

        private String tokenKey;

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



