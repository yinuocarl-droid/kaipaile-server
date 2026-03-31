package com.kaipai.module.model.referral.dto;

import lombok.Data;

@Data
public class UserEntitlementGrantListQueryDTO {

    private Integer pageNo = 1;
    private Integer pageSize = 20;

    private Long userId;
    private String grantType;
    private String grantCode;
    private Integer status;
    private String sourceType;
}
