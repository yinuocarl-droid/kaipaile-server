package com.kaipai.module.model.capability.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CapabilityProductCreateDTO {

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
    private Integer sortNo = 0;
}
