package com.kaipai.service.actor.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.card.dto.ActorCardPublicRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardWork;
import com.kaipai.model.actor.entity.ActorMediaAsset;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.service.actor.ActorCardPublicService;
import com.kaipai.service.actor.ActorCardMediaBindingService;
import com.kaipai.service.actor.ActorCardMainVisualResolver;
import com.kaipai.service.actor.ActorMediaAssetService;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;

/**
 * 已发布演员卡公开视图与复制创建（00-215 / 00-218）。
 * 数据现实：v2 向导的照片/视频/剧照存的是本地临时路径（wxfile://），非可公网访问 URL，
 * 公开视图对非 http(s) 资源返回空（观看者无法访问），资源上传链路接通后自然填充。
 */
@Service
@RequiredArgsConstructor
public class ActorCardPublicServiceImpl implements ActorCardPublicService {

    private static final Duration MAIN_VISUAL_READ_TTL = Duration.ofMinutes(10);

    private final ActorCardMapper actorCardMapper;
    private final ActorCardWorkMapper actorCardWorkMapper;
    private final ActorMediaAssetMapper assetMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final ActorCardMediaBindingService mediaBindingService;
    private final ActorMediaAssetService actorMediaAssetService;
    private final AiGeneratedImageStorage generatedImageStorage;
    private final ObjectMapper objectMapper;

    @Override
    public ActorCardPublicRespDTO getPublicView(Long cardId) {
        ActorCard card = actorCardMapper.selectById(cardId);
        if (card == null) {
            throw new BizException(404, "演员卡不存在");
        }
        if (!"published".equals(card.getStatus())) {
            throw new BizException(403, "该演员卡尚未发布");
        }

        return buildView(card);
    }

    @Override
    public ActorCardPublicRespDTO getOwnerPreview(Long userId, Long cardId) {
        ActorCard card = actorCardMapper.selectById(cardId);
        if (card == null || !userId.equals(card.getUserId())) {
            throw new BizException("演员卡不存在");
        }
        return buildView(card);
    }

    private ActorCardPublicRespDTO buildView(ActorCard card) {

        ActorCardPublicRespDTO resp = new ActorCardPublicRespDTO();
        ActorProfile actorProfile = findActorProfile(card.getUserId());
        resp.setId(card.getId());
        resp.setTitle(card.getTitle());
        resp.setStyle(card.getStyle());
        ActorCardMediaBindingRespDTO sourceBinding = mediaBindingService.list(
                card.getUserId(), card.getId(), ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null);
        String mainVisualUrl = ActorCardMainVisualResolver.resolveDisplayUrl(
                card, sourceBinding, this::isHttpUrl,
                actorProfile == null ? null : actorProfile.getCoverUrl(),
                actorProfile == null ? null : actorProfile.getAvatarUrl());
        if (StringUtils.hasText(mainVisualUrl)) {
            try {
                mainVisualUrl = generatedImageStorage.issueReadUrl(mainVisualUrl, MAIN_VISUAL_READ_TTL);
            } catch (RuntimeException ignored) {
                mainVisualUrl = ActorCardMainVisualResolver.resolveSourceDisplayUrl(
                        card, sourceBinding, this::isHttpUrl,
                        actorProfile == null ? null : actorProfile.getCoverUrl(),
                        actorProfile == null ? null : actorProfile.getAvatarUrl());
            }
        }
        // 两个字段只为旧前端兼容保留；单一首图合同要求同次响应严格同值。
        resp.setBackgroundImageUrl(mainVisualUrl);
        resp.setPreviewImageUrl(mainVisualUrl);

        ActorCardPublicRespDTO.SettingsVO settings = parseSettings(card.getSettingsJson());
        resp.setSettings(settings);
        resp.setProfile(buildProfile(
                actorProfile, card.getProfileSnapshotJson(), Boolean.TRUE.equals(settings.getShowContact())));
        resp.setWorks(buildWorks(card));
        List<String> photos = previewUrls(mediaBindingService.list(
                card.getUserId(), card.getId(), ActorCardMediaBindingServiceImpl.LIFE_PHOTO, null));
        if (photos.isEmpty() && actorProfile != null) {
            photos = parseUrlList(actorProfile.getPhotoUrls());
        }
        resp.setPhotos(photos);
        ActorCardMediaBindingRespDTO videoBinding = mediaBindingService.list(
                card.getUserId(), card.getId(), ActorCardMediaBindingServiceImpl.VIDEO_RESUME, null);
        resp.setVideo(buildVideo(firstUsableMediaUrl(
                firstPreviewUrl(videoBinding), actorProfile == null ? null : actorProfile.getVideoUrl()),
                Boolean.TRUE.equals(settings.getShowVideo())));
        resp.setAttachment(buildAttachment(
                card.getUserId(), card.getAttachmentAssetId(), Boolean.TRUE.equals(settings.getShowAttachment())));
        return resp;
    }

