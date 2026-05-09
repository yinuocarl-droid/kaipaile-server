package com.kaipai.module.server.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.card.dto.ActorCardConfigRespDTO;
import com.kaipai.module.model.card.dto.ActorPersonalizationRespDTO;
import com.kaipai.module.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.module.model.card.entity.ActorSharePreference;
import com.kaipai.module.model.card.entity.UserShareCard;
import com.kaipai.module.model.level.dto.ActorLevelInfoRespDTO;
import com.kaipai.module.model.level.dto.ActorShareCapabilityRespDTO;
import com.kaipai.module.server.card.service.ActorCardConfigService;
import com.kaipai.module.server.card.service.ActorPersonalizationService;
import com.kaipai.module.server.card.service.ActorSharePreferenceService;
import com.kaipai.module.server.card.service.CardSceneTemplateService;
import com.kaipai.module.server.card.service.UserShareCardService;
import com.kaipai.module.server.card.support.BrandCopySupport;
import com.kaipai.module.server.card.support.CurrentPhaseShareArtifactSupport;
import com.kaipai.module.server.actor.service.ActorProfileService;
import com.kaipai.module.server.capability.service.CapabilityAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActorPersonalizationServiceImpl implements ActorPersonalizationService {

    private final CardSceneTemplateService cardSceneTemplateService;
    private final ActorCardConfigService actorCardConfigService;
    private final ActorSharePreferenceService actorSharePreferenceService;
    private final CapabilityAccountService capabilityAccountService;
    private final UserShareCardService userShareCardService;
    private final ActorProfileService actorProfileService;

    @Override
    public ActorPersonalizationRespDTO resolve(Long shareCardId) {
        UserShareCard shareCard = requireShareCard(shareCardId);
        Long resolvedProfileUserId = shareCard.getUserId();
        List<ActorSceneTemplateRespDTO> templates = cardSceneTemplateService.actorSceneTemplates();
        ActorSceneTemplateRespDTO template = requireTemplate(templates, shareCard.getTemplateId());
        ActorLevelInfoRespDTO levelInfo = capabilityAccountService.actorLevelInfo(resolvedProfileUserId);
        String templateSceneCode = requireText(template.getTemplateSceneCode(), "分享卡片模板 templateSceneCode 缺失");
        ActorCardConfigRespDTO customConfig = actorCardConfigService.actorConfig(shareCard.getShareCardId());
        ActorSharePreference preference = resolveSharePreference(shareCard.getShareCardId());

        ActorPersonalizationRespDTO.PersonalizationProfile profile = new ActorPersonalizationRespDTO.PersonalizationProfile();
        profile.setProfileUserId(resolvedProfileUserId);
        profile.setShareCardId(shareCard.getShareCardId());
        profile.setLevelInfo(levelInfo);
        profile.setCapabilityTier(normalizeCapabilityTier(levelInfo.getCapabilityTier()));
        profile.setTemplateSceneCode(templateSceneCode);
        profile.setTemplate(template);
        profile.setTemplateId(template.getTemplateSceneCode());
        profile.setCustomConfig(customConfig);
        profile.setSharePreferences(buildSharePreferences(preference));

        ActorPersonalizationRespDTO response = new ActorPersonalizationRespDTO();
        response.setTemplates(templates);
        response.setProfile(profile);
        response.setTheme(resolveTheme(profile));
        response.setCapability(levelInfo.getShareCapability());
        response.setActorSnapshot(resolveActorSnapshot(resolvedProfileUserId));
        response.setArtifacts(resolveArtifacts(profile, levelInfo.getShareCapability(), response.getTheme()));
        return response;
    }

    private UserShareCard requireShareCard(Long shareCardId) {
        UserShareCard shareCard = userShareCardService.findActiveCardById(shareCardId);
        if (shareCard == null) {
            throw new BizException("分享卡片不存在");
        }
        return shareCard;
    }

    private ActorSceneTemplateRespDTO requireTemplate(List<ActorSceneTemplateRespDTO> templates, Long templateId) {
        if (templateId == null || templateId <= 0) {
            throw new BizException("分享卡片模板未绑定");
        }
        return templates.stream()
                .filter(item -> templateId.equals(item.getTemplateId()))
                .findFirst()
                .orElseThrow(() -> new BizException("分享卡片模板不存在或未启用"));
    }

    private ActorPersonalizationRespDTO.SharePreferences buildSharePreferences(ActorSharePreference preference) {
        if (preference == null) {
            throw new BizException("分享偏好未绑定");
        }
        ActorPersonalizationRespDTO.SharePreferences sharePreferences = new ActorPersonalizationRespDTO.SharePreferences();
        sharePreferences.setPreferredArtifact(CurrentPhaseShareArtifactSupport.requirePreferredArtifact(preference.getPreferredArtifact()));
        return sharePreferences;
    }

    private ActorPersonalizationRespDTO.ThemeTokenSet resolveTheme(ActorPersonalizationRespDTO.PersonalizationProfile profile) {
        if (profile.getTemplate() == null || profile.getTemplate().getThemeColors() == null) {
            throw new BizException("分享卡片模板主题色缺失");
        }
        String basePrimary = requireText(profile.getCustomConfig() == null ? null : profile.getCustomConfig().getPrimaryColor(), "分享卡片 primaryColor 缺失");
        String baseAccent = requireText(profile.getCustomConfig() == null ? null : profile.getCustomConfig().getAccentColor(), "分享卡片 accentColor 缺失");
        String baseBackground = requireText(profile.getCustomConfig() == null ? null : profile.getCustomConfig().getBackgroundColor(), "分享卡片 backgroundColor 缺失");
        String baseText = requireText(profile.getTemplate().getThemeColors().getText(), "模板 text 颜色缺失");
        String heroText = requireText(profile.getTemplate().getThemeColors().getHeroText(), "模板 heroText 颜色缺失");

        String primary = basePrimary;
        String accent = baseAccent;
        String cardPreset = profile.getTemplateSceneCode();
        String posterPreset = profile.getTemplateSceneCode();

        ActorPersonalizationRespDTO.ThemeTokenSet theme = new ActorPersonalizationRespDTO.ThemeTokenSet();
        theme.setTokenKey(profile.getTemplateSceneCode() + "-" + profile.getCapabilityTier() + "-base");
        theme.setPrimary(primary);
        theme.setAccent(accent);
        theme.setBackground(baseBackground);
        theme.setSurface(primary + "12");
        theme.setSurfaceStrong(primary + "20");
        theme.setTextPrimary(baseText);
        theme.setTextSecondary(baseText + "99");
        theme.setHeroText(heroText);
        theme.setButtonStyle(resolveButtonStyle(profile.getCapabilityTier()));
        theme.setMood(resolveSceneMood(profile.getTemplateSceneCode()));
        theme.setPosterPreset(posterPreset);
        theme.setCardPreset(cardPreset);
        return theme;
    }

    private List<ActorPersonalizationRespDTO.ShareArtifact> resolveArtifacts(ActorPersonalizationRespDTO.PersonalizationProfile profile,
                                                                             ActorShareCapabilityRespDTO capability,
                                                                             ActorPersonalizationRespDTO.ThemeTokenSet theme) {
        List<ActorPersonalizationRespDTO.ShareArtifact> artifacts = new ArrayList<>();
        artifacts.add(resolveArtifact(CurrentPhaseShareArtifactSupport.MINI_PROGRAM_CARD, profile, capability, theme));
        artifacts.add(resolveArtifact(CurrentPhaseShareArtifactSupport.POSTER, profile, capability, theme));
        return artifacts;
    }

    private ActorPersonalizationRespDTO.ShareArtifact resolveArtifact(String type,
                                                                      ActorPersonalizationRespDTO.PersonalizationProfile profile,
                                                                      ActorShareCapabilityRespDTO capability,
                                                                      ActorPersonalizationRespDTO.ThemeTokenSet theme) {
        String artifactType = CurrentPhaseShareArtifactSupport.requireArtifactType(type, "artifact");
        ActorPersonalizationRespDTO.ShareArtifact artifact = new ActorPersonalizationRespDTO.ShareArtifact();
        artifact.setType(artifactType);
        artifact.setLabel(resolveArtifactLabel(artifactType));
        String sceneName = requireText(profile.getTemplate() == null ? null : profile.getTemplate().getName(), "分享卡片模板名称缺失");
        artifact.setTitle(resolveArtifactTitle(artifactType, sceneName));
        artifact.setSubtitle(sceneName + "风格分享产物");
        artifact.setPath(resolveArtifactPath(artifactType, profile));
        artifact.setShareImageUrl("");
        artifact.setLockReason(resolveLockReason(artifactType, capability));
        artifact.setLocked(StringUtils.hasText(artifact.getLockReason()));
        artifact.setTheme(theme);
        artifact.setCapability(capability);
        return artifact;
    }

    private String resolveArtifactLabel(String type) {
        return switch (type) {
            case CurrentPhaseShareArtifactSupport.POSTER -> "分享海报";
            case CurrentPhaseShareArtifactSupport.MINI_PROGRAM_CARD -> BrandCopySupport.MINI_PROGRAM_CARD_LABEL;
            default -> throw new BizException("分享产物类型非法");
        };
    }

    private String resolveArtifactTitle(String type, String sceneName) {
        return switch (type) {
            case CurrentPhaseShareArtifactSupport.POSTER -> sceneName + "定制海报";
            case CurrentPhaseShareArtifactSupport.MINI_PROGRAM_CARD -> sceneName + BrandCopySupport.MINI_PROGRAM_CARD_LABEL;
            default -> throw new BizException("分享产物类型非法");
        };
    }

    private String resolveArtifactPath(String type,
                                       ActorPersonalizationRespDTO.PersonalizationProfile profile) {
        List<String> params = new ArrayList<>();
        params.add("shared=1");
        params.add("shareCardId=" + profile.getShareCardId());
        return "/pages/actor-profile/detail?" + String.join("&", params);
    }

    private ActorSharePreference resolveSharePreference(Long shareCardId) {
        return actorSharePreferenceService.getOne(new LambdaQueryWrapper<ActorSharePreference>()
                .eq(ActorSharePreference::getShareCardId, shareCardId)
                .orderByDesc(ActorSharePreference::getLastUpdate)
                .orderByDesc(ActorSharePreference::getPreferenceId)
                .last("limit 1"), false);
    }

    private ActorProfileDTO resolveActorSnapshot(Long profileUserId) {
        return actorProfileService.profile(profileUserId);
    }

    private String resolveLockReason(String type, ActorShareCapabilityRespDTO capability) {
        if (capability == null) {
            return null;
        }
        return switch (type) {
            case CurrentPhaseShareArtifactSupport.MINI_PROGRAM_CARD -> Boolean.TRUE.equals(capability.getCanUseCustomMiniProgramCard()) ? null : "能力可定制分享卡片";
            case CurrentPhaseShareArtifactSupport.POSTER -> Boolean.TRUE.equals(capability.getCanUseCustomPoster()) ? null : "当前海报能力暂不可用";
            default -> throw new BizException("分享产物类型非法");
        };
    }

    private String resolveButtonStyle(String capabilityTier) {
        return switch (capabilityTier) {
            case "pro" -> "glass";
            case "plus" -> "solid";
            default -> "outline";
        };
    }

    private String resolveSceneMood(String templateSceneCode) {
        return switch (templateSceneCode) {
            case "costume" -> "classic";
            case "artistic" -> "cinematic";
            case "commercial" -> "modern";
            default -> "airy";
        };
    }

    private String normalizeCapabilityTier(String capabilityTier) {
        return "pro".equals(capabilityTier) || "plus".equals(capabilityTier) ? capabilityTier : "base";
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }
}



