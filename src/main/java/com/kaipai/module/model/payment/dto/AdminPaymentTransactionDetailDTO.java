package com.kaipai.module.model.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminPaymentTransactionDetailDTO {

    private Long transactionId;

    private Long paymentOrderId;

    private String paymentOrderNo;

    private Long userId;

    private String bizType;

    private Long bizRefId;

    private Long productId;

    private String productCode;

    private String productName;

    private String payChannel;

    private Integer payStatus;

    private BigDecimal orderAmount;

    private String currencyCode;

    private LocalDateTime paidAt;

    private String channelTradeNo;

    private String channel;

    private String tradeType;

    private BigDecimal amount;

    private Integer status;

    private String callbackPayload;

    private LocalDateTime callbackTime;

    private LocalDateTime createTime;

    private LocalDateTime lastUpdate;
}
