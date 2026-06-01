package com.kaipai.model.capability.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CapabilityAccountQueryDTO {

    @Min(1)
    private int pageNo = 1;
    @Min(1)
    private int pageSize = 20;
    private Long userId;
    private String phone;
    private Integer tier;
    private Integer status;
    private String sourceType;
    private LocalDateTime effectiveTimeFrom;
    private LocalDateTime effectiveTimeTo;
    private LocalDateTime expireTimeFrom;
    private LocalDateTime expireTimeTo;
}
