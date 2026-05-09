package com.kaipai.module.model.capability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Audit trail for capability adjustments.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("capability_change_log")
public class CapabilityChangeLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long changeLogId;
    private Long userId;
    private Integer beforeTier;
    private Integer afterTier;
    private String changeReason;
    private String sourceType;
    private Long sourceRefId;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String remark;
}
