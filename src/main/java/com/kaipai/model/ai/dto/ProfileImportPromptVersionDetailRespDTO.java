package com.kaipai.model.ai.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProfileImportPromptVersionDetailRespDTO
        extends ProfileImportPromptVersionSummaryRespDTO {
    private String systemPromptBody;
    private String repairPromptBody;
    private String schemaVersion;
    private String contractVersion;
    private String changeSummary;
}
