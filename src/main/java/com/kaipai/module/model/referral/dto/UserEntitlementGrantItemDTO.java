package com.kaipai.module.model.referral.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntitlementGrantItemDTO {

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
