package com.kaipai.module.model.ai.dto;

import lombok.Data;

@Data
public class AiImageProviderPublicConfigDTO {

    private String endpoint;

    private String region;

    private String model;

    private String modelVersion;

    private String size;

    private String quality;

    private String responseFormat;

    private Integer count;

    private Boolean watermark;

    private Boolean promptRewrite;

    private String authHeader;

    private String resolution;

    private Integer connectTimeoutMs;

    private Integer readTimeoutMs;

    private Integer pollIntervalMs;

    private Integer maxPollAttempts;

    private String extraParamsJson;
}
