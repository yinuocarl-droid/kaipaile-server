package com.kaipai.model.refund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundOrderRespDTO {

    private Long refundOrderId;
    private String refundNo;
    private Long paymentOrderId;
    private String paymentOrderNo;
    private Long userId;
    private String phone;
    private BigDecimal refundAmount;
    private Integer auditStatus;
    private Integer refundStatus;
    private String refundReason;
    private String auditRemark;
    private LocalDateTime createTime;
    private LocalDateTime auditedAt;
    private String channelRefundNo;
    private LocalDateTime refundedAt;
}
