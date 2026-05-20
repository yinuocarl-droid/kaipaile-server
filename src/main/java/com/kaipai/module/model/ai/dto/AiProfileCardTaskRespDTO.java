package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    private AiProfileCardThemeRespDTO theme;

    private String failureReason;

    private List<AiProfileCardPageRespDTO> pages = new ArrayList<>();

    private LocalDateTime createTime;

    private LocalDateTime lastUpdate;
}
