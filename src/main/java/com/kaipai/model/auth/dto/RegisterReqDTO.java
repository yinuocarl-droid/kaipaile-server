package com.kaipai.model.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterReqDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "短信验证码（6位数字）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotNull(message = "用户类型不能为空")
    @Schema(description = "用户类型: 1=演员, 2=剧组/团队", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer userType;

    @Schema(description = "昵称（可选，不填则自动生成）", example = "小明")
    private String nickName;

    @Schema(description = "邀请码（可选）", example = "KM7P4A")
    private String inviteCode;

    @Schema(description = "注册设备指纹（可选）", example = "device-fingerprint-001")
    private String deviceFingerprint;
}
