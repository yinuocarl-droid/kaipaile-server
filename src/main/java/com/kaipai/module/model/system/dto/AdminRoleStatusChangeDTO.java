package com.kaipai.module.model.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminRoleStatusChangeDTO {

    @NotNull(message = "角色 ID 不能为空")
    private Long adminRoleId;

    @NotNull(message = "状态不能为空")
    private Integer status;

    private String reason;
}


