package com.kaipai.module.server.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.capability.entity.CapabilityProduct;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderDetailDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderListItemDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentOrderQueryDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionListItemDTO;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import com.kaipai.module.model.payment.entity.PaymentTransaction;
import com.kaipai.module.model.refund.entity.RefundOrder;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.capability.mapper.CapabilityProductMapper;
import com.kaipai.module.server.payment.mapper.PaymentOrderMapper;
import com.kaipai.module.server.payment.mapper.PaymentTransactionMapper;
import com.kaipai.module.server.payment.service.PaymentOrderService;
import com.kaipai.module.server.refund.mapper.RefundOrderMapper;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentOrderServiceImpl extends ServiceImpl<PaymentOrderMapper, PaymentOrder> implements PaymentOrderService {

    private static final List<String> CAPABILITY_BIZ_TYPES = List.of("capability_purchase", "capability_renewal");

    private final PaymentTransactionMapper paymentTransactionMapper;
    private final CapabilityProductMapper capabilityProductMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final UserMapper userMapper;

    @Override
    public PageResult<AdminPaymentOrderListItemDTO> adminOrderList(AdminPaymentOrderQueryDTO query) {
        Page<PaymentOrder> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<PaymentOrder> wrapper = buildOrderQuery(query);
        wrapper.orderByDesc(PaymentOrder::getCreateTime);
        Page<PaymentOrder> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }
        Map<Long, CapabilityProduct> productMap = getProductMap(result.getRecords());
        Map<Long, User> userMap = getUserMap(result.getRecords());
        List<AdminPaymentOrderListItemDTO> list = result.getRecords().stream()
                .map(order -> toListItem(order, productMap.get(order.getProductId()), userMap.get(order.getUserId())))
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AdminPaymentOrderDetailDTO adminOrderDetail(Long id) {
        PaymentOrder order = getById(id);
        if (order == null) {
            throw new BizException("支付订单不存在");
        }
        CapabilityProduct product = order.getProductId() == null ? null : capabilityProductMapper.selectById(order.getProductId());
        User user = order.getUserId() == null ? null : userMapper.selectById(order.getUserId());
        List<PaymentTransaction> transactions = paymentTransactionMapper.selectList(new LambdaQueryWrapper<PaymentTransaction>()
                .eq(PaymentTransaction::getPaymentOrderId, order.getPaymentOrderId())
                .orderByDesc(PaymentTransaction::getCreateTime));
        List<RefundOrder> refundOrders = refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrder>()
                .eq(RefundOrder::getPaymentOrderId, order.getPaymentOrderId())
                .orderByDesc(RefundOrder::getCreateTime)
                .orderByDesc(RefundOrder::getRefundOrderId));
        AdminPaymentOrderDetailDTO dto = new AdminPaymentOrderDetailDTO();
        List<AdminPaymentTransactionListItemDTO> transactionItems = transactions.stream()
                .map(transaction -> toTransactionListItem(transaction, order))
                .collect(Collectors.toList());
        dto.setOrderInfo(toOrderInfo(order, user));
        dto.setProductInfo(toProductInfo(order, product));
        dto.setPaymentInfo(toPaymentInfo(transactionItems));
        dto.setRefundSummary(toRefundSummary(refundOrders));
        return dto;
    }

    private LambdaQueryWrapper<PaymentOrder> buildOrderQuery(AdminPaymentOrderQueryDTO query) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getOrderNo())) {
            wrapper.like(PaymentOrder::getOrderNo, query.getOrderNo().trim());
        }
        if (query.getUserId() != null) {
            wrapper.eq(PaymentOrder::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getPhone())) {
            Set<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getUserId)
                            .like(User::getPhone, query.getPhone().trim()))
                    .stream()
                    .map(User::getUserId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (userIds.isEmpty()) {
                wrapper.in(PaymentOrder::getPaymentOrderId, Collections.singleton(-1L));
                return wrapper;
            }
            wrapper.in(PaymentOrder::getUserId, userIds);
        }
        if (query.getPayStatus() != null) {
            wrapper.eq(PaymentOrder::getPayStatus, query.getPayStatus());
        }
        if (StringUtils.hasText(query.getPayChannel())) {
            wrapper.eq(PaymentOrder::getPayChannel, query.getPayChannel().trim());
        }
        wrapper.in(PaymentOrder::getBizType, CAPABILITY_BIZ_TYPES);
        if (StringUtils.hasText(query.getBizType())) {
            wrapper.eq(PaymentOrder::getBizType, query.getBizType().trim());
        }
        if (query.getProductId() != null) {
            wrapper.eq(PaymentOrder::getProductId, query.getProductId());
        }
        LocalDateTime createdAtFrom = firstNonNull(query.getCreatedAtFrom(), query.getCreateTimeFrom());
        LocalDateTime createdAtTo = firstNonNull(query.getCreatedAtTo(), query.getCreateTimeTo());
        LocalDateTime paidAtFrom = firstNonNull(query.getPaidAtFrom(), query.getPaidTimeFrom());
        LocalDateTime paidAtTo = firstNonNull(query.getPaidAtTo(), query.getPaidTimeTo());
        if (createdAtFrom != null) {
            wrapper.ge(PaymentOrder::getCreateTime, createdAtFrom);
        }
        if (createdAtTo != null) {
            wrapper.le(PaymentOrder::getCreateTime, createdAtTo);
        }
        if (paidAtFrom != null) {
            wrapper.ge(PaymentOrder::getPaidAt, paidAtFrom);
        }
        if (paidAtTo != null) {
            wrapper.le(PaymentOrder::getPaidAt, paidAtTo);
        }
        return wrapper;
    }

    private Map<Long, CapabilityProduct> getProductMap(List<PaymentOrder> orders) {
        Set<Long> productIds = orders.stream()
                .map(PaymentOrder::getProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return capabilityProductMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(CapabilityProduct::getProductId, Function.identity()));
    }

    private Map<Long, User> getUserMap(List<PaymentOrder> orders) {
        Set<Long> userIds = orders.stream()
                .map(PaymentOrder::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity(), (left, right) -> left));
    }

    private AdminPaymentOrderListItemDTO toListItem(PaymentOrder order, CapabilityProduct product, User user) {
        AdminPaymentOrderListItemDTO dto = new AdminPaymentOrderListItemDTO();
        dto.setPaymentOrderId(order.getPaymentOrderId());
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setPhone(user == null ? null : user.getPhone());
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

    private AdminPaymentOrderDetailDTO.OrderInfo toOrderInfo(PaymentOrder order, User user) {
        AdminPaymentOrderDetailDTO.OrderInfo info = new AdminPaymentOrderDetailDTO.OrderInfo();
        info.setPaymentOrderId(order.getPaymentOrderId());
        info.setOrderNo(order.getOrderNo());
        info.setUserId(order.getUserId());
        info.setPhone(user == null ? null : user.getPhone());
        info.setBizType(order.getBizType());
        info.setBizRefId(order.getBizRefId());
        info.setProductId(order.getProductId());
        info.setAmount(order.getAmount());
        info.setCurrencyCode(order.getCurrencyCode());
        info.setPayStatus(order.getPayStatus());
        info.setPayChannel(order.getPayChannel());
        info.setCreateTime(order.getCreateTime());
        info.setPaidAt(order.getPaidAt());
        info.setClosedAt(order.getClosedAt());
        info.setLastUpdate(order.getLastUpdate());
        return info;
    }

    private AdminPaymentOrderDetailDTO.ProductInfo toProductInfo(PaymentOrder order, CapabilityProduct product) {
        AdminPaymentOrderDetailDTO.ProductInfo info = new AdminPaymentOrderDetailDTO.ProductInfo();
        info.setProductId(order.getProductId());
        if (product != null) {
            info.setProductCode(product.getProductCode());
            info.setProductName(product.getProductName());
            info.setDurationDays(product.getDurationDays());
        }
        return info;
    }

    private AdminPaymentOrderDetailDTO.PaymentInfo toPaymentInfo(List<AdminPaymentTransactionListItemDTO> transactionItems) {
        AdminPaymentOrderDetailDTO.PaymentInfo info = new AdminPaymentOrderDetailDTO.PaymentInfo();
        info.setTransactionCount(transactionItems.size());
        info.setTransactions(transactionItems);
        return info;
    }

    private AdminPaymentOrderDetailDTO.RefundSummary toRefundSummary(List<RefundOrder> refundOrders) {
        AdminPaymentOrderDetailDTO.RefundSummary summary = new AdminPaymentOrderDetailDTO.RefundSummary();
        summary.setTotalRefundCount(refundOrders.size());
        summary.setTotalRefundAmount(refundOrders.stream()
                .map(RefundOrder::getRefundAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (!refundOrders.isEmpty()) {
            RefundOrder latest = refundOrders.get(0);
            summary.setLatestRefundOrderId(latest.getRefundOrderId());
            summary.setLatestRefundNo(latest.getRefundNo());
            summary.setLatestAuditStatus(latest.getAuditStatus());
            summary.setLatestRefundStatus(latest.getRefundStatus());
            summary.setLatestAuditedAt(latest.getAuditedAt());
            summary.setLatestRefundedAt(latest.getRefundedAt());
        }
        return summary;
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

    private LocalDateTime firstNonNull(LocalDateTime primary, LocalDateTime secondary) {
        return primary != null ? primary : secondary;
    }
}
