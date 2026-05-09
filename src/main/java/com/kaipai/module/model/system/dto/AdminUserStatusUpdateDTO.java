package com.kaipai.module.model.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserStatusUpdateDTO {

    @NotNull(message = "状态不能为空")
    @Min(value = 1, message = "状态无效")
    @Max(value = 2, message = "状态无效")
    private Integer status;

    private String reason;
}


