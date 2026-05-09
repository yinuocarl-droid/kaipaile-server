package com.kaipai.module.server.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.capability.entity.CapabilityProduct;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionDetailDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionListItemDTO;
import com.kaipai.module.model.payment.dto.AdminPaymentTransactionQueryDTO;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import com.kaipai.module.model.payment.entity.PaymentTransaction;
import com.kaipai.module.server.capability.mapper.CapabilityProductMapper;
import com.kaipai.module.server.payment.mapper.PaymentOrderMapper;
import com.kaipai.module.server.payment.mapper.PaymentTransactionMapper;
import com.kaipai.module.server.payment.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
    private final CapabilityProductMapper capabilityProductMapper;

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
        CapabilityProduct product = order == null || order.getProductId() == null
                ? null
                : capabilityProductMapper.selectById(order.getProductId());
        AdminPaymentTransactionDetailDTO dto = new AdminPaymentTransactionDetailDTO();
        dto.setTransactionInfo(toTransactionInfo(transaction, order, product));
        dto.setCallbackPayloadSummary(toCallbackPayloadSummary(transaction));
        return dto;
    }

    private LambdaQueryWrapper<PaymentTransaction> buildTransactionQuery(AdminPaymentTransactionQueryDTO query) {
        LambdaQueryWrapper<PaymentTransaction> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getChannelTradeNo())) {
            wrapper.like(PaymentTransaction::getChannelTradeNo, query.getChannelTradeNo().trim());
        }
        if (StringUtils.hasText(query.getChannel())) {
            wrapper.eq(PaymentTransaction::getChannel, query.getChannel().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(PaymentTransaction::getStatus, query.getStatus());
        }
        LocalDateTime callbackFrom = firstNonNull(query.getCallbackFrom(), query.getCallbackTimeFrom());
        LocalDateTime callbackTo = firstNonNull(query.getCallbackTo(), query.getCallbackTimeTo());
        if (callbackFrom != null) {
            wrapper.ge(PaymentTransaction::getCallbackTime, callbackFrom);
        }
        if (callbackTo != null) {
            wrapper.le(PaymentTransaction::getCallbackTime, callbackTo);
        }
        if (StringUtils.hasText(query.getPaymentOrderNo())) {
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

    private AdminPaymentTransactionDetailDTO.TransactionInfo toTransactionInfo(PaymentTransaction transaction,
                                                                               PaymentOrder order,
                                                                               CapabilityProduct product) {
        AdminPaymentTransactionDetailDTO.TransactionInfo info = new AdminPaymentTransactionDetailDTO.TransactionInfo();
        info.setTransactionId(transaction.getTransactionId());
        info.setPaymentOrderId(transaction.getPaymentOrderId());
        info.setChannelTradeNo(transaction.getChannelTradeNo());
        info.setChannel(transaction.getChannel());
        info.setTradeType(transaction.getTradeType());
        info.setAmount(transaction.getAmount());
        info.setStatus(transaction.getStatus());
        info.setCallbackTime(transaction.getCallbackTime());
        info.setCreateTime(transaction.getCreateTime());
        info.setLastUpdate(transaction.getLastUpdate());
        if (order != null) {
            info.setPaymentOrderNo(order.getOrderNo());
            info.setUserId(order.getUserId());
            info.setBizType(order.getBizType());
            info.setBizRefId(order.getBizRefId());
            info.setProductId(order.getProductId());
            info.setPayChannel(order.getPayChannel());
            info.setPayStatus(order.getPayStatus());
            info.setOrderAmount(order.getAmount());
            info.setCurrencyCode(order.getCurrencyCode());
            info.setPaidAt(order.getPaidAt());
        }
        if (product != null) {
            info.setProductCode(product.getProductCode());
            info.setProductName(product.getProductName());
        }
        return info;
    }

    private AdminPaymentTransactionDetailDTO.CallbackPayloadSummary toCallbackPayloadSummary(PaymentTransaction transaction) {
        AdminPaymentTransactionDetailDTO.CallbackPayloadSummary summary = new AdminPaymentTransactionDetailDTO.CallbackPayloadSummary();
        String payload = transaction.getCallbackPayload();
        summary.setHasPayload(StringUtils.hasText(payload));
        summary.setPayloadLength(payload == null ? 0 : payload.length());
        summary.setPayloadPreview(buildPayloadPreview(payload));
        summary.setCallbackTime(transaction.getCallbackTime());
        return summary;
    }

    private String buildPayloadPreview(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        String normalized = payload.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private LocalDateTime firstNonNull(LocalDateTime primary, LocalDateTime secondary) {
        return primary != null ? primary : secondary;
    }
}
