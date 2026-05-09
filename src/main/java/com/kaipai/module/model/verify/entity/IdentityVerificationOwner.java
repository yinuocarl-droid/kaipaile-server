package com.kaipai.module.model.verify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_verification_owner")
public class IdentityVerificationOwner extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long ownerId;

    private String idCardHash;

    private Long userId;
}
