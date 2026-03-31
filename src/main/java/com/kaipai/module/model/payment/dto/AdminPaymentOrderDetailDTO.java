package com.kaipai.module.model.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminPaymentOrderDetailDTO {

    private Long paymentOrderId;

    private String orderNo;

    private Long userId;

    private String bizType;

    private Long bizRefId;

    private Long productId;

    private String productCode;

    private String productName;

    private Integer membershipTier;

    private Integer durationDays;

    private BigDecimal amount;

    private String currencyCode;

    private Integer payStatus;

    private String payChannel;

    private LocalDateTime createTime;

    private LocalDateTime paidAt;

    private LocalDateTime closedAt;

    private LocalDateTime lastUpdate;

    private Integer transactionCount;

    private List<AdminPaymentTransactionListItemDTO> transactions;
}
