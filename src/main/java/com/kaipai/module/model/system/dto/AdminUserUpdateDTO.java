package com.kaipai.module.model.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminUserUpdateDTO {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "姓名不能为空")
    private String userName;

    private String phone;
    private String email;
}
