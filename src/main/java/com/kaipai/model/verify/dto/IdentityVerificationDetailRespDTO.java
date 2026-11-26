package com.kaipai.model.verify.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IdentityVerificationDetailRespDTO {

    private Long verificationId;
    private Long userId;
    private String userName;
    private String phone;
    private String realName;
    private String idCardNoMasked;
    private Integer status;
    private String rejectReason;
    private LocalDateTime submitTime;
    private LocalDateTime reviewedAt;
    private Boolean actorCertified;
    private String providerResultCode;
    private String providerRequestId;
    private String providerCode;
    private String providerResultMessage;
    private LocalDateTime providerVerifiedAt;
}
