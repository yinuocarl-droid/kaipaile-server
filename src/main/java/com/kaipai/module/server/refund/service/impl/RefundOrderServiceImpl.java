package com.kaipai.module.server.refund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.ResultCode;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import com.kaipai.module.model.refund.dto.RefundApproveDTO;
import com.kaipai.module.model.refund.dto.RefundOrderDetailDTO;
import com.kaipai.module.model.refund.dto.RefundOrderQueryDTO;
import com.kaipai.module.model.refund.dto.RefundOrderRespDTO;
import com.kaipai.module.model.refund.dto.RefundRejectDTO;
import com.kaipai.module.model.refund.entity.RefundOperateLog;
import com.kaipai.module.model.refund.entity.RefundOrder;
import com.kaipai.module.server.payment.mapper.PaymentOrderMapper;
import com.kaipai.module.server.refund.mapper.RefundOrderMapper;
import com.kaipai.module.server.refund.service.RefundOrderService;
import com.kaipai.module.server.refund.service.RefundOperateLogService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundOrderServiceImpl extends ServiceImpl<RefundOrderMapper, RefundOrder> implements RefundOrderService {

    private final RefundOperateLogService refundOperateLogService;
    private final PaymentOrderMapper paymentOrderMapper;
    private final AdminAuthContext adminAuthContext;
    private final AdminOperationLogger adminOperationLogger;

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
    public RefundOrderDetailDTO adminOrderDetail(Long refundOrderId) {
        RefundOrder order = getById(refundOrderId);
        if (order == null) {
            throw new BizException("退款单不存在");
        }
        PaymentOrder paymentOrder = paymentOrderMapper.selectById(order.getPaymentOrderId());
        RefundOrderDetailDTO dto = new RefundOrderDetailDTO();
        dto.setRefundOrderId(order.getRefundOrderId());
        dto.setRefundNo(order.getRefundNo());
        dto.setPaymentOrderId(order.getPaymentOrderId());
        dto.setPaymentOrderNo(paymentOrder == null ? null : paymentOrder.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setRefundAmount(order.getRefundAmount());
        dto.setRefundReason(order.getRefundReason());
        dto.setAuditStatus(order.getAuditStatus());
        dto.setRefundStatus(order.getRefundStatus());
        dto.setAuditRemark(order.getAuditRemark());
        dto.setAuditorId(order.getAuditorId());
        dto.setAuditedAt(order.getAuditedAt());
        dto.setChannelRefundNo(order.getChannelRefundNo());
        dto.setRefundedAt(order.getRefundedAt());
        if (paymentOrder != null) {
            dto.setPaymentAmount(paymentOrder.getAmount());
            dto.setPaymentStatus(paymentOrder.getPayStatus());
            dto.setPayChannel(paymentOrder.getPayChannel());
            dto.setPaidAt(paymentOrder.getPaidAt());
        }
        dto.setOperateLogs(refundOperateLogService.listByRefundOrderId(refundOrderId));
        return dto;
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
        Map<String, Object> beforeSnapshot = snapshot(order);
        order.setAuditStatus(1);
        order.setRefundStatus(1);
        order.setAuditRemark(dto.getAuditRemark());
        order.setAuditorId(adminAuthContext.getCurrentAdminUserId());
        order.setAuditedAt(LocalDateTime.now());
        updateById(order);
        logOperate(order.getRefundOrderId(), "approve", dto.getAuditRemark());
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("refund")
                .operationCode("approve")
                .targetType("refund_order")
                .targetId(order.getRefundOrderId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(order))
                .extraContext(buildContext(order, paymentOrderMapper.selectById(order.getPaymentOrderId()), dto.getAuditRemark()))
                .operationResult(1)
                .build());
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
        Map<String, Object> beforeSnapshot = snapshot(order);
        order.setAuditStatus(2);
        order.setRefundStatus(3);
        order.setAuditRemark(dto.getAuditRemark());
        order.setAuditorId(adminAuthContext.getCurrentAdminUserId());
        order.setAuditedAt(LocalDateTime.now());
        updateById(order);
        logOperate(order.getRefundOrderId(), "reject", dto.getAuditRemark());
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("refund")
                .operationCode("reject")
                .targetType("refund_order")
                .targetId(order.getRefundOrderId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(order))
                .extraContext(buildContext(order, paymentOrderMapper.selectById(order.getPaymentOrderId()), dto.getAuditRemark()))
                .operationResult(1)
                .build());
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
        log.setOperatorId(adminAuthContext.getCurrentAdminUserId());
        log.setActionType(actionType);
        log.setRemark(remark);
        refundOperateLogService.save(log);
    }

    private Map<String, Object> snapshot(RefundOrder order) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("refundOrderId", order.getRefundOrderId());
        snapshot.put("refundNo", order.getRefundNo());
        snapshot.put("paymentOrderId", order.getPaymentOrderId());
        snapshot.put("userId", order.getUserId());
        snapshot.put("refundAmount", order.getRefundAmount());
        snapshot.put("auditStatus", order.getAuditStatus());
        snapshot.put("refundStatus", order.getRefundStatus());
        snapshot.put("auditRemark", order.getAuditRemark());
        snapshot.put("auditorId", order.getAuditorId());
        snapshot.put("auditedAt", order.getAuditedAt());
        return snapshot;
    }

    private Map<String, Object> buildContext(RefundOrder order, PaymentOrder paymentOrder, String auditRemark) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("refund_order_id", order.getRefundOrderId());
        context.put("refund_no", order.getRefundNo());
        context.put("payment_order_id", order.getPaymentOrderId());
        context.put("payment_order_no", paymentOrder == null ? null : paymentOrder.getOrderNo());
        context.put("refund_amount", order.getRefundAmount());
        context.put("audit_status_after", order.getAuditStatus());
        context.put("channel_status", order.getRefundStatus());
        context.put("audit_remark", auditRemark);
        return context;
    }
}
