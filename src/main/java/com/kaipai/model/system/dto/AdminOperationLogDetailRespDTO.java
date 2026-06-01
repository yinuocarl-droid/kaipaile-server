package com.kaipai.model.system.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminOperationLogDetailRespDTO {

    private Long operationLogId;
    private Long adminUserId;
    private String adminUserName;
    private String moduleCode;
    private String operationCode;
    private String targetType;
    private Long targetId;
    private String requestId;
    private String clientIp;
    private String userAgent;
    private String beforeSnapshotJson;
    private String afterSnapshotJson;
    private Integer operationResult;
    private String failReason;
    private String extraContextJson;
    private String confirmToken;
    private LocalDateTime confirmedAt;
    private String createUserName;
    private LocalDateTime createTime;
}


