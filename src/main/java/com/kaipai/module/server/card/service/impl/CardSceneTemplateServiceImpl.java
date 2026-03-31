package com.kaipai.module.server.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardSceneTemplateServiceImpl extends ServiceImpl<CardSceneTemplateMapper, CardSceneTemplate> implements CardSceneTemplateService {

    private final TemplatePublishLogService publishLogService;
    private final AdminAuthContext adminAuthContext;
    private final AdminOperationLogger adminOperationLogger;

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
