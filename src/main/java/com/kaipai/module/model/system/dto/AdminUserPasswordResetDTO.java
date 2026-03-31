package com.kaipai.module.model.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminUserPasswordResetDTO {

    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    private String credentialDeliveryMode;
    private String reason;
    private String resetResult;
}
