package com.kaipai.model.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AdminAiImageProviderDTO {

    private Long configId;

    private String providerCode;

    private String displayName;

    private Boolean enabled;

    private Boolean active;

    private Integer priority;

    private AiImageProviderPublicConfigDTO publicConfig;

    private Boolean secretConfigured;

    private Map<String, String> secretMask;

    private Long secretUpdatedBy;

    private String secretUpdatedByName;

    private LocalDateTime secretUpdatedAt;

    private String lastTestStatus;

    private String lastTestMessage;

    private LocalDateTime lastTestAt;

    private List<String> requiredSecretFields;

    private List<String> requiredPublicFields;

    private List<String> missingPublicFields;

    private List<String> missingSecretFields;

    private Boolean activationReady;

    private LocalDateTime createTime;

    private LocalDateTime lastUpdate;
}
