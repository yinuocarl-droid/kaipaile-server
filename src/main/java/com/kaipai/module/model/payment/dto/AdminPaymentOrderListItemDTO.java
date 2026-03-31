package com.kaipai.module.model.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminPaymentOrderListItemDTO {

    private Long paymentOrderId;

    private String orderNo;

    private Long userId;

    private String phone;

    private String bizType;

    private Long bizRefId;

    private Long productId;

    private String productCode;

    private String productName;

    private BigDecimal amount;

    private String currencyCode;

    private Integer payStatus;

    private String payChannel;

    private LocalDateTime createTime;

    private LocalDateTime paidAt;

    private LocalDateTime closedAt;
}
