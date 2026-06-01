package com.kaipai.model.ai.dto;

import lombok.Data;

@Data
public class AdminAiResumeQuotaUserDTO {

    private Long userId;

    private String userName;

    private String phone;

    private Integer realAuthStatus;

    private Integer level;

    private Integer totalQuota;

    private Integer usedCount;
}
