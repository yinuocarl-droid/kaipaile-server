package com.kaipai.module.server.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.membership.entity.MembershipProduct;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionDetailDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionListItemDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionQueryDTO;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import com.kaipai.module.model.payment.entity.PaymentTransaction;
import com.kaipai.module.server.membership.mapper.MembershipProductMapper;
import com.kaipai.module.server.payment.mapper.PaymentOrderMapper;
import com.kaipai.module.server.payment.mapper.PaymentTransactionMapper;
import com.kaipai.module.server.payment.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentTransactionServiceImpl extends ServiceImpl<PaymentTransactionMapper, PaymentTransaction> implements PaymentTransactionService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final MembershipProductMapper membershipProductMapper;

    @Override
    public PageResult<AdminPaymentTransactionListItemDTO> adminTransactionList(AdminPaymentTransactionQueryDTO query) {
        Page<PaymentTransaction> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<PaymentTransaction> wrapper = buildTransactionQuery(query);
        wrapper.orderByDesc(PaymentTransaction::getCreateTime);
        Page<PaymentTransaction> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }
        Map<Long, PaymentOrder> orderMap = getOrderMap(result.getRecords());
        List<AdminPaymentTransactionListItemDTO> list = result.getRecords().stream()
                .map(transaction -> toListItem(transaction, orderMap.get(transaction.getPaymentOrderId())))
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AdminPaymentTransactionDetailDTO adminTransactionDetail(Long id) {
        PaymentTransaction transaction = getById(id);
        if (transaction == null) {
            throw new BizException("支付流水不存在");
        }
        PaymentOrder order = transaction.getPaymentOrderId() == null ? null : paymentOrderMapper.selectById(transaction.getPaymentOrderId());
        MembershipProduct product = order == null || order.getProductId() == null
                ? null
                : membershipProductMapper.selectById(order.getProductId());
        AdminPaymentTransactionDetailDTO dto = new AdminPaymentTransactionDetailDTO();
        dto.setTransactionId(transaction.getTransactionId());
        dto.setPaymentOrderId(transaction.getPaymentOrderId());
        dto.setChannelTradeNo(transaction.getChannelTradeNo());
        dto.setChannel(transaction.getChannel());
        dto.setTradeType(transaction.getTradeType());
        dto.setAmount(transaction.getAmount());
        dto.setStatus(transaction.getStatus());
        dto.setCallbackPayload(transaction.getCallbackPayload());
        dto.setCallbackTime(transaction.getCallbackTime());
        dto.setCreateTime(transaction.getCreateTime());
        dto.setLastUpdate(transaction.getLastUpdate());
        if (order != null) {
            dto.setPaymentOrderNo(order.getOrderNo());
            dto.setUserId(order.getUserId());
            dto.setBizType(order.getBizType());
            dto.setBizRefId(order.getBizRefId());
            dto.setProductId(order.getProductId());
            dto.setPayChannel(order.getPayChannel());
            dto.setPayStatus(order.getPayStatus());
            dto.setOrderAmount(order.getAmount());
            dto.setCurrencyCode(order.getCurrencyCode());
            dto.setPaidAt(order.getPaidAt());
        }
        if (product != null) {
            dto.setProductCode(product.getProductCode());
            dto.setProductName(product.getProductName());
        }
        return dto;
    }

    private LambdaQueryWrapper<PaymentTransaction> buildTransactionQuery(AdminPaymentTransactionQueryDTO query) {
        LambdaQueryWrapper<PaymentTransaction> wrapper = new LambdaQueryWrapper<>();
        if (query.getChannelTradeNo() != null && !query.getChannelTradeNo().isBlank()) {
            wrapper.like(PaymentTransaction::getChannelTradeNo, query.getChannelTradeNo().trim());
        }
        if (query.getChannel() != null && !query.getChannel().isBlank()) {
            wrapper.eq(PaymentTransaction::getChannel, query.getChannel().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(PaymentTransaction::getStatus, query.getStatus());
        }
        if (query.getCallbackTimeFrom() != null) {
            wrapper.ge(PaymentTransaction::getCallbackTime, query.getCallbackTimeFrom());
        }
        if (query.getCallbackTimeTo() != null) {
            wrapper.le(PaymentTransaction::getCallbackTime, query.getCallbackTimeTo());
        }
        if (query.getPaymentOrderNo() != null && !query.getPaymentOrderNo().isBlank()) {
            List<Long> orderIds = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                            .select(PaymentOrder::getPaymentOrderId)
                            .like(PaymentOrder::getOrderNo, query.getPaymentOrderNo().trim()))
                    .stream()
                    .map(PaymentOrder::getPaymentOrderId)
                    .collect(Collectors.toList());
            if (orderIds.isEmpty()) {
                wrapper.in(PaymentTransaction::getPaymentOrderId, Collections.singleton(-1L));
            } else {
                wrapper.in(PaymentTransaction::getPaymentOrderId, orderIds);
            }
        }
        return wrapper;
    }

    private Map<Long, PaymentOrder> getOrderMap(List<PaymentTransaction> transactions) {
        Set<Long> orderIds = transactions.stream()
                .map(PaymentTransaction::getPaymentOrderId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return paymentOrderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(PaymentOrder::getPaymentOrderId, Function.identity()));
    }

    private AdminPaymentTransactionListItemDTO toListItem(PaymentTransaction transaction, PaymentOrder order) {
        AdminPaymentTransactionListItemDTO dto = new AdminPaymentTransactionListItemDTO();
        dto.setTransactionId(transaction.getTransactionId());
        dto.setPaymentOrderId(transaction.getPaymentOrderId());
        dto.setPaymentOrderNo(order == null ? null : order.getOrderNo());
        dto.setChannelTradeNo(transaction.getChannelTradeNo());
        dto.setChannel(transaction.getChannel());
        dto.setTradeType(transaction.getTradeType());
        dto.setAmount(transaction.getAmount());
        dto.setStatus(transaction.getStatus());
        dto.setCallbackTime(transaction.getCallbackTime());
        dto.setCreateTime(transaction.getCreateTime());
        return dto;
    }
}
