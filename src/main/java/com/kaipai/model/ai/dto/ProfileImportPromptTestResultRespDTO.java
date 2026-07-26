package com.kaipai.model.ai.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProfileImportPromptTestResultRespDTO {
    private Long promptVersionId;
    private String contentSha256;
    private String runtimeSha256;
    private String fixtureCode;
    private String fixtureVersion;
    private String fixtureSha256;
    private String modelName;
    private Integer configVersion;
    private String status;
    private Integer candidateCount;
    private Integer workCount;
    private Long elapsedMs;
    private String errorCode;
    private Long testedBy;
    private LocalDateTime testedAt;
}
