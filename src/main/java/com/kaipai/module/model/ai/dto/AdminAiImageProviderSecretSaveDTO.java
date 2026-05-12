package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AdminAiImageProviderSecretSaveDTO {

    private Map<String, String> secrets;

    private String reason;
}
