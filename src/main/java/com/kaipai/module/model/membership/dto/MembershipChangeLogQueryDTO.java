package com.kaipai.module.model.membership.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MembershipChangeLogQueryDTO {

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    private long pageSize = 20;

    private Long userId;
    private String changeReason;
    private String sourceType;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
}
