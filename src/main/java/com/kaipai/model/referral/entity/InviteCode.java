package com.kaipai.model.referral.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("invite_code")
public class InviteCode extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long inviteCodeId;

    private Long userId;

    private String code;

    /** 1 active, 2 disabled */
    private Integer status;
}
