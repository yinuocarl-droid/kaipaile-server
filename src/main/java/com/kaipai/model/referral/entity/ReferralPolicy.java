package com.kaipai.model.referral.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("referral_policy")
public class ReferralPolicy extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long policyId;

    private String policyName;

    private Integer enabled;

    private Integer requireRealAuth;

    private Integer requireProfileCompletion;

    private Integer profileCompletionThreshold;

    private Integer sameDeviceLimit;

    private Integer hourlyInviteLimit;

    private Integer autoGrantEnabled;

    private String grantRuleJson;
}
