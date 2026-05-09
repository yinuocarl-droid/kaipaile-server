package com.kaipai.module.model.capability.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CapabilityAccountOpenDTO {

    @NotNull
    private Integer tier;
    @NotNull
    private LocalDateTime effectiveTime;
    @NotNull
    private LocalDateTime expireTime;
    @NotEmpty
    private String sourceType;
    private Long sourceRefId;
    private String remark;
}

