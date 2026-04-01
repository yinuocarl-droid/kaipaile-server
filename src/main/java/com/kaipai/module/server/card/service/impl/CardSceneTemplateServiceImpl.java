package com.kaipai.module.server.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardSceneTemplateServiceImpl extends ServiceImpl<CardSceneTemplateMapper, CardSceneTemplate> implements CardSceneTemplateService {

    private static final int STATUS_ENABLED = 1;

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
        if (templates.isEmpty()) {
            return List.of(
                    buildDefaultTemplate("general"),
                    buildDefaultTemplate("urban"),
                    buildDefaultTemplate("commercial"),
                    buildDefaultTemplate("costume"),
                    buildDefaultTemplate("artistic")
            );
        }
        return templates.stream().map(this::toActorSceneTemplate).toList();
    }

    @Override
    public PageResult<TemplateItemDTO> adminTemplateList(TemplateListQueryDTO dto) {
        Page<CardSceneTemplate> page = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<CardSceneTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(dto.getSceneKey()), CardSceneTemplate::getSceneKey, dto.getSceneKey())
                .eq(dto.getStatus() != null, CardSceneTemplate::getStatus, dto.getStatus())
                .eq(StringUtils.hasText(dto.getTier()), CardSceneTemplate::getTier, dto.getTier())
                .orderByAsc(CardSceneTemplate::getSortNo);
        page(page, wrapper);
        List<TemplateItemDTO> list = page.getRecords().stream().map(template -> {
            TemplateItemDTO dtoItem = new TemplateItemDTO();
            BeanUtils.copyProperties(template, dtoItem);
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
        dto.setPublishLogs(publishLogService.listByTemplateId(templateId));
        return dto;
    }

    @Override
    public void createTemplate(TemplateCreateDTO dto) {
        CardSceneTemplate template = new CardSceneTemplate();
        BeanUtils.copyProperties(dto, template);
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
        BeanUtils.copyProperties(dto, template);
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
        LambdaQueryWrapper<CardSceneTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getTemplateId() != null, CardSceneTemplate::getTemplateId, dto.getTemplateId())
                .eq(StringUtils.hasText(dto.getSceneKey()), CardSceneTemplate::getSceneKey, dto.getSceneKey())
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
        LambdaQueryWrapper<CardSceneTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getTemplateId() != null, CardSceneTemplate::getTemplateId, dto.getTemplateId())
                .eq(StringUtils.hasText(dto.getTemplateCode()), CardSceneTemplate::getTemplateCode, dto.getTemplateCode())
                .eq(StringUtils.hasText(dto.getSceneKey()), CardSceneTemplate::getSceneKey, dto.getSceneKey())
                .eq(dto.getStatus() != null, CardSceneTemplate::getStatus, dto.getStatus())
                .orderByAsc(CardSceneTemplate::getSortNo);
        Page<CardSceneTemplate> result = page(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toShareArtifactItem).toList());
    }

    @Override
    public void updateShareArtifact(Long templateId, ShareArtifactUpdateDTO dto) {
        CardSceneTemplate template = requireTemplate(templateId);
        Map<String, Object> beforeSnapshot = snapshot(template);
        template.setArtifactPresetJson(dto.getArtifactPresetJson());
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
        ActorSceneTemplateRespDTO dto = buildDefaultTemplate(template.getSceneKey());
        dto.setSceneKey(normalizeSceneKey(template.getSceneKey()));
        if (StringUtils.hasText(template.getTemplateName())) {
            dto.setName(template.getTemplateName());
        }
        if (StringUtils.hasText(template.getDescription())) {
            dto.setDescription(template.getDescription());
        }
        if (StringUtils.hasText(template.getLayoutVariant())) {
            dto.setLayoutVariant(template.getLayoutVariant());
        }
        dto.setTier(StringUtils.hasText(template.getTier())
                ? template.getTier()
                : Boolean.TRUE.equals(template.getMembershipRequired()) ? "paid" : "free");
        dto.setRequiredLevel(template.getRequiredLevel() == null ? 1 : template.getRequiredLevel());
        applyThemeOverride(dto.getThemeColors(), template.getBaseThemeJson());
        applyArtifactOverride(dto, template.getArtifactPresetJson());
        return dto;
    }

    private void applyThemeOverride(ActorSceneTemplateRespDTO.ThemeColors themeColors, String baseThemeJson) {
        JsonNode root = readJson(baseThemeJson);
        if (root == null) {
            return;
        }
        JsonNode themeNode = root.has("themeColors") ? root.get("themeColors") : root;
        themeColors.setPrimary(textValue(themeNode, "primary", themeColors.getPrimary()));
        themeColors.setAccent(textValue(themeNode, "accent", themeColors.getAccent()));
        themeColors.setBackground(textValue(themeNode, "background", themeColors.getBackground()));
        themeColors.setText(textValue(themeNode, "text", themeColors.getText()));
        themeColors.setHeroText(textValue(themeNode, "heroText", themeColors.getHeroText()));
    }

    private void applyArtifactOverride(ActorSceneTemplateRespDTO dto, String artifactPresetJson) {
        JsonNode root = readJson(artifactPresetJson);
        if (root == null) {
            return;
        }
        dto.setCoverImage(textValue(root, "coverImage", dto.getCoverImage()));
        dto.setHeroEyebrow(textValue(root, "heroEyebrow", dto.getHeroEyebrow()));
        JsonNode focusNode = root.has("contentFocus") ? root.get("contentFocus") : root.get("focus");
        if (focusNode != null && focusNode.isArray()) {
            dto.setContentFocus(readStringArray(focusNode));
        }
    }

    private JsonNode readJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignore) {
            return null;
        }
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

    private String normalizeSceneKey(String sceneKey) {
        return StringUtils.hasText(sceneKey) ? sceneKey.trim() : "general";
    }

    private ActorSceneTemplateRespDTO buildDefaultTemplate(String sceneKey) {
        ActorSceneTemplateRespDTO dto = new ActorSceneTemplateRespDTO();
        dto.setSceneKey(normalizeSceneKey(sceneKey));
        dto.setCoverImage("");
        dto.setThemeColors(new ActorSceneTemplateRespDTO.ThemeColors());
        switch (dto.getSceneKey()) {
            case "urban" -> {
                dto.setName("都市");
                dto.setDescription("突出生活质感、都市表达与台词感。");
                dto.setHeroEyebrow("URBAN SCREEN TEST");
                dto.setLayoutVariant("compact");
                dto.setContentFocus(List.of("lifestyle", "urban", "dialogue"));
                dto.setTier("free");
                dto.setRequiredLevel(1);
                dto.getThemeColors().setPrimary("#4d7cff");
                dto.getThemeColors().setAccent("#a9d0ff");
                dto.getThemeColors().setBackground("#edf4ff");
                dto.getThemeColors().setText("#162033");
                dto.getThemeColors().setHeroText("#ffffff");
            }
            case "commercial" -> {
                dto.setName("商业");
                dto.setDescription("偏向广告、短视频与商业镜头感。");
                dto.setHeroEyebrow("COMMERCIAL LOOKBOOK");
                dto.setLayoutVariant("compact");
                dto.setContentFocus(List.of("portrait", "commercial", "camera"));
                dto.setTier("free");
                dto.setRequiredLevel(2);
                dto.getThemeColors().setPrimary("#ff8e33");
                dto.getThemeColors().setAccent("#ffd0a7");
                dto.getThemeColors().setBackground("#fff6ec");
                dto.getThemeColors().setText("#2a190c");
                dto.getThemeColors().setHeroText("#ffffff");
            }
            case "costume" -> {
                dto.setName("古装");
                dto.setDescription("突出古装扮相、气质和身段表达。");
                dto.setHeroEyebrow("COSTUME REEL");
                dto.setLayoutVariant("spacious");
                dto.setContentFocus(List.of("production", "costume", "body"));
                dto.setTier("free");
                dto.setRequiredLevel(2);
                dto.getThemeColors().setPrimary("#8d5f3c");
                dto.getThemeColors().setAccent("#ddc2a0");
                dto.getThemeColors().setBackground("#f6efe8");
                dto.getThemeColors().setText("#2c231d");
                dto.getThemeColors().setHeroText("#ffffff");
            }
            case "artistic" -> {
                dto.setName("文艺");
                dto.setDescription("突出质感、表演深度和胶片氛围。");
                dto.setHeroEyebrow("ART HOUSE PROFILE");
                dto.setLayoutVariant("magazine");
                dto.setContentFocus(List.of("production", "artistic", "depth"));
                dto.setTier("free");
                dto.setRequiredLevel(4);
                dto.getThemeColors().setPrimary("#6e5a74");
                dto.getThemeColors().setAccent("#c9b6cf");
                dto.getThemeColors().setBackground("#f4eef6");
                dto.getThemeColors().setText("#241d29");
                dto.getThemeColors().setHeroText("#ffffff");
            }
            default -> {
                dto.setName("通用");
                dto.setDescription("适合首次分享的全量信息名片。");
                dto.setHeroEyebrow("GENERAL CAST CARD");
                dto.setLayoutVariant("compact");
                dto.setContentFocus(List.of("all", "portrait", "work"));
                dto.setTier("free");
                dto.setRequiredLevel(1);
                dto.getThemeColors().setPrimary("#ff7a45");
                dto.getThemeColors().setAccent("#ffb178");
                dto.getThemeColors().setBackground("#fff7f0");
                dto.getThemeColors().setText("#181b22");
                dto.getThemeColors().setHeroText("#ffffff");
            }
        }
        return dto;
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
        snapshot.put("sceneKey", template.getSceneKey());
        snapshot.put("templateName", template.getTemplateName());
        snapshot.put("layoutVariant", template.getLayoutVariant());
        snapshot.put("tier", template.getTier());
        snapshot.put("requiredLevel", template.getRequiredLevel());
        snapshot.put("membershipRequired", template.getMembershipRequired());
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
        context.put("scene_code", template.getSceneKey());
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
        context.put("scene_key", template.getSceneKey());
        context.put("base_theme_json", template.getBaseThemeJson());
        return context;
    }

    private Map<String, Object> artifactContext(CardSceneTemplate template) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("template_id", template.getTemplateId());
        context.put("template_code", template.getTemplateCode());
        context.put("scene_key", template.getSceneKey());
        context.put("artifact_preset_json", template.getArtifactPresetJson());
        return context;
    }

    private ThemeTokenItemDTO toThemeTokenItem(CardSceneTemplate template) {
        ThemeTokenItemDTO dto = new ThemeTokenItemDTO();
        dto.setTemplateId(template.getTemplateId());
        dto.setTemplateCode(template.getTemplateCode());
        dto.setSceneKey(template.getSceneKey());
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
        dto.setSceneKey(template.getSceneKey());
        dto.setTemplateName(template.getTemplateName());
        dto.setStatus(template.getStatus());
        dto.setArtifactPresetJson(template.getArtifactPresetJson());
        dto.setUpdateTime(template.getLastUpdate());
        return dto;
    }
}
