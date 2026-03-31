package com.kaipai.module.server.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.card.dto.TemplateCreateDTO;
import com.kaipai.module.model.card.dto.TemplateItemDTO;
import com.kaipai.module.model.card.dto.TemplateListQueryDTO;
import com.kaipai.module.model.card.dto.TemplatePublishDTO;
import com.kaipai.module.model.card.dto.TemplateRollbackDTO;
import com.kaipai.module.model.card.dto.TemplateUpdateDTO;
import com.kaipai.module.model.card.entity.CardSceneTemplate;
import com.kaipai.module.server.card.mapper.CardSceneTemplateMapper;
import com.kaipai.module.server.card.service.CardSceneTemplateService;
import com.kaipai.module.server.card.service.TemplatePublishLogService;
import com.kaipai.module.model.card.entity.TemplatePublishLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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
        wrapper.eq(dto.getSceneKey() != null, CardSceneTemplate::getSceneKey, dto.getSceneKey())
                .eq(dto.getStatus() != null, CardSceneTemplate::getStatus, dto.getStatus())
                .eq(dto.getTier() != null, CardSceneTemplate::getTier, dto.getTier())
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
        CardSceneTemplate template = getById(dto.getTemplateId());
        if (template == null) {
            throw new BizException("Template not found");
        }
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
    public void publishTemplate(TemplatePublishDTO dto) {
        CardSceneTemplate template = getById(dto.getTemplateId());
        if (template == null) {
            throw new BizException("Template not found");
        }
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
        CardSceneTemplate template = getById(dto.getTemplateId());
        if (template == null) {
            throw new BizException("Template not found");
        }
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
}