    @Override
    public ActorCardRespDTO copy(Long userId, Long cardId) {
        ActorCard src = actorCardMapper.selectById(cardId);
        if (src == null || !userId.equals(src.getUserId())) {
            throw new BizException("演员卡不存在");
        }

        ActorCard copy = new ActorCard();
        copy.setUserId(userId);
        copy.setStatus("draft");
        copy.setTitle(src.getTitle());
        copy.setStyle(src.getStyle());
        copy.setCurrentStep(7);
        copy.setBackgroundImageUrl(src.getBackgroundImageUrl());
        copy.setSourceImageUrl(src.getSourceImageUrl());
        copy.setExpandedImageUrl(src.getExpandedImageUrl());
        copy.setExpandedSourceFingerprint(src.getExpandedSourceFingerprint());
        copy.setProfileSnapshotJson(src.getProfileSnapshotJson());
        copy.setPhotosJson(src.getPhotosJson());
        copy.setVideoUrl(src.getVideoUrl());
        copy.setAttachmentAssetId(src.getAttachmentAssetId());
        copy.setSettingsJson(src.getSettingsJson());
        copy.setPublishedVersion(0);
        actorCardMapper.insert(copy);

        // 复制参演作品子表（生成门禁依赖 actor_card_work 行数）
        List<ActorCardWork> works = actorCardWorkMapper.selectList(new LambdaQueryWrapper<ActorCardWork>()
                .eq(ActorCardWork::getCardId, cardId)
                .orderByAsc(ActorCardWork::getSortOrder)
                .orderByAsc(ActorCardWork::getId));
        Map<Long, Long> workIdMapping = new HashMap<>();
        for (ActorCardWork w : works) {
            ActorCardWork nw = new ActorCardWork();
            nw.setCardId(copy.getId());
            nw.setSourceWorkId(w.getSourceWorkId());
            nw.setWorkTitle(w.getWorkTitle());
            nw.setWorkType(w.getWorkType());
            nw.setRoleName(w.getRoleName());
            nw.setStillsJson(w.getStillsJson());
            nw.setSortOrder(w.getSortOrder());
            actorCardWorkMapper.insert(nw);
            workIdMapping.put(w.getId(), nw.getId());
        }
        mediaBindingService.copyBindings(userId, cardId, copy.getId(), workIdMapping);

        ActorCardRespDTO dto = new ActorCardRespDTO();
        dto.setId(copy.getId());
        dto.setStatus("draft");
        dto.setTitle(copy.getTitle());
        dto.setStyle(copy.getStyle());
        dto.setCurrentStep(7);
        return dto;
    }

    // ── 组装 ───────────────────────────────────────────────────────────────────

