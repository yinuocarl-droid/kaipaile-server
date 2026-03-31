package com.kaipai.module.model.membership.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminMembershipAccountItemDTO {

    private Long membershipId;
    private Long userId;
    private String nickname;
    private String phone;
    private Integer tier;
    private Integer status;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String sourceType;
    private Long sourceRefId;
    private LocalDateTime recentChangeTime;
}
