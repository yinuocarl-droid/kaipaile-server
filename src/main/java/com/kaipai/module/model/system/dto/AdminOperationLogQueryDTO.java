package com.kaipai.module.model.system.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminOperationLogQueryDTO {

    @Min(1)
    private int pageNo = 1;

    @Min(1)
    private int pageSize = 20;

    private Long adminUserId;
    private String moduleCode;
    private String operationCode;
    private Integer result;
}
