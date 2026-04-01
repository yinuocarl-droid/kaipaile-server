package com.kaipai.module.model.verify.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IdentityVerificationStatusRespDTO {

    /** 0 unsubmitted, 1 pending, 2 approved, 3 rejected */
    private Integer status;

    private String realName;

    private String idCardNo;

    private String rejectReason;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;
}
