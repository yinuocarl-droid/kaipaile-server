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
 * Capability payment orders.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_order")
public class PaymentOrder extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long paymentOrderId;
    private String orderNo;
    private Long userId;
    private String bizType;
    private Long bizRefId;
    private Long productId;
    private BigDecimal amount;
    private String currencyCode;
    private Integer payStatus;
    private String payChannel;
    private LocalDateTime paidAt;
    private LocalDateTime closedAt;
}
