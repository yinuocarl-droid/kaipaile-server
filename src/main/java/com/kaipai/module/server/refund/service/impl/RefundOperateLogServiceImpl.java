package com.kaipai.module.server.refund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.refund.dto.RefundOperateLogItemDTO;
import com.kaipai.module.model.refund.dto.RefundOperateLogQueryDTO;
import com.kaipai.module.model.refund.entity.RefundOrder;
import com.kaipai.module.model.refund.entity.RefundOperateLog;
import com.kaipai.module.server.refund.mapper.RefundOrderMapper;
import com.kaipai.module.server.refund.mapper.RefundOperateLogMapper;
import com.kaipai.module.server.refund.service.RefundOperateLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RefundOperateLogServiceImpl extends ServiceImpl<RefundOperateLogMapper, RefundOperateLog> implements RefundOperateLogService {

    private final RefundOrderMapper refundOrderMapper;

    @Override
    public List<RefundOperateLogItemDTO> listByRefundOrderId(Long refundOrderId) {
        return lambdaQuery()
                .eq(RefundOperateLog::getRefundOrderId, refundOrderId)
                .orderByDesc(RefundOperateLog::getCreateTime)
                .list()
                .stream()
                .map(this::toItem)
                .toList();
    }

    @Override
    public PageResult<RefundOperateLogItemDTO> adminLogList(RefundOperateLogQueryDTO query) {
        Page<RefundOperateLog> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<RefundOperateLog> wrapper = new LambdaQueryWrapper<>();
        if (query.getRefundOrderId() != null) {
            wrapper.eq(RefundOperateLog::getRefundOrderId, query.getRefundOrderId());
        }
        if (query.getOperatorId() != null) {
            wrapper.eq(RefundOperateLog::getOperatorId, query.getOperatorId());
        }
        if (StringUtils.hasText(query.getActionType())) {
            wrapper.eq(RefundOperateLog::getActionType, query.getActionType().trim());
        }
        if (query.getDateFrom() != null) {
            wrapper.ge(RefundOperateLog::getCreateTime, query.getDateFrom());
        }
        if (query.getDateTo() != null) {
            wrapper.le(RefundOperateLog::getCreateTime, query.getDateTo());
        }
        if (StringUtils.hasText(query.getRefundNo())) {
            List<RefundOrder> refundOrders = refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrder>()
                    .eq(RefundOrder::getRefundNo, query.getRefundNo().trim()));
            Set<Long> refundOrderIds = refundOrders.stream().map(RefundOrder::getRefundOrderId).collect(Collectors.toSet());
            if (refundOrderIds.isEmpty()) {
                return new PageResult<>(0, Collections.emptyList());
            }
            wrapper.in(RefundOperateLog::getRefundOrderId, refundOrderIds);
        }
        wrapper.orderByDesc(RefundOperateLog::getCreateTime)
                .orderByDesc(RefundOperateLog::getLogId);
        Page<RefundOperateLog> result = page(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toItem).toList());
    }

    private RefundOperateLogItemDTO toItem(RefundOperateLog entity) {
        RefundOperateLogItemDTO dto = new RefundOperateLogItemDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
