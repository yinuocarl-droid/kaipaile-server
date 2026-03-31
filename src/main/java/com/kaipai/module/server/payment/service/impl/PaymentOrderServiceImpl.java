package com.kaipai.module.server.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.membership.entity.MembershipProduct;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderDetailDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderListItemDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderQueryDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionListItemDTO;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import com.kaipai.module.model.payment.entity.PaymentTransaction;
import com.kaipai.module.server.membership.mapper.MembershipProductMapper;
import com.kaipai.module.server.payment.mapper.PaymentOrderMapper;
import com.kaipai.module.server.payment.mapper.PaymentTransactionMapper;
import com.kaipai.module.server.payment.service.PaymentOrderService;
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
public class PaymentOrderServiceImpl extends ServiceImpl<PaymentOrderMapper, PaymentOrder> implements PaymentOrderService {

    private final PaymentTransactionMapper paymentTransactionMapper;
    private final MembershipProductMapper membershipProductMapper;

    @Override
    public PageResult<AdminPaymentOrderListItemDTO> adminOrderList(AdminPaymentOrderQueryDTO query) {
        Page<PaymentOrder> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<PaymentOrder> wrapper = buildOrderQuery(query);
        wrapper.orderByDesc(PaymentOrder::getCreateTime);
        Page<PaymentOrder> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }
        Map<Long, MembershipProduct> productMap = getProductMap(result.getRecords());
        List<AdminPaymentOrderListItemDTO> list = result.getRecords().stream()
                .map(order -> toListItem(order, productMap.get(order.getProductId())))
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AdminPaymentOrderDetailDTO adminOrderDetail(Long id) {
        PaymentOrder order = getById(id);
        if (order == null) {
            throw new BizException("支付订单不存在");
        }
        MembershipProduct product = order.getProductId() == null ? null : membershipProductMapper.selectById(order.getProductId());
        List<PaymentTransaction> transactions = paymentTransactionMapper.selectList(new LambdaQueryWrapper<PaymentTransaction>()
                .eq(PaymentTransaction::getPaymentOrderId, order.getPaymentOrderId())
                .orderByDesc(PaymentTransaction::getCreateTime));
        AdminPaymentOrderDetailDTO dto = new AdminPaymentOrderDetailDTO();
        dto.setPaymentOrderId(order.getPaymentOrderId());
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setBizType(order.getBizType());
        dto.setBizRefId(order.getBizRefId());
        dto.setProductId(order.getProductId());
        dto.setAmount(order.getAmount());
        dto.setCurrencyCode(order.getCurrencyCode());
        dto.setPayStatus(order.getPayStatus());
        dto.setPayChannel(order.getPayChannel());
        dto.setCreateTime(order.getCreateTime());
        dto.setPaidAt(order.getPaidAt());
        dto.setClosedAt(order.getClosedAt());
        dto.setLastUpdate(order.getLastUpdate());
        if (product != null) {
            dto.setProductCode(product.getProductCode());
            dto.setProductName(product.getProductName());
            dto.setMembershipTier(product.getMembershipTier());
            dto.setDurationDays(product.getDurationDays());
        }
        List<AdminPaymentTransactionListItemDTO> transactionItems = transactions.stream()
                .map(transaction -> toTransactionListItem(transaction, order))
                .collect(Collectors.toList());
        dto.setTransactions(transactionItems);
        dto.setTransactionCount(transactionItems.size());
        return dto;
    }

    private LambdaQueryWrapper<PaymentOrder> buildOrderQuery(AdminPaymentOrderQueryDTO query) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        if (query.getOrderNo() != null && !query.getOrderNo().isBlank()) {
            wrapper.like(PaymentOrder::getOrderNo, query.getOrderNo().trim());
        }
        if (query.getUserId() != null) {
            wrapper.eq(PaymentOrder::getUserId, query.getUserId());
        }
        if (query.getPayStatus() != null) {
            wrapper.eq(PaymentOrder::getPayStatus, query.getPayStatus());
        }
        if (query.getPayChannel() != null && !query.getPayChannel().isBlank()) {
            wrapper.eq(PaymentOrder::getPayChannel, query.getPayChannel().trim());
        }
        if (query.getBizType() != null && !query.getBizType().isBlank()) {
            wrapper.eq(PaymentOrder::getBizType, query.getBizType().trim());
        }
        if (query.getProductId() != null) {
            wrapper.eq(PaymentOrder::getProductId, query.getProductId());
        }
        if (query.getCreateTimeFrom() != null) {
            wrapper.ge(PaymentOrder::getCreateTime, query.getCreateTimeFrom());
        }
        if (query.getCreateTimeTo() != null) {
            wrapper.le(PaymentOrder::getCreateTime, query.getCreateTimeTo());
        }
        if (query.getPaidTimeFrom() != null) {
            wrapper.ge(PaymentOrder::getPaidAt, query.getPaidTimeFrom());
        }
        if (query.getPaidTimeTo() != null) {
            wrapper.le(PaymentOrder::getPaidAt, query.getPaidTimeTo());
        }
        return wrapper;
    }

    private Map<Long, MembershipProduct> getProductMap(List<PaymentOrder> orders) {
        Set<Long> productIds = orders.stream()
                .map(PaymentOrder::getProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return membershipProductMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(MembershipProduct::getProductId, Function.identity()));
    }

    private AdminPaymentOrderListItemDTO toListItem(PaymentOrder order, MembershipProduct product) {
        AdminPaymentOrderListItemDTO dto = new AdminPaymentOrderListItemDTO();
        dto.setPaymentOrderId(order.getPaymentOrderId());
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setBizType(order.getBizType());
        dto.setBizRefId(order.getBizRefId());
        dto.setProductId(order.getProductId());
        dto.setAmount(order.getAmount());
        dto.setCurrencyCode(order.getCurrencyCode());
        dto.setPayStatus(order.getPayStatus());
        dto.setPayChannel(order.getPayChannel());
        dto.setCreateTime(order.getCreateTime());
        dto.setPaidAt(order.getPaidAt());
        dto.setClosedAt(order.getClosedAt());
        if (product != null) {
            dto.setProductCode(product.getProductCode());
            dto.setProductName(product.getProductName());
        }
        return dto;
    }

    private AdminPaymentTransactionListItemDTO toTransactionListItem(PaymentTransaction transaction, PaymentOrder order) {
        AdminPaymentTransactionListItemDTO dto = new AdminPaymentTransactionListItemDTO();
        dto.setTransactionId(transaction.getTransactionId());
        dto.setPaymentOrderId(transaction.getPaymentOrderId());
        dto.setPaymentOrderNo(order.getOrderNo());
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
