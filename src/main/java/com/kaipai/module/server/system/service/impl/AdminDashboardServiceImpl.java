package com.kaipai.module.server.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import com.kaipai.module.model.referral.entity.ReferralRecord;
import com.kaipai.module.model.refund.entity.RefundOrder;
import com.kaipai.module.model.system.dto.AdminDashboardOverviewDTO;
import com.kaipai.module.model.system.dto.AdminDashboardOverviewQueryDTO;
import com.kaipai.module.model.verify.entity.IdentityVerification;
import com.kaipai.module.server.payment.mapper.PaymentOrderMapper;
import com.kaipai.module.server.referral.mapper.ReferralRecordMapper;
import com.kaipai.module.server.refund.mapper.RefundOrderMapper;
import com.kaipai.module.server.system.service.AdminDashboardService;
import com.kaipai.module.server.verify.mapper.IdentityVerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final IdentityVerificationMapper identityVerificationMapper;
    private final ReferralRecordMapper referralRecordMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final PaymentOrderMapper paymentOrderMapper;

    @Override
    public AdminDashboardOverviewDTO overview(AdminDashboardOverviewQueryDTO query) {
        LocalDateTime dateFrom = query.getDateFrom();
        LocalDateTime dateTo = query.getDateTo();
        LocalDateTime todayFrom = LocalDate.now().atStartOfDay();
        LocalDateTime todayTo = todayFrom.plusDays(1);

        AdminDashboardOverviewDTO dto = new AdminDashboardOverviewDTO();
        dto.setVerifyPendingCount(identityVerificationMapper.selectCount(buildVerifyPendingWrapper(dateFrom, dateTo)));
        dto.setReferralRiskPendingCount(referralRecordMapper.selectCount(buildReferralRiskWrapper(dateFrom, dateTo)));
        dto.setRefundPendingCount(refundOrderMapper.selectCount(buildRefundPendingWrapper(dateFrom, dateTo)));
        dto.setTodayPaymentOrderCount(paymentOrderMapper.selectCount(buildPaymentTodayWrapper(
                dateFrom == null ? todayFrom : dateFrom,
                dateTo == null ? todayTo : dateTo)));
        dto.setRecentItems(buildRecentItems(query, dateFrom, dateTo, todayFrom, todayTo));
        return dto;
    }

    private List<AdminDashboardOverviewDTO.RecentItem> buildRecentItems(AdminDashboardOverviewQueryDTO query,
                                                                        LocalDateTime dateFrom,
                                                                        LocalDateTime dateTo,
                                                                        LocalDateTime todayFrom,
                                                                        LocalDateTime todayTo) {
        List<AdminDashboardOverviewDTO.RecentItem> items = new ArrayList<>();
        if (matchesBizLine(query.getBizLine(), "verify")) {
            items.addAll(identityVerificationMapper.selectList(buildVerifyPendingWrapper(dateFrom, dateTo).last("limit 5"))
                    .stream()
                    .map(this::toVerifyItem)
                    .toList());
        }
        if (matchesBizLine(query.getBizLine(), "referral")) {
            items.addAll(referralRecordMapper.selectList(buildReferralRiskWrapper(dateFrom, dateTo).last("limit 5"))
                    .stream()
                    .map(this::toReferralItem)
                    .toList());
        }
        if (matchesBizLine(query.getBizLine(), "refund")) {
            items.addAll(refundOrderMapper.selectList(buildRefundPendingWrapper(dateFrom, dateTo).last("limit 5"))
                    .stream()
                    .map(this::toRefundItem)
                    .toList());
        }
        if (matchesBizLine(query.getBizLine(), "payment")) {
            items.addAll(paymentOrderMapper.selectList(buildPaymentTodayWrapper(
                            dateFrom == null ? todayFrom : dateFrom,
                            dateTo == null ? todayTo : dateTo)
                            .last("limit 5"))
                    .stream()
                    .map(this::toPaymentItem)
                    .toList());
        }
        return items.stream()
                .sorted(Comparator.comparing(AdminDashboardOverviewDTO.RecentItem::getOccurredAt,
                        Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .limit(10)
                .toList();
    }

    private LambdaQueryWrapper<IdentityVerification> buildVerifyPendingWrapper(LocalDateTime dateFrom, LocalDateTime dateTo) {
        LambdaQueryWrapper<IdentityVerification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IdentityVerification::getStatus, 1);
        if (dateFrom != null) {
            wrapper.ge(IdentityVerification::getCreateTime, dateFrom);
        }
        if (dateTo != null) {
            wrapper.le(IdentityVerification::getCreateTime, dateTo);
        }
        return wrapper.orderByDesc(IdentityVerification::getCreateTime).orderByDesc(IdentityVerification::getVerificationId);
    }

    private LambdaQueryWrapper<ReferralRecord> buildReferralRiskWrapper(LocalDateTime dateFrom, LocalDateTime dateTo) {
        LambdaQueryWrapper<ReferralRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReferralRecord::getRiskFlag, 1).eq(ReferralRecord::getStatus, 3);
        if (dateFrom != null) {
            wrapper.ge(ReferralRecord::getRegisteredAt, dateFrom);
        }
        if (dateTo != null) {
            wrapper.le(ReferralRecord::getRegisteredAt, dateTo);
        }
        return wrapper.orderByDesc(ReferralRecord::getRegisteredAt).orderByDesc(ReferralRecord::getReferralId);
    }

    private LambdaQueryWrapper<RefundOrder> buildRefundPendingWrapper(LocalDateTime dateFrom, LocalDateTime dateTo) {
        LambdaQueryWrapper<RefundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundOrder::getAuditStatus, 0);
        if (dateFrom != null) {
            wrapper.ge(RefundOrder::getCreateTime, dateFrom);
        }
        if (dateTo != null) {
            wrapper.le(RefundOrder::getCreateTime, dateTo);
        }
        return wrapper.orderByDesc(RefundOrder::getCreateTime).orderByDesc(RefundOrder::getRefundOrderId);
    }

    private LambdaQueryWrapper<PaymentOrder> buildPaymentTodayWrapper(LocalDateTime dateFrom, LocalDateTime dateTo) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<>();
        if (dateFrom != null) {
            wrapper.ge(PaymentOrder::getCreateTime, dateFrom);
        }
        if (dateTo != null) {
            wrapper.lt(PaymentOrder::getCreateTime, dateTo);
        }
        return wrapper.orderByDesc(PaymentOrder::getCreateTime).orderByDesc(PaymentOrder::getPaymentOrderId);
    }

    private AdminDashboardOverviewDTO.RecentItem toVerifyItem(IdentityVerification record) {
        AdminDashboardOverviewDTO.RecentItem item = new AdminDashboardOverviewDTO.RecentItem();
        item.setBizLine("verify");
        item.setItemType("identity_verification");
        item.setItemId(record.getVerificationId());
        item.setUserId(record.getUserId());
        item.setReferenceNo(String.valueOf(record.getVerificationId()));
        item.setTitle("实名认证待审核");
        item.setStatus(record.getStatus());
        item.setOccurredAt(record.getCreateTime());
        return item;
    }

    private AdminDashboardOverviewDTO.RecentItem toReferralItem(ReferralRecord record) {
        AdminDashboardOverviewDTO.RecentItem item = new AdminDashboardOverviewDTO.RecentItem();
        item.setBizLine("referral");
        item.setItemType("referral_risk");
        item.setItemId(record.getReferralId());
        item.setUserId(record.getInviteeUserId());
        item.setReferenceNo(record.getInviteCodeSnapshot());
        item.setTitle("异常邀请待处理");
        item.setStatus(record.getStatus());
        item.setOccurredAt(record.getRegisteredAt());
        return item;
    }

    private AdminDashboardOverviewDTO.RecentItem toRefundItem(RefundOrder order) {
        AdminDashboardOverviewDTO.RecentItem item = new AdminDashboardOverviewDTO.RecentItem();
        item.setBizLine("refund");
        item.setItemType("refund_order");
        item.setItemId(order.getRefundOrderId());
        item.setUserId(order.getUserId());
        item.setReferenceNo(order.getRefundNo());
        item.setTitle("退款待审核");
        item.setStatus(order.getAuditStatus());
        item.setOccurredAt(order.getCreateTime());
        return item;
    }

    private AdminDashboardOverviewDTO.RecentItem toPaymentItem(PaymentOrder order) {
        AdminDashboardOverviewDTO.RecentItem item = new AdminDashboardOverviewDTO.RecentItem();
        item.setBizLine("payment");
        item.setItemType("payment_order");
        item.setItemId(order.getPaymentOrderId());
        item.setUserId(order.getUserId());
        item.setReferenceNo(order.getOrderNo());
        item.setTitle("今日支付订单");
        item.setStatus(order.getPayStatus());
        item.setOccurredAt(order.getCreateTime());
        return item;
    }

    private boolean matchesBizLine(String bizLine, String expected) {
        return !StringUtils.hasText(bizLine) || Objects.equals(bizLine.trim(), expected);
    }
}
