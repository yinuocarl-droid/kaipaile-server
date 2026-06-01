package com.kaipai.model.referral.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminReferralRiskItemDTO {

    private Long referralId;
    private String inviteCode;
    private Long inviterUserId;
    private String inviterName;
    private Long inviteeUserId;
    private String inviteeName;
    private String riskReason;
    private Integer status;
    private Integer riskFlag;
    private LocalDateTime registeredAt;
}
