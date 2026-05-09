package com.kaipai.module.model.system.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminOperationLogListItemDTO {

    private Long operationLogId;
    private Long adminUserId;
    private String adminUserName;
    private String moduleCode;
    private String operationCode;
    private String targetType;
    private Long targetId;
    private String requestId;
    private Integer operationResult;
    private String failReason;
    private String clientIp;
    private LocalDateTime confirmedAt;
    private LocalDateTime createTime;
}


