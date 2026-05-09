package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminAiResumeGovernanceSweepRequestDTO {

    private List<String> failureIds;

    private Integer limit;

    private String evaluateAt;

    private String reason;

    private String requestId;
}
