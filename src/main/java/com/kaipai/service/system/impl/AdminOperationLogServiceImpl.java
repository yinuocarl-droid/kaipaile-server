package com.kaipai.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.system.dto.AdminOperationLogDetailRespDTO;
import com.kaipai.model.system.dto.AdminOperationLogListItemDTO;
import com.kaipai.model.system.dto.AdminOperationLogQueryDTO;
import com.kaipai.model.system.entity.AdminOperationLog;
import com.kaipai.mapper.system.AdminOperationLogMapper;
import com.kaipai.service.system.AdminOperationLogService;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminOperationLogServiceImpl extends ServiceImpl<AdminOperationLogMapper, AdminOperationLog> implements AdminOperationLogService {

    @Override
    public PageResult<AdminOperationLogListItemDTO> adminOperationLogList(AdminOperationLogQueryDTO query) {
        Page<AdminOperationLog> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<AdminOperationLog> wrapper = new LambdaQueryWrapper<AdminOperationLog>()
                .select(
                        AdminOperationLog::getOperationLogId,
                        AdminOperationLog::getAdminUserId,
                        AdminOperationLog::getAdminUserName,
                        AdminOperationLog::getModuleCode,
                        AdminOperationLog::getOperationCode,
                        AdminOperationLog::getTargetType,
                        AdminOperationLog::getTargetId,
                        AdminOperationLog::getRequestId,
                        AdminOperationLog::getOperationResult,
                        AdminOperationLog::getFailReason,
                        AdminOperationLog::getClientIp,
                        AdminOperationLog::getConfirmedAt,
                        AdminOperationLog::getCreateTime
                );
        if (query.getAdminUserId() != null) {
            wrapper.eq(AdminOperationLog::getAdminUserId, query.getAdminUserId());
        }
        if (StringUtils.hasText(query.getModuleCode())) {
            wrapper.eq(AdminOperationLog::getModuleCode, query.getModuleCode().trim());
        }
        if (StringUtils.hasText(query.getOperationCode())) {
            wrapper.eq(AdminOperationLog::getOperationCode, query.getOperationCode().trim());
        }
        if (StringUtils.hasText(query.getTargetType())) {
            wrapper.eq(AdminOperationLog::getTargetType, query.getTargetType().trim());
        }
        if (StringUtils.hasText(query.getRequestId())) {
            wrapper.eq(AdminOperationLog::getRequestId, query.getRequestId().trim());
        }
        if (query.getResult() != null) {
            wrapper.eq(AdminOperationLog::getOperationResult, query.getResult());
        }
        if (query.getDateFrom() != null) {
            wrapper.ge(AdminOperationLog::getCreateTime, query.getDateFrom());
        }
        if (query.getDateTo() != null) {
            wrapper.le(AdminOperationLog::getCreateTime, query.getDateTo());
        }
        wrapper.orderByDesc(AdminOperationLog::getCreateTime).orderByDesc(AdminOperationLog::getOperationLogId);
        Page<AdminOperationLog> result = page(page, wrapper);
        List<AdminOperationLogListItemDTO> list = result.getRecords().stream().map(this::toListItem).toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AdminOperationLogDetailRespDTO adminOperationLogDetail(Long operationLogId) {
        AdminOperationLog log = getById(operationLogId);
        if (log == null) {
            throw new BizException("操作日志不存在");
        }
        AdminOperationLogDetailRespDTO dto = new AdminOperationLogDetailRespDTO();
        BeanUtils.copyProperties(log, dto);
        return dto;
    }

    private AdminOperationLogListItemDTO toListItem(AdminOperationLog log) {
        AdminOperationLogListItemDTO dto = new AdminOperationLogListItemDTO();
        BeanUtils.copyProperties(log, dto);
        return dto;
    }
}


