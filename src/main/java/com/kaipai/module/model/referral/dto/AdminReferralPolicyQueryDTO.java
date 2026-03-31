package com.kaipai.module.model.referral.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminReferralPolicyQueryDTO {

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    private long pageSize = 20;

    private String policyName;
    private Integer enabled;
}
