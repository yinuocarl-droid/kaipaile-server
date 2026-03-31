package com.kaipai.module.model.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAdminListItemDTO {

    private Long userId;
    private String nickname;
    private String phone;
    private Integer userType;
    private String role;
    private Integer realAuthStatus;
    private Integer membershipTier;
    private Integer membershipStatus;
    private Integer referralStatus;
    private Integer validInviteCount;
    private UserAdminEntitlementSummaryDTO entitlementSummary;
    private LocalDateTime registeredAt;
    private LocalDateTime lastActiveAt;
}
