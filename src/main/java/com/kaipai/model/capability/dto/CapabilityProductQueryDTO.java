package com.kaipai.model.capability.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CapabilityProductQueryDTO {

    @Min(1)
    private int pageNo = 1;
    @Min(1)
    private int pageSize = 20;
    private String productCode;
    private String productName;
    private Integer capabilityTier;
    private Integer status;
}
