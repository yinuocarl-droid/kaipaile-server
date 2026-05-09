package com.kaipai.module.model.capability.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CapabilityChangeLogItemDTO {

    private Long changeLogId;
    private Long userId;
    private String nickname;
    private String phone;
    private Integer beforeTier;
    private Integer afterTier;
    private String changeReason;
    private String sourceType;
    private Long sourceRefId;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String remark;
    private LocalDateTime createTime;
}
