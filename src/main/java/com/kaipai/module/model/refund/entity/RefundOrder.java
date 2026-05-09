package com.kaipai.module.model.refund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Refund order records for capability purchases.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("refund_order")
public class RefundOrder extends BaseEntity {

    @TableId(type = IdType.AUTO)
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
    private LocalDateTime auditedAt;
    private String channelRefundNo;
    private LocalDateTime refundedAt;
}
