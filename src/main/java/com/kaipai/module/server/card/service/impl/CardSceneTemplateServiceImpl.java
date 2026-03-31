package com.kaipai.module.server.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardSceneTemplateServiceImpl extends ServiceImpl<CardSceneTemplateMapper, CardSceneTemplate> implements CardSceneTemplateService {

    private final TemplatePublishLogService publishLogService;

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
    }

    @Override
    public void updateTemplate(TemplateUpdateDTO dto) {
        CardSceneTemplate template = getById(dto.getTemplateId());
        if (template == null) {
            throw new BizException("Template not found");
        }
        BeanUtils.copyProperties(dto, template);
        template.setLastUpdate(LocalDateTime.now());
        updateById(template);
    }

    @Override
    public void publishTemplate(TemplatePublishDTO dto) {
        CardSceneTemplate template = getById(dto.getTemplateId());
        if (template == null) {
            throw new BizException("Template not found");
        }
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
        publishLogService.recordPublishLog(log);
    }

    @Override
    public void rollbackTemplate(TemplateRollbackDTO dto) {
        CardSceneTemplate template = getById(dto.getTemplateId());
        if (template == null) {
            throw new BizException("Template not found");
        }
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
        publishLogService.recordPublishLog(log);
    }
}
