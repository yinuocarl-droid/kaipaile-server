package com.kaipai.module.model.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiProfileCardGenerateReqDTO {

    @NotBlank
    private String templateSceneCode;

    private String styleCode;

    private String sourceImageUrl;
}
