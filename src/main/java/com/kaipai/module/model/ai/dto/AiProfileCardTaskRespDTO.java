package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiProfileCardTaskRespDTO {

    private String taskId;

    private String status;

    private String templateSceneCode;

    private String styleCode;

    private String providerCode;

    private String modelCode;

    private Long shareCardId;

    private String sourceImageUrl;

    private String generatedImageUrl;

    private String failureReason;

    private LocalDateTime createTime;

    private LocalDateTime lastUpdate;
}
