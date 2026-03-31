package com.kaipai.module.model.refund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RefundOrderDetailDTO {

    private Long refundOrderId;
    private String refundNo;
    private Long paymentOrderId;
    private String paymentOrderNo;
    private Long userId;
    private BigDecimal refundAmount;
    private String refundReason;
    private Integer auditStatus;
    private Integer refundStatus;
    private String auditRemark;
    private Long auditorId;
    private LocalDateTime auditedAt;
    private String channelRefundNo;
    private LocalDateTime refundedAt;
    private BigDecimal paymentAmount;
    private Integer paymentStatus;
    private String payChannel;
    private LocalDateTime paidAt;
    private List<RefundOperateLogItemDTO> operateLogs;
}
