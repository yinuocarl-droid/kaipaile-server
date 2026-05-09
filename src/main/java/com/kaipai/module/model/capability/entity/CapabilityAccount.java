package com.kaipai.module.model.capability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * User capability account.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("capability_account")
public class CapabilityAccount extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long capabilityId;
    private Long userId;
    private Integer tier;
    private Integer status;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String sourceType;
    private Long sourceRefId;
}
