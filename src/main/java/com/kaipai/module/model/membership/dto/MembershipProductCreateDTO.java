package com.kaipai.module.model.membership.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class MembershipProductCreateDTO {

    @NotBlank
    private String productCode;
    @NotBlank
    private String productName;
    @NotNull
    private Integer membershipTier;
    @NotNull
    private Integer durationDays;
    @NotNull
    private BigDecimal listPrice;
    @NotNull
    private BigDecimal salePrice;
    private String benefitConfigJson;
    private Integer sortNo = 0;
}
