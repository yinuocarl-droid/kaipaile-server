package com.kaipai.model.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信一键登录请求")
public class WechatLoginReqDTO {

    @NotBlank(message = "微信手机号授权 code 不能为空")
    @Schema(description = "微信 getPhoneNumber 返回的手机号授权 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(description = "邀请码（首次自动注册时生效）", example = "KM7P4A")
    private String inviteCode;

    @Schema(description = "注册设备指纹（可选）", example = "device-fingerprint-001")
    private String deviceFingerprint;
}
