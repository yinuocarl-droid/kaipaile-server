package com.kaipai.model.ai.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProfileImportPromptVersionActionReqDTO extends ProfileImportPromptStrictWriteDTO {
    private String reasonCode;
    private Integer expectedTemplateVersion;
    private Integer expectedVersion;
}
