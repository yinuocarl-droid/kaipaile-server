package com.kaipai.module.model.referral.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminReferralPolicyDetailDTO {

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
    private String updateUserName;
    private LocalDateTime lastUpdate;
    private String versionRemark;
}
