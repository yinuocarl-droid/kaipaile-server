package com.kaipai.module.model.capability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CapabilityProductUpdateDTO {

    @NotBlank
    private String productCode;
    @NotBlank
    private String productName;
    @NotNull
    private Integer capabilityTier;
    @NotNull
    private Integer durationDays;
    @NotNull
    private BigDecimal listPrice;
    @NotNull
    private BigDecimal salePrice;
    private String benefitConfigJson;
    private Integer status;
    private Integer sortNo = 0;
}
