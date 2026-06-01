package com.kaipai.model.ai.dto;

import lombok.Data;

@Data
public class AdminAiImageProviderTestRespDTO {

    private String providerCode;

    private String modelCode;

    private String status;

    private String message;

    private String imageUrl;

    private Long elapsedMs;
}
