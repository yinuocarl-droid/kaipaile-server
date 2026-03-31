package com.kaipai.module.model.referral.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserEntitlementGrantGrantRequestDTO {

    private Long userId;
    private String grantType;
    private String grantCode;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String sourceType;
    private Long sourceRefId;
    private String remark;
}
