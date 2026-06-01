package com.kaipai.model.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment transaction records.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_transaction")
public class PaymentTransaction extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long transactionId;
    private Long paymentOrderId;
    private String channelTradeNo;
    private String channel;
    private String tradeType;
    private BigDecimal amount;
    private Integer status;
    private String callbackPayload;
    private LocalDateTime callbackTime;
}
