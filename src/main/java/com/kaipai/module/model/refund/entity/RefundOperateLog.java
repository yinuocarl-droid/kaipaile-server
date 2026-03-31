package com.kaipai.module.model.refund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Refund operation log entries.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("refund_operate_log")
public class RefundOperateLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long logId;
    private Long refundOrderId;
    private Long operatorId;
    private String actionType;
    private String remark;
}
