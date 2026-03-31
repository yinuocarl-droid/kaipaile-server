package com.kaipai.module.server.refund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.ResultCode;
import com.kaipai.module.model.refund.dto.RefundApproveDTO;
import com.kaipai.module.model.refund.dto.RefundOrderQueryDTO;
import com.kaipai.module.model.refund.dto.RefundOrderRespDTO;
import com.kaipai.module.model.refund.dto.RefundRejectDTO;
import com.kaipai.module.model.refund.entity.RefundOperateLog;
import com.kaipai.module.model.refund.entity.RefundOrder;
import com.kaipai.module.server.refund.mapper.RefundOrderMapper;
import com.kaipai.module.server.refund.service.RefundOrderService;
import com.kaipai.module.server.refund.service.RefundOperateLogService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundOrderServiceImpl extends ServiceImpl<RefundOrderMapper, RefundOrder> implements RefundOrderService {

    private final RefundOperateLogService refundOperateLogService;

    @Override
    public PageResult<RefundOrderRespDTO> adminOrderList(RefundOrderQueryDTO query) {
        Page<RefundOrder> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<RefundOrder> wrapper = new LambdaQueryWrapper<>();
        if (query.getRefundNo() != null) {
            wrapper.eq(RefundOrder::getRefundNo, query.getRefundNo());
        }
        if (query.getUserId() != null) {
            wrapper.eq(RefundOrder::getUserId, query.getUserId());
        }
        if (query.getAuditStatus() != null) {
            wrapper.eq(RefundOrder::getAuditStatus, query.getAuditStatus());
        }
        if (query.getRefundStatus() != null) {
            wrapper.eq(RefundOrder::getRefundStatus, query.getRefundStatus());
        }
        wrapper.orderByDesc(RefundOrder::getCreateTime);
        Page<RefundOrder> result = page(page, wrapper);
        List<RefundOrderRespDTO> records = result.getRecords().stream().map(this::toDto).collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    @Transactional
    public void approveRefund(Long refundOrderId, RefundApproveDTO dto) {
        RefundOrder order = getById(refundOrderId);
        if (order == null) {
            throw new BizException(ResultCode.FAILED.getMessage());
        }
        if (!Objects.equals(order.getAuditStatus(), 0)) {
            throw new BizException("退款单状态异常");
        }
        order.setAuditStatus(1);
        order.setRefundStatus(1);
        order.setAuditRemark(dto.getAuditRemark());
        order.setAuditedAt(LocalDateTime.now());
        updateById(order);
        logOperate(order.getRefundOrderId(), "approve", dto.getAuditRemark());
    }

    @Override
    @Transactional
    public void rejectRefund(Long refundOrderId, RefundRejectDTO dto) {
        RefundOrder order = getById(refundOrderId);
        if (order == null) {
            throw new BizException(ResultCode.FAILED.getMessage());
        }
        if (!Objects.equals(order.getAuditStatus(), 0)) {
            throw new BizException("退款单状态异常");
        }
        order.setAuditStatus(2);
        order.setRefundStatus(3);
        order.setAuditRemark(dto.getAuditRemark());
        order.setAuditedAt(LocalDateTime.now());
        updateById(order);
        logOperate(order.getRefundOrderId(), "reject", dto.getAuditRemark());
    }

    private RefundOrderRespDTO toDto(RefundOrder order) {
        RefundOrderRespDTO dto = new RefundOrderRespDTO();
        dto.setRefundOrderId(order.getRefundOrderId());
        dto.setRefundNo(order.getRefundNo());
        dto.setPaymentOrderId(order.getPaymentOrderId());
        dto.setUserId(order.getUserId());
        dto.setRefundAmount(order.getRefundAmount());
        dto.setAuditStatus(order.getAuditStatus());
        dto.setRefundStatus(order.getRefundStatus());
        dto.setRefundReason(order.getRefundReason());
        dto.setAuditRemark(order.getAuditRemark());
        dto.setAuditedAt(order.getAuditedAt());
        dto.setChannelRefundNo(order.getChannelRefundNo());
        dto.setRefundedAt(order.getRefundedAt());
        return dto;
    }

    private void logOperate(Long refundOrderId, String actionType, String remark) {
        RefundOperateLog log = new RefundOperateLog();
        log.setRefundOrderId(refundOrderId);
        log.setOperatorId(0L);
        log.setActionType(actionType);
        log.setRemark(remark);
        refundOperateLogService.save(log);
    }
}
