package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminAiResumeGovernanceSweepResultDTO {

    private boolean dryRun;

    private String requestId;

    private String triggerSource;

    private String operatorName;

    private String evaluatedAt;

    private int totalCount;

    private int dueCount;

    private int autoRemindCount;

    private int timeoutEscalationCount;

    private int executedCount;

    private int skippedCount;

    private List<AdminAiResumeGovernanceSweepItemDTO> items;
}
