package com.kaipai.model.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AdminAiImageProviderRevealSecretRespDTO {

    private String providerCode;

    private Map<String, String> secrets;

    private LocalDateTime revealedAt;
}