    private ActorCardPublicRespDTO.ProfileVO buildProfile(
            ActorProfile profile, String profileSnapshotJson, boolean showContact) {
        ActorCardPublicRespDTO.ProfileVO p = new ActorCardPublicRespDTO.ProfileVO();
        JsonNode snapshot = null;
        try {
            if (StringUtils.hasText(profileSnapshotJson)) {
                snapshot = objectMapper.readTree(profileSnapshotJson);
            }
        } catch (Exception ignored) {
            // 快照非法时继续使用演员档案补齐，不阻塞观看页
        }

        JsonNode profileExtras = parseObject(profile == null ? null : profile.getExtendedField());
        p.setName(firstNonBlank(text(snapshot, "name"), profile == null ? null : profile.getNickName()));
        p.setGender(firstNonBlank(
                text(snapshot, "gender"), profile == null ? null : genderText(profile.getGender())));
        p.setAge(firstInteger(snapshot, "age", profile == null ? null : profile.getAge()));
        p.setHeight(firstNonBlank(text(snapshot, "height"), numberText(profile == null ? null : profile.getHeight())));
        p.setWeight(firstNonBlank(text(snapshot, "weight"), numberText(profile == null ? null : profile.getWeight())));
        p.setCity(firstNonBlank(text(snapshot, "city"), profile == null ? null : profile.getLocationCity()));
        p.setSchool(firstNonBlank(text(snapshot, "school"), profile == null ? null : profile.getSchoolName()));
        p.setBodyType(firstNonBlank(text(snapshot, "bodyType"), text(profileExtras, "bodyType")));
        p.setHairStyle(firstNonBlank(text(snapshot, "hairStyle"), text(profileExtras, "hairStyle")));
        p.setLanguages(firstNonEmptyList(
                stringList(snapshot, "languages"),
                stringList(snapshot, "languageTags"),
                parseStringList(profile == null ? null : profile.getLanguageTagsJson()),
                stringList(profileExtras, "languages")));
        ActorCardPublicRespDTO.SkillGroupsVO skillGroups = new ActorCardPublicRespDTO.SkillGroupsVO();
        skillGroups.setSpecialty(firstNonEmptyList(
                stringList(snapshot, "specialtyTags"),
                stringList(snapshot, "specialties"),
                stringList(snapshot, "specialty_tags"),
                parseStringList(profile == null ? null : profile.getSpecialtyTagsJson())));
        skillGroups.setProfessionalAbility(firstNonEmptyList(
                stringList(snapshot, "professionalAbilityTags"),
                stringList(snapshot, "professionalAbilities"),
                stringList(snapshot, "professional_ability_tags"),
                parseStringList(profile == null ? null : profile.getProfessionalAbilityTagsJson())));
        skillGroups.setRoleType(firstNonEmptyList(
                stringList(snapshot, "roleTypeTags"),
                stringList(snapshot, "roleTypes"),
                stringList(snapshot, "role_type_tags"),
                parseStringList(profile == null ? null : profile.getRoleTypeTagsJson())));
        p.setSkillGroups(skillGroups);
        p.setSkills(distinctValues(
                stringList(snapshot, "skills"),
                stringList(snapshot, "skillTypes"),
                parseStringList(profile == null ? null : profile.getSpecialtyTagsJson()),
                parseStringList(profile == null ? null : profile.getProfessionalAbilityTagsJson()),
                parseStringList(profile == null ? null : profile.getRoleTypeTagsJson()),
                splitCommaSeparated(profile == null ? null : profile.getSkillTag())));
        p.setCertified(profile != null && Boolean.TRUE.equals(profile.getIsCertified()));
        p.setContact(showContact
                ? firstNonBlank(text(snapshot, "contact"), profile == null ? null : profile.getPhone())
                : null);
        p.setIntroduction(firstNonBlank(
                text(snapshot, "introduction"), text(snapshot, "intro"),
                profile == null ? null : profile.getIntro()));
        return p;
    }

