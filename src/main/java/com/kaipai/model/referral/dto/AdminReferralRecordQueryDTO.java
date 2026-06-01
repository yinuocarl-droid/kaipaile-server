package com.kaipai.model.referral.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminReferralRecordQueryDTO {

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    private long pageSize = 20;

    private Long inviterUserId;
    private String inviteCode;
    private Long inviteeUserId;
    private Integer status;
    private Integer riskFlag;
    private LocalDateTime registeredAtFrom;
    private LocalDateTime registeredAtTo;
    private LocalDateTime validatedAtFrom;
    private LocalDateTime validatedAtTo;
}
