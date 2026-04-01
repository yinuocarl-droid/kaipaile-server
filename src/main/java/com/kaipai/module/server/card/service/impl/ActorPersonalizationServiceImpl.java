package com.kaipai.module.server.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.module.model.card.dto.ActorCardConfigRespDTO;
import com.kaipai.module.model.card.dto.ActorPersonalizationRespDTO;
import com.kaipai.module.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.module.model.card.entity.ActorSharePreference;
import com.kaipai.module.model.fortune.dto.FortuneReportRespDTO;
import com.kaipai.module.model.level.dto.ActorLevelInfoRespDTO;
import com.kaipai.module.model.level.dto.ActorShareCapabilityRespDTO;
import com.kaipai.module.server.card.service.ActorCardConfigService;
import com.kaipai.module.server.card.service.ActorPersonalizationService;
import com.kaipai.module.server.card.service.ActorSharePreferenceService;
import com.kaipai.module.server.card.service.CardSceneTemplateService;
import com.kaipai.module.server.fortune.service.FortuneReportService;
import com.kaipai.module.server.membership.service.MembershipAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActorPersonalizationServiceImpl implements ActorPersonalizationService {

    private static final String DEFAULT_SCENE = "general";
    private static final String DEFAULT_ARTIFACT = "miniProgramCard";

    private final CardSceneTemplateService cardSceneTemplateService;
    private final ActorCardConfigService actorCardConfigService;
    private final ActorSharePreferenceService actorSharePreferenceService;
    private final MembershipAccountService membershipAccountService;
    private final FortuneReportService fortuneReportService;

    @Override
    public ActorPersonalizationRespDTO resolve(Long actorId, String requestedScene, boolean loadFortune, Long currentUserId) {
        List<ActorSceneTemplateRespDTO> templates = cardSceneTemplateService.actorSceneTemplates();
        ActorLevelInfoRespDTO levelInfo = membershipAccountService.actorLevelInfo(actorId);
        int sceneLevel = levelInfo.getLevel() != null && levelInfo.getLevel() > 0 ? levelInfo.getLevel() : 1;
        String sceneKey = resolveSceneKey(requestedScene, templates, sceneLevel);
        ActorSceneTemplateRespDTO template = resolveTemplate(templates, sceneKey, sceneLevel);
        ActorCardConfigRespDTO customConfig = actorCardConfigService.actorConfig(actorId, sceneKey);
        ActorSharePreference preference = actorSharePreferenceService.getOne(new LambdaQueryWrapper<ActorSharePreference>()
                .eq(ActorSharePreference::getUserId, actorId)
                .eq(ActorSharePreference::getSceneKey, sceneKey)
                .orderByDesc(ActorSharePreference::getLastUpdate)
                .orderByDesc(ActorSharePreference::getPreferenceId)
                .last("limit 1"), false);

        boolean exposeFortuneReport = loadFortune && currentUserId != null && currentUserId.equals(actorId);
        boolean canUseFortuneTheme = levelInfo.getShareCapability() != null && Boolean.TRUE.equals(levelInfo.getShareCapability().getCanApplyFortuneTheme());
        FortuneReportRespDTO fortuneReport = null;
        if (canUseFortuneTheme || exposeFortuneReport) {
            try {
                fortuneReport = fortuneReportService.currentReport(actorId);
            } catch (Exception ignored) {
                fortuneReport = null;
            }
        }

        ActorPersonalizationRespDTO.PersonalizationProfile profile = new ActorPersonalizationRespDTO.PersonalizationProfile();
        profile.setActorId(actorId);
        profile.setLevelInfo(levelInfo);
        profile.setMembershipTier(normalizeMembershipTier(levelInfo.getMembershipTier()));
        profile.setSceneKey(sceneKey);
        profile.setTemplate(template);
        profile.setTemplateId(template == null ? sceneKey : template.getSceneKey());
        profile.setCustomConfig(customConfig);
        profile.setFortuneProfile(buildFortuneProfile(fortuneReport, exposeFortuneReport));
        profile.setSharePreferences(buildSharePreferences(preference, canUseFortuneTheme));

        ActorPersonalizationRespDTO response = new ActorPersonalizationRespDTO();
        response.setTemplates(templates);
        response.setProfile(profile);
        response.setTheme(resolveTheme(profile));
        response.setCapability(levelInfo.getShareCapability());
        response.setArtifacts(resolveArtifacts(profile, levelInfo.getShareCapability(), response.getTheme()));
        return response;
    }

    private ActorPersonalizationRespDTO.SharePreferences buildSharePreferences(ActorSharePreference preference, boolean canUseFortuneTheme) {
        ActorPersonalizationRespDTO.SharePreferences sharePreferences = new ActorPersonalizationRespDTO.SharePreferences();
        sharePreferences.setPreferredArtifact(preference == null || !StringUtils.hasText(preference.getPreferredArtifact())
                ? DEFAULT_ARTIFACT
                : preference.getPreferredArtifact().trim());
        sharePreferences.setPreferredTone(preference == null || !StringUtils.hasText(preference.getPreferredTone())
                ? null
                : preference.getPreferredTone().trim());
        boolean enabledByPreference = preference == null || preference.getEnableFortuneTheme() == null || Boolean.TRUE.equals(preference.getEnableFortuneTheme());
        sharePreferences.setEnableFortuneTheme(canUseFortuneTheme && enabledByPreference);
        return sharePreferences;
    }

    private ActorPersonalizationRespDTO.FortuneProfile buildFortuneProfile(FortuneReportRespDTO fortuneReport, boolean exposeReport) {
        ActorPersonalizationRespDTO.FortuneProfile profile = new ActorPersonalizationRespDTO.FortuneProfile();
        profile.setReport(exposeReport ? fortuneReport : null);
        if (fortuneReport == null) {
            return profile;
        }
        profile.setLuckyColor(fortuneReport.getLuckyColor());
        List<String> keywords = new ArrayList<>();
        if (fortuneReport.getZodiacFortune() != null && StringUtils.hasText(fortuneReport.getZodiacFortune().getKeyword())) {
            keywords.add(fortuneReport.getZodiacFortune().getKeyword().trim());
        }
        if (fortuneReport.getConstellationFortune() != null && StringUtils.hasText(fortuneReport.getConstellationFortune().getKeyword())) {
            keywords.add(fortuneReport.getConstellationFortune().getKeyword().trim());
        }
        profile.setKeywords(keywords);
        String toneKeyword = keywords.isEmpty() ? null : keywords.get(0);
        profile.setTone(resolveFortuneTone(toneKeyword));
        profile.setVisualTags(resolveFortuneVisualTags(toneKeyword));
        return profile;
    }

    private ActorPersonalizationRespDTO.ThemeTokenSet resolveTheme(ActorPersonalizationRespDTO.PersonalizationProfile profile) {
        String basePrimary = valueOrDefault(profile.getCustomConfig() == null ? null : profile.getCustomConfig().getPrimaryColor(),
                profile.getTemplate() != null && profile.getTemplate().getThemeColors() != null ? profile.getTemplate().getThemeColors().getPrimary() : "#ff7a45");
        String baseAccent = valueOrDefault(profile.getCustomConfig() == null ? null : profile.getCustomConfig().getAccentColor(),
                profile.getTemplate() != null && profile.getTemplate().getThemeColors() != null ? profile.getTemplate().getThemeColors().getAccent() : "#ffb178");
        String baseBackground = valueOrDefault(profile.getCustomConfig() == null ? null : profile.getCustomConfig().getBackgroundColor(),
                profile.getTemplate() != null && profile.getTemplate().getThemeColors() != null ? profile.getTemplate().getThemeColors().getBackground() : "#fff7f0");
        String baseText = valueOrDefault(profile.getTemplate() != null && profile.getTemplate().getThemeColors() != null ? profile.getTemplate().getThemeColors().getText() : null, "#181b22");
        String heroText = valueOrDefault(profile.getTemplate() != null && profile.getTemplate().getThemeColors() != null ? profile.getTemplate().getThemeColors().getHeroText() : null, "#ffffff");

        String primary = basePrimary;
        String accent = baseAccent;
        String cardPreset = profile.getSceneKey();
        String posterPreset = profile.getSceneKey();
        String luckyColor = clampHexColor(profile.getFortuneProfile().getLuckyColor());
        if (Boolean.TRUE.equals(profile.getSharePreferences().getEnableFortuneTheme()) && luckyColor != null) {
            primary = luckyColor;
            accent = luckyColor + "66";
            cardPreset = profile.getSceneKey() + "-fortune";
            posterPreset = profile.getSceneKey() + "-fortune";
        }

        ActorPersonalizationRespDTO.ThemeTokenSet theme = new ActorPersonalizationRespDTO.ThemeTokenSet();
        theme.setThemeId(profile.getSceneKey() + "-" + profile.getMembershipTier() + "-" + (Boolean.TRUE.equals(profile.getSharePreferences().getEnableFortuneTheme()) ? "fortune" : "base"));
        theme.setPrimary(primary);
        theme.setAccent(accent);
        theme.setBackground(baseBackground);
        theme.setSurface(primary + "12");
        theme.setSurfaceStrong(primary + "20");
        theme.setTextPrimary(baseText);
        theme.setTextSecondary(baseText + "99");
        theme.setHeroText(heroText);
        theme.setButtonStyle(resolveButtonStyle(profile.getMembershipTier()));
        theme.setMood(resolveSceneMood(profile.getSceneKey()));
        theme.setPosterPreset(posterPreset);
        theme.setCardPreset(cardPreset);
        return theme;
    }

    private List<ActorPersonalizationRespDTO.ShareArtifact> resolveArtifacts(ActorPersonalizationRespDTO.PersonalizationProfile profile,
                                                                             ActorShareCapabilityRespDTO capability,
                                                                             ActorPersonalizationRespDTO.ThemeTokenSet theme) {
        List<ActorPersonalizationRespDTO.ShareArtifact> artifacts = new ArrayList<>();
        artifacts.add(resolveArtifact("miniProgramCard", profile, capability, theme));
        artifacts.add(resolveArtifact("poster", profile, capability, theme));
        artifacts.add(resolveArtifact("publicCardPage", profile, capability, theme));
        artifacts.add(resolveArtifact("inviteCard", profile, capability, theme));
        return artifacts;
    }

    private ActorPersonalizationRespDTO.ShareArtifact resolveArtifact(String type,
                                                                      ActorPersonalizationRespDTO.PersonalizationProfile profile,
                                                                      ActorShareCapabilityRespDTO capability,
                                                                      ActorPersonalizationRespDTO.ThemeTokenSet theme) {
        ActorPersonalizationRespDTO.ShareArtifact artifact = new ActorPersonalizationRespDTO.ShareArtifact();
        artifact.setType(type);
        artifact.setLabel(resolveArtifactLabel(type));
        String sceneName = profile.getTemplate() == null || !StringUtils.hasText(profile.getTemplate().getName()) ? "通用" : profile.getTemplate().getName();
        String fortuneKeyword = profile.getFortuneProfile().getKeywords().isEmpty() ? null : profile.getFortuneProfile().getKeywords().get(0);
        artifact.setTitle(resolveArtifactTitle(type, sceneName));
        artifact.setSubtitle(StringUtils.hasText(fortuneKeyword) ? "本期关键词：" + fortuneKeyword : sceneName + "风格分享产物");
        artifact.setPath(resolveArtifactPath(type, profile, theme));
        artifact.setShareImageUrl("");
        artifact.setLockReason(resolveLockReason(type, capability));
        artifact.setLocked(StringUtils.hasText(artifact.getLockReason()));
        artifact.setTheme(theme);
        artifact.setCapability(capability);
        return artifact;
    }

    private String resolveArtifactLabel(String type) {
        return switch (type) {
            case "poster" -> "分享海报";
            case "publicCardPage" -> "公开名片页";
            case "inviteCard" -> "邀请卡片";
            default -> "小程序卡片";
        };
    }

    private String resolveArtifactTitle(String type, String sceneName) {
        return switch (type) {
            case "inviteCard" -> sceneName + "风格邀请卡";
            case "publicCardPage" -> sceneName + "公开名片页";
            case "poster" -> sceneName + "定制海报";
            default -> sceneName + "分享卡片";
        };
    }

    private String resolveArtifactPath(String type,
                                       ActorPersonalizationRespDTO.PersonalizationProfile profile,
                                       ActorPersonalizationRespDTO.ThemeTokenSet theme) {
        return switch (type) {
            case "publicCardPage" -> "/pages/actor-profile/detail?actorId=" + profile.getActorId()
                    + "&scene=" + profile.getSceneKey()
                    + "&themeId=" + encode(theme.getThemeId())
                    + "&shared=1";
            case "inviteCard" -> "/pkg-card/invite/index";
            default -> {
                List<String> params = new ArrayList<>();
                params.add("actorId=" + profile.getActorId());
                params.add("scene=" + profile.getSceneKey());
                params.add("shared=1");
                params.add("artifact=" + type);
                params.add("themeId=" + encode(theme.getThemeId()));
                if (StringUtils.hasText(profile.getSharePreferences().getPreferredTone())) {
                    params.add("tone=" + encode(profile.getSharePreferences().getPreferredTone()));
                }
                yield "/pkg-card/actor-card/index?" + String.join("&", params);
            }
        };
    }

    private String resolveLockReason(String type, ActorShareCapabilityRespDTO capability) {
        if (capability == null) {
            return null;
        }
        return switch (type) {
            case "miniProgramCard" -> Boolean.TRUE.equals(capability.getCanUseCustomMiniProgramCard()) ? null : "会员可定制分享卡片";
            case "poster" -> Boolean.TRUE.equals(capability.getCanUseCustomPoster()) ? null : "会员可生成定制海报";
            case "inviteCard" -> Boolean.TRUE.equals(capability.getCanUseCustomInviteCard()) ? null : "会员可定制邀请卡片";
            default -> null;
        };
    }

    private ActorSceneTemplateRespDTO resolveTemplate(List<ActorSceneTemplateRespDTO> templates, String sceneKey, int level) {
        List<ActorSceneTemplateRespDTO> available = resolveAvailableTemplates(templates, level);
        return available.stream()
                .filter(item -> sceneKey.equals(item.getSceneKey()))
                .findFirst()
                .orElseGet(() -> available.isEmpty() ? null : available.get(0));
    }

    private String resolveSceneKey(String requestedScene, List<ActorSceneTemplateRespDTO> templates, int level) {
        List<ActorSceneTemplateRespDTO> available = resolveAvailableTemplates(templates, level);
        if (available.isEmpty()) {
            return DEFAULT_SCENE;
        }
        if (StringUtils.hasText(requestedScene)) {
            String normalized = requestedScene.trim();
            boolean matched = available.stream().anyMatch(item -> normalized.equals(item.getSceneKey()));
            if (matched) {
                return normalized;
            }
        }
        return available.get(0).getSceneKey();
    }

    private List<ActorSceneTemplateRespDTO> resolveAvailableTemplates(List<ActorSceneTemplateRespDTO> templates, int level) {
        List<ActorSceneTemplateRespDTO> available = new ArrayList<>();
        for (ActorSceneTemplateRespDTO item : templates) {
            int requiredLevel = item.getRequiredLevel() == null ? 1 : item.getRequiredLevel();
            if (requiredLevel <= level) {
                available.add(item);
            }
        }
        if (available.isEmpty() && !templates.isEmpty()) {
            available.add(templates.get(0));
        }
        return available;
    }

    private String resolveFortuneTone(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "calm";
        }
        if (keyword.contains("突破") || keyword.contains("锋")) {
            return "sharp";
        }
        if (keyword.contains("表现") || keyword.contains("明亮")) {
            return "bright";
        }
        if (keyword.contains("柔") || keyword.contains("静")) {
            return "gentle";
        }
        return "calm";
    }

    private List<String> resolveFortuneVisualTags(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return new ArrayList<>();
        }
        if (keyword.contains("突破")) {
            return new ArrayList<>(List.of("高识别", "镜头冲击"));
        }
        if (keyword.contains("表现")) {
            return new ArrayList<>(List.of("明亮", "外放"));
        }
        return new ArrayList<>(List.of("平衡", "稳定"));
    }

    private String resolveButtonStyle(String membershipTier) {
        return switch (membershipTier) {
            case "vip" -> "glass";
            case "member" -> "solid";
            default -> "outline";
        };
    }

    private String resolveSceneMood(String sceneKey) {
        return switch (sceneKey) {
            case "costume" -> "classic";
            case "artistic" -> "cinematic";
            case "commercial" -> "modern";
            default -> "airy";
        };
    }

    private String normalizeMembershipTier(String membershipTier) {
        return "vip".equals(membershipTier) || "member".equals(membershipTier) ? membershipTier : "none";
    }

    private String valueOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String clampHexColor(String color) {
        if (!StringUtils.hasText(color)) {
            return null;
        }
        String value = color.trim();
        return value.matches("^#[0-9a-fA-F]{6}$") ? value : null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
