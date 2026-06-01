package com.kaipai.model.ai.dto;

import lombok.Data;

@Data
public class AdminAiImageProviderTestReqDTO {

    private String sourceImageUrl;

    private String prompt;

    private String negativePrompt;

    private String templateSceneCode;

    private String styleCode;
}
