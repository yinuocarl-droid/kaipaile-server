package com.kaipai.service.card.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.card.dto.TemplatePublishLogItemDTO;
import com.kaipai.model.card.dto.TemplatePublishLogQueryDTO;
import com.kaipai.model.card.entity.TemplatePublishLog;
import com.kaipai.mapper.card.TemplatePublishLogMapper;
import com.kaipai.service.card.TemplatePublishLogService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TemplatePublishLogServiceImpl extends ServiceImpl<TemplatePublishLogMapper, TemplatePublishLog> implements TemplatePublishLogService {

    @Override
    public void recordPublishLog(TemplatePublishLog log) {
        save(log);
    }

    @Override
    public List<TemplatePublishLogItemDTO> listByTemplateId(Long templateId) {
        return lambdaQuery()
                .eq(TemplatePublishLog::getTemplateId, templateId)
                .orderByDesc(TemplatePublishLog::getPublishedAt)
                .list()
                .stream()
                .map(this::toItem)
                .toList();
    }

    @Override
    public PageResult<TemplatePublishLogItemDTO> adminPublishLogList(TemplatePublishLogQueryDTO queryDTO) {
        Page<TemplatePublishLog> page = new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize());
        LambdaQueryWrapper<TemplatePublishLog> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getTemplateId() != null) {
            wrapper.eq(TemplatePublishLog::getTemplateId, queryDTO.getTemplateId());
        }
        if (StringUtils.hasText(queryDTO.getPublishVersion())) {
            wrapper.eq(TemplatePublishLog::getPublishVersion, queryDTO.getPublishVersion().trim());
        }
        if (StringUtils.hasText(queryDTO.getActionType())) {
            wrapper.eq(TemplatePublishLog::getActionType, queryDTO.getActionType().trim());
        }
        if (queryDTO.getPublishedBy() != null) {
            wrapper.eq(TemplatePublishLog::getPublishedBy, queryDTO.getPublishedBy());
        }
        if (queryDTO.getPublishedAtFrom() != null) {
            wrapper.ge(TemplatePublishLog::getPublishedAt, queryDTO.getPublishedAtFrom());
        }
        if (queryDTO.getPublishedAtTo() != null) {
            wrapper.le(TemplatePublishLog::getPublishedAt, queryDTO.getPublishedAtTo());
        }
        wrapper.orderByDesc(TemplatePublishLog::getPublishedAt)
                .orderByDesc(TemplatePublishLog::getPublishLogId);
        Page<TemplatePublishLog> result = page(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toItem).toList());
    }

    private TemplatePublishLogItemDTO toItem(TemplatePublishLog entity) {
        TemplatePublishLogItemDTO dto = new TemplatePublishLogItemDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}



