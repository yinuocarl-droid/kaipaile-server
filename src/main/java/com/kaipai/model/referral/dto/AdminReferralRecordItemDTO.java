package com.kaipai.model.referral.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminReferralRecordItemDTO {

    private Long referralId;
    private Long inviterUserId;
    private String inviterName;
    private String inviteCode;
    private Long inviteeUserId;
    private String inviteeName;
    private Integer status;
    private Integer riskFlag;
    private LocalDateTime registeredAt;
    private LocalDateTime validatedAt;
}
