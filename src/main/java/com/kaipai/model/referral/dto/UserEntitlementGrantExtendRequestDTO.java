package com.kaipai.model.referral.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserEntitlementGrantExtendRequestDTO {

    private Long grantId;
    private LocalDateTime expireTime;
    private String remark;
}
