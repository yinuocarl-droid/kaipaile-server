package com.kaipai.model.capability.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminCapabilityAccountItemDTO {

    private Long capabilityId;
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
