package com.kaipai.module.model.recruit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminRecruitProjectStatusChangeDTO {

    @NotNull
    @Min(1)
    @Max(2)
    private Integer status;

    private String reason;
}
