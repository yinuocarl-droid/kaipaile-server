package com.kaipai.model.ai.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProfileImportPromptCreateDraftReqDTO extends ProfileImportPromptStrictWriteDTO {
    private Long sourceVersionId;
    private Integer expectedTemplateVersion;
}
