package com.kaipai.model.card.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplatePublishLogItemDTO {

    private Long publishLogId;
    private Long templateId;
    private String targetType;
    private String targetCode;
    private String publishVersion;
    private String draftVersion;
    private String sourceVersion;
    private String targetVersion;
    private String actionType;
    private Long publishedBy;
    private String publishNote;
    private String diffSummaryJson;
    private String snapshotJson;
    private LocalDateTime publishedAt;
}



