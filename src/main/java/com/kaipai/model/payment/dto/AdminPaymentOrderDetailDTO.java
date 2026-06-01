package com.kaipai.model.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminPaymentOrderDetailDTO {

    private OrderInfo orderInfo;

    private ProductInfo productInfo;

    private PaymentInfo paymentInfo;

    private RefundSummary refundSummary;

    @Data
    public static class OrderInfo {
        private Long paymentOrderId;
        private String orderNo;
        private Long userId;
        private String phone;
        private String bizType;
        private Long bizRefId;
        private Long productId;
        private BigDecimal amount;
        private String currencyCode;
        private Integer payStatus;
        private String payChannel;
        private LocalDateTime createTime;
        private LocalDateTime paidAt;
        private LocalDateTime closedAt;
        private LocalDateTime lastUpdate;
    }

    @Data
    public static class ProductInfo {
        private Long productId;
        private String productCode;
        private String productName;
        private Integer durationDays;
    }

    @Data
    public static class PaymentInfo {
        private Integer transactionCount;
        private List<AdminPaymentTransactionListItemDTO> transactions;
    }

    @Data
    public static class RefundSummary {
        private Integer totalRefundCount;
        private BigDecimal totalRefundAmount;
        private Long latestRefundOrderId;
        private String latestRefundNo;
        private Integer latestAuditStatus;
        private Integer latestRefundStatus;
        private LocalDateTime latestAuditedAt;
        private LocalDateTime latestRefundedAt;
    }
}
