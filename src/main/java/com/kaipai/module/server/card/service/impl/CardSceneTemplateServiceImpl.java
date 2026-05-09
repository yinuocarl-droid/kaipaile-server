package com.kaipai.module.server.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.module.model.card.dto.TemplateCreateDTO;
import com.kaipai.module.model.card.dto.TemplateDetailDTO;
import com.kaipai.module.model.card.dto.TemplateItemDTO;
import com.kaipai.module.model.card.dto.TemplateListQueryDTO;
import com.kaipai.module.model.card.dto.TemplatePublishDTO;
import com.kaipai.module.model.card.dto.TemplatePublishLogItemDTO;
import com.kaipai.module.model.card.dto.TemplateRollbackDTO;
import com.kaipai.module.model.card.dto.TemplateSortDTO;
import com.kaipai.module.model.card.dto.TemplateStatusChangeDTO;
import com.kaipai.module.model.card.dto.TemplateUpdateDTO;
import com.kaipai.module.model.card.dto.ThemeTokenItemDTO;
import com.kaipai.module.model.card.dto.ThemeTokenQueryDTO;
import com.kaipai.module.model.card.dto.ThemeTokenUpdateDTO;
import com.kaipai.module.model.card.dto.ShareArtifactItemDTO;
import com.kaipai.module.model.card.dto.ShareArtifactQueryDTO;
import com.kaipai.module.model.card.dto.ShareArtifactUpdateDTO;
import com.kaipai.module.model.card.entity.CardSceneTemplate;
import com.kaipai.module.model.card.entity.TemplatePublishLog;
import com.kaipai.module.server.card.mapper.CardSceneTemplateMapper;
import com.kaipai.module.server.card.service.CardSceneTemplateService;
import com.kaipai.module.server.card.service.TemplatePublishLogService;
import com.kaipai.module.server.card.support.CurrentPhaseShareArtifactSupport;
import com.kaipai.module.server.card.support.TemplateSceneCodeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardSceneTemplateServiceImpl extends ServiceImpl<CardSceneTemplateMapper, CardSceneTemplate> implements CardSceneTemplateService {

    private static final int STATUS_ENABLED = 1;
    private static final Set<String> ARTIFACT_PRESET_FIELDS = Set.of(
            "coverImage",
            "heroEyebrow",
            "requiredInviteCount",
            "contentFocus",
            CurrentPhaseShareArtifactSupport.MINI_PROGRAM_CARD,
            CurrentPhaseShareArtifactSupport.POSTER,
            "pageConfig");
    private final TemplatePublishLogService publishLogService;
    private final AdminAuthContext adminAuthContext;
    private final AdminOperationLogger adminOperationLogger;
    private final ObjectMapper objectMapper;

    @Override
    public List<ActorSceneTemplateRespDTO> actorSceneTemplates() {
        List<CardSceneTemplate> templates = list(new LambdaQueryWrapper<CardSceneTemplate>()
                .eq(CardSceneTemplate::getStatus, STATUS_ENABLED)
                .orderByAsc(CardSceneTemplate::getSortNo)
                .orderByDesc(CardSceneTemplate::getLastUpdate));
        return templates.stream().map(this::toActorSceneTemplate).toList();
    }

    @Override
    public String resolveSceneDisplayName(String templateSceneCode) {
        String normalizedTemplateSceneCode = normalizeTemplateSceneCode(templateSceneCode);
        return actorSceneTemplates().stream()
                .filter(item -> normalizeTemplateSceneCode(item.getTemplateSceneCode()).equals(normalizedTemplateSceneCode))
                .map(ActorSceneTemplateRespDTO::getName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseThrow(() -> new BizException("场景模板不存在或未启用"));
    }

    @Override
    public PageResult<TemplateItemDTO> adminTemplateList(TemplateListQueryDTO dto) {
        Page<CardSceneTemplate> page = new Page<>(dto.getPageNo(), dto.getPageSize());
        String templateSceneCode = normalizeOptionalTemplateSceneCode(dto.getTemplateSceneCode());
        LambdaQueryWrapper<CardSceneTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(templateSceneCode), CardSceneTemplate::getTemplateSceneCode, templateSceneCode)
                .eq(dto.getStatus() != null, CardSceneTemplate::getStatus, dto.getStatus())
                .eq(StringUtils.hasText(dto.getTier()), CardSceneTemplate::getTier, dto.getTier())
                .orderByAsc(CardSceneTemplate::getSortNo);
        page(page, wrapper);
        List<TemplateItemDTO> list = page.getRecords().stream().map(template -> {
            TemplateItemDTO dtoItem = new TemplateItemDTO();
            BeanUtils.copyProperties(template, dtoItem);
            dtoItem.setRequiredInviteCount(resolveRequiredInviteCount(template.getArtifactPresetJson(), template.getTemplateSceneCode()));
            dtoItem.setRequiredLevel(resolveRequiredLevel(template.getRequiredLevel(), dtoItem.getRequiredInviteCount()));
            dtoItem.setUpdateTime(template.getLastUpdate());
            return dtoItem;
        }).collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), list);
    }

    @Override
    public TemplateDetailDTO adminTemplateDetail(Long templateId) {
        CardSceneTemplate template = requireTemplate(templateId);
        TemplateDetailDTO dto = new TemplateDetailDTO();
        BeanUtils.copyProperties(template, dto);
        dto.setRequiredInviteCount(resolveRequiredInviteCount(template.getArtifactPresetJson(), template.getTemplateSceneCode()));
        dto.setRequiredLevel(resolveRequiredLevel(template.getRequiredLevel(), dto.getRequiredInviteCount()));
        dto.setPublishLogs(publishLogService.listByTemplateId(templateId));
        return dto;
    }

    @Override
    public void createTemplate(TemplateCreateDTO dto) {
        String templateSceneCode = normalizeTemplateSceneCode(dto.getTemplateSceneCode());
        CardSceneTemplate template = new CardSceneTemplate();
        BeanUtils.copyProperties(dto, template);
        template.setTemplateSceneCode(templateSceneCode);
        template.setRequiredLevel(resolveRequiredLevel(dto.getRequiredLevel(), dto.getRequiredInviteCount()));
        template.setArtifactPresetJson(mergeArtifactPresetJson(null, dto.getArtifactPresetJson(), dto.getRequiredInviteCount(), templateSceneCode));
        template.setStatus(1);
        save(template);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("content")
                .operationCode("create")
                .targetType("card_scene_template")
                .targetId(template.getTemplateId())
                .afterSnapshot(snapshot(template))
                .extraContext(snapshot(template))
                .operationResult(1)
                .build());
    }

    @Override
    public void updateTemplate(TemplateUpdateDTO dto) {
        CardSceneTemplate template = requireTemplate(dto.getTemplateId());
        Map<String, Object> beforeSnapshot = snapshot(template);
        String existingArtifactPresetJson = template.getArtifactPresetJson();
        BeanUtils.copyProperties(dto, template);
        template.setRequiredLevel(resolveRequiredLevel(dto.getRequiredLevel(),
                dto.getRequiredInviteCount() == null
                        ? resolveRequiredInviteCount(existingArtifactPresetJson, template.getTemplateSceneCode())
                        : dto.getRequiredInviteCount()));
        template.setArtifactPresetJson(mergeArtifactPresetJson(
                existingArtifactPresetJson,
                dto.getArtifactPresetJson(),
                dto.getRequiredInviteCount(),
                template.getTemplateSceneCode()));
        template.setLastUpdate(LocalDateTime.now());
        updateById(template);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("content")
                .operationCode("edit")
                .targetType("card_scene_template")
                .targetId(template.getTemplateId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(template))
                .extraContext(snapshot(template))
                .operationResult(1)
                .build());
    }

    @Override
    public void enableTemplate(Long templateId, TemplateStatusChangeDTO dto) {
        changeTemplateStatus(templateId, 1, "enable", dto == null ? null : dto.getReason());
    }

    @Override
    public void disableTemplate(Long templateId, TemplateStatusChangeDTO dto) {
        changeTemplateStatus(templateId, 2, "disable", dto == null ? null : dto.getReason());
    }

    @Override
    public void sortTemplate(Long templateId, TemplateSortDTO dto) {
        CardSceneTemplate template = requireTemplate(templateId);
        Map<String, Object> beforeSnapshot = snapshot(template);
        template.setSortNo(dto.getSortNo());
        template.setLastUpdate(LocalDateTime.now());
        updateById(template);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("content")
                .operationCode("sort")
                .targetType("card_scene_template")
                .targetId(template.getTemplateId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(template))
                .extraContext(sortContext(template))
                .operationResult(1)
                .build());
    }

    @Override
    public void publishTemplate(TemplatePublishDTO dto) {
        CardSceneTemplate template = requireTemplate(dto.getTemplateId());
        Map<String, Object> beforeSnapshot = snapshot(template);
        template.setStatus(1);
        updateById(template);
        TemplatePublishLog log = new TemplatePublishLog();
        log.setTemplateId(template.getTemplateId());
        log.setTargetType("template");
        log.setTargetCode(template.getTemplateCode());
        log.setPublishVersion(dto.getPublishVersion());
        log.setPublishNote(dto.getPublishNote());
        log.setPublishedAt(LocalDateTime.now());
        log.setActionType("publish");
        log.setPublishedBy(adminAuthContext.getCurrentAdminUserId());
        publishLogService.recordPublishLog(log);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("content")
                .operationCode("publish")
                .targetType("card_scene_template")
                .targetId(template.getTemplateId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(template))
                .extraContext(publishContext(template, dto.getPublishVersion(), dto.getPublishNote(), "publish", null))
                .operationResult(1)
                .build());
    }

    @Override
    public void rollbackTemplate(TemplateRollbackDTO dto) {
        CardSceneTemplate template = requireTemplate(dto.getTemplateId());
        Map<String, Object> beforeSnapshot = snapshot(template);
        template.setStatus(2);
        updateById(template);
        TemplatePublishLog log = new TemplatePublishLog();
        log.setTemplateId(template.getTemplateId());
        log.setTargetType("template");
        log.setTargetCode(template.getTemplateCode());
        log.setPublishVersion(dto.getSourceVersion());
        log.setSourceVersion(dto.getSourceVersion());
        log.setTargetVersion(template.getTemplateCode());
        log.setPublishNote(dto.getPublishNote());
        log.setPublishedAt(LocalDateTime.now());
        log.setActionType("rollback");
        log.setPublishedBy(adminAuthContext.getCurrentAdminUserId());
        publishLogService.recordPublishLog(log);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("content")
                .operationCode("rollback")
                .targetType("card_scene_template")
                .targetId(template.getTemplateId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(template))
                .extraContext(publishContext(template, dto.getSourceVersion(), dto.getPublishNote(), "rollback", dto.getSourceVersion()))
                .operationResult(1)
                .build());
    }

    @Override
    public PageResult<ThemeTokenItemDTO> adminThemeTokenList(ThemeTokenQueryDTO dto) {
        Page<CardSceneTemplate> page = new Page<>(dto.getPageNo(), dto.getPageSize());
        String templateSceneCode = normalizeOptionalTemplateSceneCode(dto.getTemplateSceneCode());
        LambdaQueryWrapper<CardSceneTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getTemplateId() != null, CardSceneTemplate::getTemplateId, dto.getTemplateId())
                .eq(StringUtils.hasText(templateSceneCode), CardSceneTemplate::getTemplateSceneCode, templateSceneCode)
                .eq(dto.getStatus() != null, CardSceneTemplate::getStatus, dto.getStatus())
                .eq(StringUtils.hasText(dto.getTemplateCode()), CardSceneTemplate::getTemplateCode, dto.getTemplateCode())
                .orderByAsc(CardSceneTemplate::getSortNo);
        Page<CardSceneTemplate> result = page(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toThemeTokenItem).toList());
    }

    @Override
    public void updateThemeToken(Long templateId, ThemeTokenUpdateDTO dto) {
        CardSceneTemplate template = requireTemplate(templateId);
        Map<String, Object> beforeSnapshot = snapshot(template);
        template.setBaseThemeJson(dto.getBaseThemeJson());
        template.setLastUpdate(LocalDateTime.now());
        updateById(template);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("content")
                .operationCode("theme-edit")
                .targetType("card_scene_template")
                .targetId(templateId)
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(template))
                .extraContext(themeContext(template))
                .operationResult(1)
                .build());
    }

    @Override
    public PageResult<ShareArtifactItemDTO> adminShareArtifactList(ShareArtifactQueryDTO dto) {
        Page<CardSceneTemplate> page = new Page<>(dto.getPageNo(), dto.getPageSize());
        String templateSceneCode = normalizeOptionalTemplateSceneCode(dto.getTemplateSceneCode());
        LambdaQueryWrapper<CardSceneTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getTemplateId() != null, CardSceneTemplate::getTemplateId, dto.getTemplateId())
                .eq(StringUtils.hasText(dto.getTemplateCode()), CardSceneTemplate::getTemplateCode, dto.getTemplateCode())
                .eq(StringUtils.hasText(templateSceneCode), CardSceneTemplate::getTemplateSceneCode, templateSceneCode)
                .eq(dto.getStatus() != null, CardSceneTemplate::getStatus, dto.getStatus())
                .orderByAsc(CardSceneTemplate::getSortNo);
        Page<CardSceneTemplate> result = page(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toShareArtifactItem).toList());
    }

    @Override
    public void updateShareArtifact(Long templateId, ShareArtifactUpdateDTO dto) {
        CardSceneTemplate template = requireTemplate(templateId);
        Map<String, Object> beforeSnapshot = snapshot(template);
        template.setArtifactPresetJson(mergeArtifactPresetJson(
                template.getArtifactPresetJson(),
                dto.getArtifactPresetJson(),
                resolveRequiredInviteCount(template.getArtifactPresetJson(), template.getTemplateSceneCode()),
                template.getTemplateSceneCode()));
        template.setLastUpdate(LocalDateTime.now());
        updateById(template);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("content")
                .operationCode("artifact-edit")
                .targetType("card_scene_template")
                .targetId(templateId)
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(template))
                .extraContext(artifactContext(template))
                .operationResult(1)
                .build());
    }

    private ActorSceneTemplateRespDTO toActorSceneTemplate(CardSceneTemplate template) {
        ActorSceneTemplateRespDTO dto = new ActorSceneTemplateRespDTO();
        dto.setThemeColors(new ActorSceneTemplateRespDTO.ThemeColors());
        dto.setPageConfig(new ActorSceneTemplateRespDTO.PageConfig());
        dto.setTemplateId(template.getTemplateId());
        dto.setTemplateSceneCode(normalizeTemplateSceneCode(template.getTemplateSceneCode()));
        dto.setName(requireText(template.getTemplateName(), "模板名称缺失"));
        dto.setDescription(template.getDescription());
        dto.setLayoutVariant(normalizeRuntimeLayoutVariant(template.getLayoutVariant()));
        dto.setTier(requireText(template.getTier(), "模板 tier 缺失"));
        Integer requiredInviteCount = resolveRequiredInviteCount(template.getArtifactPresetJson(), template.getTemplateSceneCode());
        dto.setRequiredInviteCount(requiredInviteCount);
        dto.setRequiredLevel(resolveRequiredLevel(template.getRequiredLevel(), requiredInviteCount));
        applyThemeOverride(dto.getThemeColors(), template.getBaseThemeJson());
        applyArtifactOverride(dto, template.getArtifactPresetJson());
        return dto;
    }

    private void applyThemeOverride(ActorSceneTemplateRespDTO.ThemeColors themeColors, String baseThemeJson) {
        JsonNode root = requireObjectJson(baseThemeJson, "模板 base_theme_json 缺失或格式错误");
        JsonNode themeNode = root.get("themeColors");
        if (themeNode == null || !themeNode.isObject()) {
            throw new BizException("模板 themeColors 缺失或格式错误");
        }
        themeColors.setPrimary(requireText(textValue(themeNode, "primary", null), "模板 primary 颜色缺失"));
        themeColors.setAccent(requireText(textValue(themeNode, "accent", null), "模板 accent 颜色缺失"));
        themeColors.setBackground(requireText(textValue(themeNode, "background", null), "模板 background 颜色缺失"));
        themeColors.setText(requireText(textValue(themeNode, "text", null), "模板 text 颜色缺失"));
        themeColors.setHeroText(requireText(textValue(themeNode, "heroText", null), "模板 heroText 颜色缺失"));
    }

    private void applyArtifactOverride(ActorSceneTemplateRespDTO dto, String artifactPresetJson) {
        JsonNode root = requireObjectJson(artifactPresetJson, "模板 artifact_preset_json 缺失或格式错误");
        requireCurrentArtifactPreset(root);
        dto.setCoverImage(textValue(root, "coverImage", null));
        dto.setHeroEyebrow(textValue(root, "heroEyebrow", null));
        dto.setRequiredInviteCount(intValue(root, "requiredInviteCount", dto.getRequiredInviteCount()));
        JsonNode focusNode = root.get("contentFocus");
        if (focusNode != null && focusNode.isArray()) {
            dto.setContentFocus(readStringArray(focusNode));
        }
        applyPageConfigOverride(dto, root);
    }

    private void applyPageConfigOverride(ActorSceneTemplateRespDTO dto, JsonNode root) {
        if (dto.getPageConfig() == null) {
            dto.setPageConfig(new ActorSceneTemplateRespDTO.PageConfig());
        }
        ActorSceneTemplateRespDTO.PageConfig pageConfig = dto.getPageConfig();
        JsonNode pageConfigNode = root.get("pageConfig");
        if (pageConfigNode == null || !pageConfigNode.isObject()) {
            throw new BizException("模板 pageConfig 缺失或格式错误");
        }

        pageConfig.setLayoutPreset(requireText(textValue(pageConfigNode, "layoutPreset", null), "模板 pageConfig.layoutPreset 缺失"));
        pageConfig.setSurface(requireText(textValue(pageConfigNode, "surface", null), "模板 pageConfig.surface 缺失"));
        pageConfig.setDensity(requireText(textValue(pageConfigNode, "density", null), "模板 pageConfig.density 缺失"));
        pageConfig.setHeroStyle(requireText(textValue(pageConfigNode, "heroStyle", null), "模板 pageConfig.heroStyle 缺失"));

        if (pageConfig.getSections() == null) {
            pageConfig.setSections(new ActorSceneTemplateRespDTO.Sections());
        }
        JsonNode sectionsNode = pageConfigNode.get("sections");
        if (sectionsNode == null || !sectionsNode.isObject()) {
            throw new BizException("模板 pageConfig.sections 缺失或格式错误");
        }
        pageConfig.getSections().setProfile(requireBooleanValue(sectionsNode, "profile", "模板 pageConfig.sections.profile 缺失"));
        pageConfig.getSections().setStats(requireBooleanValue(sectionsNode, "stats", "模板 pageConfig.sections.stats 缺失"));
        pageConfig.getSections().setTimeline(requireBooleanValue(sectionsNode, "timeline", "模板 pageConfig.sections.timeline 缺失"));
        pageConfig.getSections().setContactCta(requireBooleanValue(sectionsNode, "contactCta", "模板 pageConfig.sections.contactCta 缺失"));

        if (pageConfig.getActions() == null) {
            pageConfig.setActions(new ActorSceneTemplateRespDTO.Actions());
        }
        JsonNode actionsNode = pageConfigNode.get("actions");
        if (actionsNode == null || !actionsNode.isObject()) {
            throw new BizException("模板 pageConfig.actions 缺失或格式错误");
        }
        pageConfig.getActions().setPrimary(requireText(textValue(actionsNode, "primary", null), "模板 pageConfig.actions.primary 缺失"));
        pageConfig.getActions().setSecondary(requireText(textValue(actionsNode, "secondary", null), "模板 pageConfig.actions.secondary 缺失"));
    }

    private JsonNode readJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private JsonNode requireObjectJson(String raw, String message) {
        JsonNode node = readJson(raw);
        if (node == null || !node.isObject()) {
            throw new BizException(message);
        }
        return node;
    }

    private List<String> readStringArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return Collections.emptyList();
        }
        return streamOf(arrayNode)
                .map(JsonNode::asText)
                .filter(StringUtils::hasText)
                .toList();
    }

    private java.util.stream.Stream<JsonNode> streamOf(JsonNode arrayNode) {
        List<JsonNode> items = new java.util.ArrayList<>();
        arrayNode.forEach(items::add);
        return items.stream();
    }

    private String textValue(JsonNode node, String fieldName, String defaultValue) {
        if (node == null || !node.has(fieldName) || !StringUtils.hasText(node.get(fieldName).asText())) {
            return defaultValue;
        }
        return node.get(fieldName).asText();
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private Integer intValue(JsonNode node, String fieldName, Integer defaultValue) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return defaultValue;
        }
        JsonNode field = node.get(fieldName);
        if (field.isInt() || field.isLong()) {
            return field.asInt();
        }
        if (field.isTextual() && StringUtils.hasText(field.asText())) {
            try {
                return Integer.parseInt(field.asText().trim());
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Boolean booleanValue(JsonNode node, String fieldName, Boolean defaultValue) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return defaultValue;
        }
        JsonNode field = node.get(fieldName);
        if (field.isBoolean()) {
            return field.asBoolean();
        }
        if (field.isTextual() && StringUtils.hasText(field.asText())) {
            return Boolean.parseBoolean(field.asText().trim());
        }
        return defaultValue;
    }

    private Boolean requireBooleanValue(JsonNode node, String fieldName, String message) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            throw new BizException(message);
        }
        JsonNode field = node.get(fieldName);
        if (!field.isBoolean()) {
            throw new BizException(message + "或格式错误");
        }
        return field.asBoolean();
    }

    private String normalizeTemplateSceneCode(String templateSceneCode) {
        return TemplateSceneCodeValidator.requireAllowed(templateSceneCode);
    }

    private String normalizeOptionalTemplateSceneCode(String templateSceneCode) {
        return TemplateSceneCodeValidator.normalizeOptional(templateSceneCode);
    }

    private Integer resolveRequiredInviteCount(String artifactPresetJson, String templateSceneCode) {
        JsonNode root = requireObjectJson(artifactPresetJson, "模板 artifact_preset_json 缺失或格式错误");
        requireCurrentArtifactPreset(root);
        return intValue(root, "requiredInviteCount", null);
    }

    private Integer resolveRequiredLevel(Integer requiredLevel, Integer requiredInviteCount) {
        if (requiredLevel != null && requiredLevel > 0) {
            return requiredLevel;
        }
        int inviteCount = requiredInviteCount == null ? -1 : requiredInviteCount;
        if (requiredInviteCount != null) {
            if (inviteCount >= 8) {
                return 5;
            }
            if (inviteCount >= 5) {
                return 4;
            }
            if (inviteCount >= 3) {
                return 3;
            }
            if (inviteCount >= 1) {
                return 2;
            }
            return 1;
        }
        return null;
    }

    private String mergeArtifactPresetJson(String existingJson, String incomingJson, Integer requiredInviteCount, String templateSceneCode) {
        String sourceJson = incomingJson != null ? incomingJson : existingJson;
        ObjectNode root = requireObjectNode(sourceJson, "模板 artifact_preset_json 缺失或格式错误");
        if (requiredInviteCount != null) {
            root.put("requiredInviteCount", Math.max(requiredInviteCount, 0));
        }
        requireCurrentArtifactPreset(root);
        return writeJson(root);
    }

    private void requireCurrentArtifactPreset(JsonNode root) {
        if (!root.isObject()) {
            throw new BizException("模板 artifact_preset_json 必须是对象");
        }
        root.fieldNames().forEachRemaining(fieldName -> {
            if (!ARTIFACT_PRESET_FIELDS.contains(fieldName)) {
                throw new BizException("模板 artifact_preset_json 包含非当前字段: " + fieldName);
            }
        });
        requireArtifactPresetNode(root, CurrentPhaseShareArtifactSupport.MINI_PROGRAM_CARD);
        requireArtifactPresetNode(root, CurrentPhaseShareArtifactSupport.POSTER);
    }

    private void requireArtifactPresetNode(JsonNode root, String fieldName) {
        JsonNode artifactNode = root.get(fieldName);
        if (artifactNode != null && !artifactNode.isObject()) {
            throw new BizException("模板 artifact_preset_json." + fieldName + " 必须是对象");
        }
    }

    private String normalizeRuntimeLayoutVariant(String rawVariant) {
        String normalized = StringUtils.hasText(rawVariant) ? rawVariant.trim() : null;
        if (!StringUtils.hasText(normalized)) {
            throw new BizException("模板 layoutVariant 缺失");
        }
        return switch (normalized) {
            case "magazine", "spacious", "compact" -> normalized;
            default -> throw new BizException("模板 layoutVariant 不合法");
        };
    }

    private ObjectNode requireObjectNode(String raw, String message) {
        JsonNode node = requireObjectJson(raw, message);
        return ((ObjectNode) node).deepCopy();
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new BizException("模板 JSON 序列化失败");
        }
    }

    private CardSceneTemplate requireTemplate(Long templateId) {
        CardSceneTemplate template = getById(templateId);
        if (template == null) {
            throw new BizException("Template not found");
        }
        return template;
    }

    private void changeTemplateStatus(Long templateId, int targetStatus, String operationCode, String reason) {
        CardSceneTemplate template = requireTemplate(templateId);
        if (template.getStatus() != null && template.getStatus() == targetStatus) {
            throw new BizException("Template status already matched");
        }
        Map<String, Object> beforeSnapshot = snapshot(template);
        template.setStatus(targetStatus);
        template.setLastUpdate(LocalDateTime.now());
        updateById(template);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("content")
                .operationCode(operationCode)
                .targetType("card_scene_template")
                .targetId(templateId)
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(template))
                .extraContext(statusContext(template, reason))
                .operationResult(1)
                .build());
    }

    private Map<String, Object> snapshot(CardSceneTemplate template) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("templateId", template.getTemplateId());
        snapshot.put("templateCode", template.getTemplateCode());
        snapshot.put("templateSceneCode", template.getTemplateSceneCode());
        snapshot.put("templateName", template.getTemplateName());
        snapshot.put("layoutVariant", template.getLayoutVariant());
        snapshot.put("tier", template.getTier());
        snapshot.put("requiredLevel", template.getRequiredLevel());
        snapshot.put("requiredInviteCount", resolveRequiredInviteCount(template.getArtifactPresetJson(), template.getTemplateSceneCode()));
        snapshot.put("unlockRequired", template.getUnlockRequired());
        snapshot.put("status", template.getStatus());
        snapshot.put("sortNo", template.getSortNo());
        return snapshot;
    }

    private Map<String, Object> publishContext(CardSceneTemplate template, String publishVersion,
                                               String publishNote, String actionType, String sourceVersion) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("template_id", template.getTemplateId());
        context.put("template_code", template.getTemplateCode());
        context.put("publish_version", publishVersion);
        context.put("publish_note", publishNote);
        context.put("action_type", actionType);
        context.put("source_version", sourceVersion);
        return context;
    }

    private Map<String, Object> statusContext(CardSceneTemplate template, String reason) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("template_id", template.getTemplateId());
        context.put("template_code", template.getTemplateCode());
        context.put("template_status_after", template.getStatus());
        context.put("template_scene_code", template.getTemplateSceneCode());
        context.put("reason", reason);
        return context;
    }

    private Map<String, Object> sortContext(CardSceneTemplate template) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("template_id", template.getTemplateId());
        context.put("template_code", template.getTemplateCode());
        context.put("sort_no", template.getSortNo());
        return context;
    }

    private Map<String, Object> themeContext(CardSceneTemplate template) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("template_id", template.getTemplateId());
        context.put("template_code", template.getTemplateCode());
        context.put("template_scene_code", template.getTemplateSceneCode());
        context.put("base_theme_json", template.getBaseThemeJson());
        return context;
    }

    private Map<String, Object> artifactContext(CardSceneTemplate template) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("template_id", template.getTemplateId());
        context.put("template_code", template.getTemplateCode());
        context.put("template_scene_code", template.getTemplateSceneCode());
        context.put("artifact_preset_json", template.getArtifactPresetJson());
        return context;
    }

    private ThemeTokenItemDTO toThemeTokenItem(CardSceneTemplate template) {
        ThemeTokenItemDTO dto = new ThemeTokenItemDTO();
        dto.setTemplateId(template.getTemplateId());
        dto.setTemplateCode(template.getTemplateCode());
        dto.setTemplateSceneCode(template.getTemplateSceneCode());
        dto.setTemplateName(template.getTemplateName());
        dto.setStatus(template.getStatus());
        dto.setBaseThemeJson(template.getBaseThemeJson());
        dto.setUpdateTime(template.getLastUpdate());
        return dto;
    }

    private ShareArtifactItemDTO toShareArtifactItem(CardSceneTemplate template) {
        ShareArtifactItemDTO dto = new ShareArtifactItemDTO();
        dto.setTemplateId(template.getTemplateId());
        dto.setTemplateCode(template.getTemplateCode());
        dto.setTemplateSceneCode(template.getTemplateSceneCode());
        dto.setTemplateName(template.getTemplateName());
        dto.setStatus(template.getStatus());
        dto.setArtifactPresetJson(template.getArtifactPresetJson());
        dto.setUpdateTime(template.getLastUpdate());
        return dto;
    }
}




