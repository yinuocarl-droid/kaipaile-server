package com.kaipai.module.model.referral.dto;

import lombok.Data;

@Data
public class AdminReferralPolicySaveDTO {

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
