package com.kaipai.module.model.referral.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_entitlement_grant")
public class UserEntitlementGrant extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long grantId;

    private Long userId;

    private String grantType;

    private String grantCode;

    private Integer status;

    private LocalDateTime effectiveTime;

    private LocalDateTime expireTime;

    private String sourceType;

    private Long sourceRefId;

    private String remark;
}
