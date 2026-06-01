package com.kaipai.model.ai.dto;

import lombok.Data;

@Data
public class AiProfileCardGenerateRespDTO {

    private String taskId;

    private String status;

    private String message;

    private Integer estimatedReadyMinutes;
}
