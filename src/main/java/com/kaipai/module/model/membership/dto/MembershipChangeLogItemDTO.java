package com.kaipai.module.model.membership.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MembershipChangeLogItemDTO {

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
