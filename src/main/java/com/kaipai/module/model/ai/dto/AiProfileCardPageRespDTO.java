package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiProfileCardPageRespDTO {

    private Integer pageNo;

    private String pageType;

    private String status;

    private String providerCode;

    private String modelCode;

    private String generatedImageUrl;

    private String failureReason;

    private LocalDateTime createTime;

    private LocalDateTime lastUpdate;
}
