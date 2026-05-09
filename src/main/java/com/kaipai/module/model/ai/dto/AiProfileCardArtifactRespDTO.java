package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiProfileCardArtifactRespDTO {

    private String artifactId;

    private String taskId;

    private String status;

    private String templateSceneCode;

    private String styleCode;

    private Long shareCardId;

    private String generatedImageUrl;

    private LocalDateTime createTime;

    private LocalDateTime lastUpdate;
}
