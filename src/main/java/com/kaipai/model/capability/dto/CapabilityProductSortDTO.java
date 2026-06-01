package com.kaipai.model.capability.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CapabilityProductSortDTO {

    @NotNull
    private Integer sortNo;
}
