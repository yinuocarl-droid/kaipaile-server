package com.kaipai.model.ai.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProfileImportPromptVersionSummaryRespDTO {
    private Long promptVersionId;
    private Long templateId;
    private Integer versionNo;
    private String versionLabel;
    private String lifecycleStatus;
    private String contentSha256;
    private String testStatus;
    private String testedModelName;
    private String testErrorCode;
    private Integer testCandidateCount;
    private Integer testWorkCount;
    private Long testedBy;
    private LocalDateTime testedAt;
    private Long releasedBy;
    private LocalDateTime releasedAt;
    private Long updateUserId;
    private String updateUserName;
    private LocalDateTime lastUpdate;
    private Integer version;
}
