package com.kaipai.model.ai.dto;

import lombok.Data;

@Data
public class ProfileImportCapabilityRespDTO {
    private boolean enabled;
    private boolean available;
    private String providerCode;
    private String modelName;
    private Integer maxInputLength;
    private String unavailableReason;

    public ProfileImportCapabilityRespDTO(
            boolean enabled,
            boolean available,
            String providerCode,
            String modelName,
            Integer maxInputLength,
            String unavailableReason) {
        this.enabled = enabled;
        this.available = available;
        this.providerCode = providerCode;
        this.modelName = modelName;
        this.maxInputLength = maxInputLength;
        this.unavailableReason = unavailableReason;
    }
}
