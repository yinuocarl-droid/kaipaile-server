package com.kaipai.module.model.system.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class AdminUserBindRolesDTO {

    @NotNull(message = "角色编码不能为空")
    private List<String> roleCodes;

    private String reason;
}
