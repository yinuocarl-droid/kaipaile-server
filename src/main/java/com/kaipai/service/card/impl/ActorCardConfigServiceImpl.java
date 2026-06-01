package com.kaipai.service.card.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.card.dto.ActorCardConfigRespDTO;
import com.kaipai.model.card.dto.ActorCardConfigSaveDTO;
import com.kaipai.model.card.dto.ActorMyShareCardItemDTO;
import com.kaipai.model.card.dto.ActorMyShareCardsRespDTO;
import com.kaipai.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.model.card.entity.ActorCardConfig;
import com.kaipai.model.card.entity.ActorSharePreference;
import com.kaipai.model.card.entity.UserShareCard;
import com.kaipai.mapper.card.ActorCardConfigMapper;
import com.kaipai.service.card.ActorCardConfigService;
import com.kaipai.service.card.ActorSharePreferenceService;
import com.kaipai.service.card.CardSceneTemplateService;
import com.kaipai.service.card.UserShareCardService;
import com.kaipai.service.card.support.CurrentPhaseShareArtifactSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActorCardConfigServiceImpl extends ServiceImpl<ActorCardConfigMapper, ActorCardConfig> implements ActorCardConfigService {

    private final CardSceneTemplateService templateService;
    private final ActorSharePreferenceService actorSharePreferenceService;
    private final UserShareCardService userShareCardService;
    private final ObjectMapper objectMapper;

    @Override
    public ActorCardConfigRespDTO actorConfig(Long shareCardId) {
        UserShareCard shareCard = requireActiveShareCard(shareCardId);
        ActorSceneTemplateRespDTO template = requireTemplate(shareCard);
        ActorCardConfig config = requireConfig(shareCard);
        return toResp(config, shareCard, template);
    }

    @Override
    public ActorMyShareCardsRespDTO myCards(Long profileUserId) {
        List<ActorSceneTemplateRespDTO> templates = templateService.actorSceneTemplates();
        Map<Long, Integer> templateOrder = new LinkedHashMap<>();
        for (int i = 0; i < templates.size(); i++) {
            templateOrder.put(templates.get(i).getTemplateId(), i);
        }

        List<ActorMyShareCardItemDTO> cards = userShareCardService.listOwnedCards(profileUserId).stream()
                .sorted((left, right) -> Integer.compare(
                        templateOrder.getOrDefault(left.getTemplateId(), Integer.MAX_VALUE),
                        templateOrder.getOrDefault(right.getTemplateId(), Integer.MAX_VALUE)))
                .map(this::toMyCardItem)
                .collect(Collectors.toList());

        ActorMyShareCardsRespDTO response = new ActorMyShareCardsRespDTO();
        response.setCards(cards);
        response.setTemplates(templates);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActorCardConfigRespDTO saveActorConfig(Long currentUserId, ActorCardConfigSaveDTO dto) {
        if (dto.getShareCardId() == null) {
            throw new BizException("shareCardId 不能为空");
        }
        UserShareCard shareCard = requireOwnedActiveShareCard(currentUserId, dto.getShareCardId());
        ActorSceneTemplateRespDTO template = requireTemplate(shareCard);
        ActorCardConfig config = findConfig(shareCard);
        if (config == null) {
            config = new ActorCardConfig();
            config.setShareCardId(shareCard.getShareCardId());
        }

        config.setLayoutVariant(requireText(dto.getLayoutVariant(), "layoutVariant 不能为空"));
        config.setPrimaryColor(requireText(dto.getPrimaryColor(), "primaryColor 不能为空"));
        config.setAccentColor(requireText(dto.getAccentColor(), "accentColor 不能为空"));
        config.setBackgroundColor(requireText(dto.getBackgroundColor(), "backgroundColor 不能为空"));
        config.setHighlightedExperienceIds(writeJson(dto.getHighlightedExperiences()));
        config.setHighlightedPhotoUrls(writeJson(dto.getHighlightedPhotos()));
        config.setTagOrderJson(writeJson(dto.getTagOrder()));

        if (config.getConfigId() == null) {
            save(config);
        } else {
            updateById(config);
        }
        saveSharePreference(shareCard, dto);
        return toResp(config, shareCard, template);
    }

    private ActorCardConfigRespDTO toResp(ActorCardConfig config, UserShareCard shareCard, ActorSceneTemplateRespDTO template) {
        ActorCardConfigRespDTO dto = new ActorCardConfigRespDTO();
        dto.setProfileUserId(shareCard.getUserId());
        dto.setShareCardId(shareCard.getShareCardId());
        dto.setTemplateSceneCode(template.getTemplateSceneCode());
        dto.setLayoutVariant(config.getLayoutVariant());
        dto.setPrimaryColor(config.getPrimaryColor());
        dto.setAccentColor(config.getAccentColor());
        dto.setBackgroundColor(config.getBackgroundColor());
        dto.setHighlightedExperiences(readLongList(config.getHighlightedExperienceIds()));
        dto.setHighlightedPhotos(readStringList(config.getHighlightedPhotoUrls()));
        dto.setTagOrder(readStringList(config.getTagOrderJson()));
        return dto;
    }

    private ActorMyShareCardItemDTO toMyCardItem(UserShareCard card) {
        ActorSceneTemplateRespDTO template = requireTemplate(card);
        ActorCardConfig config = requireConfig(card);
        ActorMyShareCardItemDTO dto = new ActorMyShareCardItemDTO();
        dto.setCardId(card.getShareCardId());
        dto.setConfigId(config.getConfigId());
        dto.setProfileUserId(card.getUserId());
        dto.setTemplateId(card.getTemplateId());
        dto.setTemplateSceneCode(template.getTemplateSceneCode());
        dto.setLayoutVariant(config.getLayoutVariant());
        dto.setPrimaryColor(config.getPrimaryColor());
        dto.setAccentColor(config.getAccentColor());
        dto.setBackgroundColor(config.getBackgroundColor());
        dto.setDefaultCard(Boolean.TRUE.equals(card.getDefaultCard()));
        dto.setCreateTime(card.getCreateTime());
        dto.setUpdateTime(card.getLastUpdate());
        return dto;
    }

    private UserShareCard requireActiveShareCard(Long shareCardId) {
        UserShareCard shareCard = userShareCardService.findActiveCardById(shareCardId);
        if (shareCard == null) {
            throw new BizException("分享卡片不存在");
        }
        return shareCard;
    }

    private UserShareCard requireOwnedActiveShareCard(Long currentUserId, Long shareCardId) {
        UserShareCard shareCard = requireActiveShareCard(shareCardId);
        if (!currentUserId.equals(shareCard.getUserId())) {
            throw new BizException("分享卡片不存在");
        }
        return shareCard;
    }

    private ActorSceneTemplateRespDTO requireTemplate(UserShareCard shareCard) {
        if (shareCard.getTemplateId() == null || shareCard.getTemplateId() <= 0) {
            throw new BizException("分享卡片模板未绑定");
        }
        return templateService.actorSceneTemplates().stream()
                .filter(item -> shareCard.getTemplateId().equals(item.getTemplateId()))
                .findFirst()
                .orElseThrow(() -> new BizException("分享卡片模板不存在或未启用"));
    }

    private ActorCardConfig requireConfig(UserShareCard shareCard) {
        ActorCardConfig config = findConfig(shareCard);
        if (config == null) {
            throw new BizException("分享卡片配置未绑定");
        }
        return config;
    }

    private ActorCardConfig findConfig(UserShareCard shareCard) {
        return getOne(new LambdaQueryWrapper<ActorCardConfig>()
                .eq(ActorCardConfig::getShareCardId, shareCard.getShareCardId())
                .orderByDesc(ActorCardConfig::getLastUpdate)
                .orderByDesc(ActorCardConfig::getConfigId)
                .last("limit 1"), false);
    }

    private void saveSharePreference(UserShareCard shareCard, ActorCardConfigSaveDTO dto) {
        boolean hasExplicitPreference = StringUtils.hasText(dto.getPreferredArtifact());
        if (!hasExplicitPreference) {
            return;
        }

        ActorSharePreference preference = resolveSharePreference(shareCard.getShareCardId());
        if (preference == null) {
            preference = new ActorSharePreference();
        }
        preference.setShareCardId(shareCard.getShareCardId());
        preference.setPreferredArtifact(resolvePreferredArtifact(dto, preference));

        if (preference.getPreferenceId() == null) {
            actorSharePreferenceService.save(preference);
        } else {
            actorSharePreferenceService.updateById(preference);
        }
    }

    private String resolvePreferredArtifact(ActorCardConfigSaveDTO dto, ActorSharePreference existingPreference) {
        if (StringUtils.hasText(dto.getPreferredArtifact())) {
            return CurrentPhaseShareArtifactSupport.requirePreferredArtifact(dto.getPreferredArtifact());
        }
        if (existingPreference != null && StringUtils.hasText(existingPreference.getPreferredArtifact())) {
            return CurrentPhaseShareArtifactSupport.requirePreferredArtifact(existingPreference.getPreferredArtifact());
        }
        throw new BizException("preferredArtifact 缺失");
    }

    private ActorSharePreference resolveSharePreference(Long shareCardId) {
        return actorSharePreferenceService.getOne(new LambdaQueryWrapper<ActorSharePreference>()
                .eq(ActorSharePreference::getShareCardId, shareCardId)
                .orderByDesc(ActorSharePreference::getLastUpdate)
                .orderByDesc(ActorSharePreference::getPreferenceId)
                .last("limit 1"), false);
    }

    private List<Long> readLongList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Long>>() {});
        } catch (Exception error) {
            throw new BizException("名片配置 highlightedExperiences JSON 无效");
        }
    }

    private List<String> readStringList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception error) {
            throw new BizException("名片配置列表 JSON 无效");
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

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(message);
        }
        return normalized;
    }
}



