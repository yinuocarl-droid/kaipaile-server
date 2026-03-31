package com.kaipai.module.model.membership.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Audit trail for membership adjustments.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("membership_change_log")
public class MembershipChangeLog extends BaseEntity {

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
