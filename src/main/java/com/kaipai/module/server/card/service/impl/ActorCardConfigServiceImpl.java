package com.kaipai.module.server.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.card.dto.ActorCardConfigRespDTO;
import com.kaipai.module.model.card.dto.ActorCardConfigSaveDTO;
import com.kaipai.module.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.module.model.card.entity.ActorCardConfig;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.card.mapper.ActorCardConfigMapper;
import com.kaipai.module.server.card.service.ActorCardConfigService;
import com.kaipai.module.server.card.service.CardSceneTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActorCardConfigServiceImpl extends ServiceImpl<ActorCardConfigMapper, ActorCardConfig> implements ActorCardConfigService {

    private final CardSceneTemplateService templateService;
    private final ActorProfileMapper actorProfileMapper;
    private final ObjectMapper objectMapper;

    @Override
    public ActorCardConfigRespDTO actorConfig(Long actorId, String sceneKey) {
        String normalizedSceneKey = normalizeSceneKey(sceneKey);
        ActorCardConfig config = getOne(new LambdaQueryWrapper<ActorCardConfig>()
                .eq(ActorCardConfig::getUserId, actorId)
                .eq(ActorCardConfig::getSceneKey, normalizedSceneKey)
                .orderByDesc(ActorCardConfig::getLastUpdate)
                .orderByDesc(ActorCardConfig::getConfigId)
                .last("limit 1"), false);
        return config == null
                ? buildDefaultConfig(actorId, normalizedSceneKey)
                : toResp(config);
    }

    @Override
    public ActorCardConfigRespDTO saveActorConfig(Long currentUserId, ActorCardConfigSaveDTO dto) {
        if (dto.getActorId() == null || !dto.getActorId().equals(currentUserId)) {
            throw new BizException("只能保存自己的名片配置");
        }

        String normalizedSceneKey = normalizeSceneKey(dto.getSceneKey());
        ActorCardConfig config = getOne(new LambdaQueryWrapper<ActorCardConfig>()
                .eq(ActorCardConfig::getUserId, currentUserId)
                .eq(ActorCardConfig::getSceneKey, normalizedSceneKey)
                .orderByDesc(ActorCardConfig::getLastUpdate)
                .orderByDesc(ActorCardConfig::getConfigId)
                .last("limit 1"), false);
        if (config == null) {
            config = new ActorCardConfig();
            config.setUserId(currentUserId);
        }

        ActorProfile profile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, currentUserId)
                .last("limit 1"));
        config.setActorProfileId(profile == null ? null : profile.getActorProfileId());
        config.setSceneKey(normalizedSceneKey);
        config.setLayoutVariant(StringUtils.hasText(dto.getLayoutVariant()) ? dto.getLayoutVariant() : buildDefaultConfig(currentUserId, normalizedSceneKey).getLayoutVariant());
        config.setPrimaryColor(trimToNull(dto.getPrimaryColor()));
        config.setAccentColor(trimToNull(dto.getAccentColor()));
        config.setBackgroundColor(trimToNull(dto.getBackgroundColor()));
        config.setHighlightedExperienceIds(writeJson(dto.getHighlightedExperiences()));
        config.setHighlightedPhotoUrls(writeJson(dto.getHighlightedPhotos()));
        config.setTagOrderJson(writeJson(dto.getTagOrder()));

        if (config.getConfigId() == null) {
            save(config);
        } else {
            updateById(config);
        }
        return toResp(config);
    }

    private ActorCardConfigRespDTO toResp(ActorCardConfig config) {
        ActorCardConfigRespDTO dto = new ActorCardConfigRespDTO();
        dto.setActorId(config.getUserId());
        dto.setSceneKey(normalizeSceneKey(config.getSceneKey()));
        dto.setLayoutVariant(config.getLayoutVariant());
        dto.setPrimaryColor(config.getPrimaryColor());
        dto.setAccentColor(config.getAccentColor());
        dto.setBackgroundColor(config.getBackgroundColor());
        dto.setHighlightedExperiences(readLongList(config.getHighlightedExperienceIds()));
        dto.setHighlightedPhotos(readStringList(config.getHighlightedPhotoUrls()));
        dto.setTagOrder(readStringList(config.getTagOrderJson()));
        return mergeWithDefault(dto);
    }

    private ActorCardConfigRespDTO buildDefaultConfig(Long actorId, String sceneKey) {
        ActorSceneTemplateRespDTO template = templateService.actorSceneTemplates().stream()
                .filter(item -> normalizeSceneKey(item.getSceneKey()).equals(sceneKey))
                .findFirst()
                .orElseGet(() -> templateService.actorSceneTemplates().stream().findFirst().orElse(null));

        ActorCardConfigRespDTO dto = new ActorCardConfigRespDTO();
        dto.setActorId(actorId);
        dto.setSceneKey(sceneKey);
        dto.setLayoutVariant(template == null ? "compact" : template.getLayoutVariant());
        dto.setPrimaryColor(template == null || template.getThemeColors() == null ? "#ff7a45" : template.getThemeColors().getPrimary());
        dto.setAccentColor(template == null || template.getThemeColors() == null ? "#ffb178" : template.getThemeColors().getAccent());
        dto.setBackgroundColor(template == null || template.getThemeColors() == null ? "#fff7f0" : template.getThemeColors().getBackground());
        dto.setHighlightedExperiences(Collections.emptyList());
        dto.setHighlightedPhotos(Collections.emptyList());
        dto.setTagOrder(Collections.emptyList());
        return dto;
    }

    private ActorCardConfigRespDTO mergeWithDefault(ActorCardConfigRespDTO dto) {
        ActorCardConfigRespDTO defaults = buildDefaultConfig(dto.getActorId(), dto.getSceneKey());
        if (!StringUtils.hasText(dto.getLayoutVariant())) {
            dto.setLayoutVariant(defaults.getLayoutVariant());
        }
        if (!StringUtils.hasText(dto.getPrimaryColor())) {
            dto.setPrimaryColor(defaults.getPrimaryColor());
        }
        if (!StringUtils.hasText(dto.getAccentColor())) {
            dto.setAccentColor(defaults.getAccentColor());
        }
        if (!StringUtils.hasText(dto.getBackgroundColor())) {
            dto.setBackgroundColor(defaults.getBackgroundColor());
        }
        if (dto.getHighlightedExperiences() == null) {
            dto.setHighlightedExperiences(defaults.getHighlightedExperiences());
        }
        if (dto.getHighlightedPhotos() == null) {
            dto.setHighlightedPhotos(defaults.getHighlightedPhotos());
        }
        if (dto.getTagOrder() == null) {
            dto.setTagOrder(defaults.getTagOrder());
        }
        return dto;
    }

    private String normalizeSceneKey(String sceneKey) {
        return StringUtils.hasText(sceneKey) ? sceneKey.trim() : "general";
    }

    private List<Long> readLongList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Long>>() {});
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

    private List<String> readStringList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (Exception error) {
            throw new IllegalStateException("序列化名片配置失败", error);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
