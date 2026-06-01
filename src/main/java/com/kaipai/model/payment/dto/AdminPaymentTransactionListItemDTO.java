package com.kaipai.model.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminPaymentTransactionListItemDTO {

    private Long transactionId;

    private Long paymentOrderId;

    private String paymentOrderNo;

    private String channelTradeNo;

    private String channel;

    private String tradeType;

    private BigDecimal amount;

    private Integer status;

    private LocalDateTime callbackTime;

    private LocalDateTime createTime;
}
