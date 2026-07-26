package com.kaipai.model.ai.dto;

import lombok.Data;

@Data
public class ProfileImportPromptTemplateSummaryRespDTO {
    private Long templateId;
    private String templateCode;
    private String scene;
    private String displayName;
    private Long activeVersionId;
    private Integer activeVersionNo;
    private String activeVersionLabel;
    private String activeContentSha256;
    private String activeTestStatus;
    private Long draftVersionId;
    private Integer draftVersionNo;
    private String draftVersionLabel;
    private String draftContentSha256;
    private String draftTestStatus;
    private Integer version;
}
