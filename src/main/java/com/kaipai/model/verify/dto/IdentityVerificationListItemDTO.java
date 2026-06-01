package com.kaipai.model.verify.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IdentityVerificationListItemDTO {

    private Long verificationId;
    private Long userId;
    private String userName;
    private String phone;
    private String realName;
    private Integer status;
    private LocalDateTime submitTime;
    private String verifyProvider;
    private String providerResultCode;
}
