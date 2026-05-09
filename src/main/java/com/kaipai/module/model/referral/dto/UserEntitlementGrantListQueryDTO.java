package com.kaipai.module.model.referral.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserEntitlementGrantListQueryDTO {

    @Min(1)
    private Integer pageNo = 1;
    @Min(1)
    private Integer pageSize = 20;

    private Long userId;
    private String phone;
    private String grantType;
    private String grantCode;
    private Integer status;
    private String sourceType;
    private Long sourceRefId;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private LocalDateTime expireFrom;
    private LocalDateTime expireTo;
}