    private ActorProfile findActorProfile(Long userId) {
        try {
            return actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                    .eq(ActorProfile::getUserId, userId)
                    .orderByDesc(ActorProfile::getActorProfileId)
                    .last("limit 1"));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<ActorCardPublicRespDTO.WorkVO> buildWorks(ActorCard card) {
        List<ActorCardPublicRespDTO.WorkVO> result = new ArrayList<>();
        List<ActorCardWork> works = actorCardWorkMapper.selectList(new LambdaQueryWrapper<ActorCardWork>()
                .eq(ActorCardWork::getCardId, card.getId())
                .orderByAsc(ActorCardWork::getSortOrder)
                .orderByAsc(ActorCardWork::getId));
        for (ActorCardWork w : works) {
            ActorCardPublicRespDTO.WorkVO vo = new ActorCardPublicRespDTO.WorkVO();
            vo.setId(w.getId());
            vo.setTitle(w.getWorkTitle());
            vo.setRole(w.getRoleName());
            vo.setWorkType(w.getWorkType());
            List<String> stills = previewUrls(mediaBindingService.list(
                    card.getUserId(), card.getId(), ActorCardMediaBindingServiceImpl.WORK_STILL, w.getId()));
            vo.setStills(stills.isEmpty() ? parseUrlList(w.getStillsJson()) : stills);
            result.add(vo);
        }
        return result;
    }

    private ActorCardPublicRespDTO.VideoVO buildVideo(String videoUrl, boolean showVideo) {
        if (!showVideo || !isHttpUrl(videoUrl)) {
            return null;
        }
        ActorCardPublicRespDTO.VideoVO v = new ActorCardPublicRespDTO.VideoVO();
        v.setUrl(videoUrl);
        return v;
    }

    private List<String> previewUrls(ActorCardMediaBindingRespDTO binding) {
        if (binding == null || binding.getItems() == null) {
            return new ArrayList<>();
        }
        return binding.getItems().stream()
                .map(item -> firstNonBlank(item.getPreviewUrl(), item.getLegacyUrl()))
                .filter(this::isHttpUrl)
                .toList();
    }

    private String firstPreviewUrl(ActorCardMediaBindingRespDTO binding) {
        List<String> urls = previewUrls(binding);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private ActorCardPublicRespDTO.AttachmentVO buildAttachment(
            Long userId, Long assetId, boolean showAttachment) {
        if (!showAttachment || assetId == null) {
            return null;
        }
        ActorMediaAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            return null;
        }
        ActorCardPublicRespDTO.AttachmentVO a = new ActorCardPublicRespDTO.AttachmentVO();
        a.setAssetId(assetId);
        a.setFilename(asset.getOriginalName());
        try {
            a.setPageImages(actorMediaAssetService.listPages(userId, assetId).stream()
                    .map(page -> page.getAccessUrl())
                    .filter(StringUtils::hasText)
                    .toList());
        } catch (RuntimeException ignored) {
            a.setPageImages(List.of());
        }
        return a;
    }

    private ActorCardPublicRespDTO.SettingsVO parseSettings(String settingsJson) {
        ActorCardPublicRespDTO.SettingsVO s = new ActorCardPublicRespDTO.SettingsVO();
        if (!StringUtils.hasText(settingsJson)) {
            return s;
        }
        try {
            JsonNode n = objectMapper.readTree(settingsJson);
            if (n.has("showContact")) s.setShowContact(n.get("showContact").asBoolean());
            if (n.has("showVideo")) s.setShowVideo(n.get("showVideo").asBoolean());
            if (n.has("showAttachment")) s.setShowAttachment(n.get("showAttachment").asBoolean());
            if (n.hasNonNull("order") && n.get("order").isArray()) {
                List<String> order = new ArrayList<>();
                for (JsonNode item : n.get("order")) {
                    if (item.isTextual()) order.add(item.asText());
                }
                if (!order.isEmpty()) s.setOrder(order);
            }
        } catch (Exception ignored) {
            // 非法 settingsJson 时用默认值
        }
        return s;
    }

    /** 解析 JSON 字符串数组，仅保留可公网访问的 http(s) URL（本地 wxfile:// 临时路径不返回） */
    private List<String> parseUrlList(String json) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return result;
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            if (n.isArray()) {
                for (JsonNode item : n) {
                    if (item.isTextual() && isHttpUrl(item.asText())) {
                        result.add(item.asText());
                    }
                }
            }
        } catch (Exception ignored) {
            // 忽略非法 JSON
        }
        return result;
    }

    private boolean isHttpUrl(String value) {
        if (!StringUtils.hasText(value)) return false;
        String normalized = value.trim();
        return (normalized.startsWith("http://") || normalized.startsWith("https://"))
                && !normalized.matches("(?i)^https?://tmp(?:[/:]|$).*");
    }

    private String firstUsableMediaUrl(String... values) {
        for (String value : values) {
            if (isHttpUrl(value)) return value.trim();
        }
        return null;
    }

    private JsonNode parseObject(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            JsonNode parsed = objectMapper.readTree(value);
            return parsed.isObject() ? parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> parseStringList(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        try {
            JsonNode parsed = objectMapper.readTree(value);
            if (parsed.isArray()) return stringList(parsed);
        } catch (Exception ignored) {
            // 历史字段可能是逗号分隔，继续走兼容解析
        }
        return splitCommaSeparated(value);
    }

    private List<String> stringList(JsonNode node, String field) {
        return node == null ? List.of() : stringList(node.get(field));
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual() && StringUtils.hasText(item.asText())) values.add(item.asText().trim());
        });
        return values.stream().distinct().toList();
    }

    @SafeVarargs
    private final List<String> firstNonEmptyList(List<String>... candidates) {
        for (List<String> candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) return candidate;
        }
        return List.of();
    }

    @SafeVarargs
    private final List<String> distinctValues(List<String>... candidates) {
        return java.util.Arrays.stream(candidates)
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> splitCommaSeparated(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Integer firstInteger(JsonNode node, String field, Integer fallback) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) return fallback;
        JsonNode value = node.get(field);
        if (value.canConvertToInt()) return value.asInt();
        if (value.isTextual()) {
            try {
                return Integer.valueOf(value.asText().replaceAll("[^0-9-]", ""));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String numberText(Integer value) {
        return value == null || value <= 0 ? null : String.valueOf(value);
    }

    private String genderText(Integer gender) {
        if (gender == null) return null;
        return gender == 1 ? "male" : gender == 2 ? "female" : "unknown";
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isTextual()) return v.asText();
        if (v.isNumber() || v.isBoolean()) return v.asText();
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) return v;
        }
        return null;
    }
}
