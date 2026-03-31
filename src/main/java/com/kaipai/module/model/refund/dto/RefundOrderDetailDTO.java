package com.kaipai.module.model.refund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RefundOrderDetailDTO {

    private RefundInfo refundInfo;
    private PaymentOrderInfo paymentOrderInfo;
    private List<RefundOperateLogItemDTO> operateLogs;

    @Data
    public static class RefundInfo {
        private Long refundOrderId;
        private String refundNo;
        private Long paymentOrderId;
        private Long userId;
        private BigDecimal refundAmount;
        private String refundReason;
        private Integer auditStatus;
        private Integer refundStatus;
        private String auditRemark;
        private Long auditorId;
        private LocalDateTime createTime;
        private LocalDateTime auditedAt;
        private String channelRefundNo;
        private LocalDateTime refundedAt;
    }

    @Data
    public static class PaymentOrderInfo {
        private Long paymentOrderId;
        private String paymentOrderNo;
        private BigDecimal paymentAmount;
        private Integer paymentStatus;
        private String payChannel;
        private LocalDateTime paidAt;
    }
}
