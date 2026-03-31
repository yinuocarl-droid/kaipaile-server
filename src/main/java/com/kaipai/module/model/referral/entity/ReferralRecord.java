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
@TableName("referral_record")
public class ReferralRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long referralId;

    private Long inviterUserId;

    private Long inviteeUserId;

    private Long inviteCodeId;

    private String inviteCodeSnapshot;

    private String registerDeviceFingerprint;

    private Integer status;

    private Integer riskFlag;

    private String riskReason;

    private LocalDateTime registeredAt;

    private LocalDateTime validatedAt;
}
