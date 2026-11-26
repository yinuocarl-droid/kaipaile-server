package com.kaipai.model.ai.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProfileImportPromptUpdateDraftReqDTO extends ProfileImportPromptStrictWriteDTO {
    private String versionLabel;
    private String systemPromptBody;
    private String repairPromptBody;
    private String changeSummary;
    private Integer expectedVersion;
}
