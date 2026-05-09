package com.kaipai.module.model.system.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class AdminRoleSaveDTO {

    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    private Integer status;
    private String remark;
    private List<String> menuPermissions;
    private List<String> pagePermissions;
    private List<String> actionPermissions;
}


