package com.kaipai.model.system.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminOperationLogQueryDTO {

    @Min(1)
    private int pageNo = 1;

    @Min(1)
    private int pageSize = 20;

    private Long adminUserId;
    private String moduleCode;
    private String operationCode;
    private String targetType;
    private String requestId;
    private Integer result;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
}


