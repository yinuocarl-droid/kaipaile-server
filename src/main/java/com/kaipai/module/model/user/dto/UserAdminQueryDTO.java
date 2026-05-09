package com.kaipai.module.model.user.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserAdminQueryDTO {

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    private long pageSize = 20;

    private Long userId;
    private String phone;
    private String nickname;
    private Integer role;
    private Integer userType;
    private Integer realAuthStatus;
    private Integer referralStatus;
    private Integer entitlementStatus;
}
