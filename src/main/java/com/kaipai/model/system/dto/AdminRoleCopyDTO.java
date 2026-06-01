package com.kaipai.model.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminRoleCopyDTO {

    @NotNull(message = "源角色 ID 不能为空")
    private Long sourceRoleId;

    @NotBlank(message = "新角色编码不能为空")
    private String roleCode;

    @NotBlank(message = "新角色名称不能为空")
    private String roleName;

    private String remark;
}


