package com.kaipai.module.model.referral.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminReferralRiskQueryDTO {

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    private long pageSize = 20;

    private String inviteCode;
    private Long inviterUserId;
    private Long inviteeUserId;
    private String riskReason;
    private Integer status;
    private Integer riskFlag = 1;
    private LocalDateTime registeredAtFrom;
    private LocalDateTime registeredAtTo;
}
