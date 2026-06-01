package com.kaipai.model.ai.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminAiResumeHistoryQueryDTO {

    @Min(1)
    private int pageNo = 1;

    @Min(1)
    private int pageSize = 20;

    private Long userId;

    private String status;

    private String keyword;

    private String requestId;
}
