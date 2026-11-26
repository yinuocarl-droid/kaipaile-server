package com.kaipai.model.ai.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProfileImportPromptAuditRespDTO {
    private Long promptAuditId;
    private Long templateId;
    private Long promptVersionId;
    private String actionCode;
    private Long fromVersionId;
    private Long toVersionId;
    private String contentSha256;
    private String runtimeSha256;
    private String schemaVersion;
    private String contractVersion;
    private String fixtureCode;
    private String fixtureVersion;
    private String fixtureSha256;
    private String modelName;
    private Integer configVersion;
    private Long testOperatorId;
    private LocalDateTime testedAt;
    private Long operatorId;
    private String operatorName;
    private String reasonCode;
    private String resultStatus;
    private String errorCode;
    private String message;
    private LocalDateTime createTime;
}
