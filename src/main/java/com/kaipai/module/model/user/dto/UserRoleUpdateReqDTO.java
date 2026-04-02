package com.kaipai.module.model.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "当前用户身份切换请求")
public class UserRoleUpdateReqDTO {

    @NotNull
    @Min(1)
    @Max(2)
    @Schema(description = "用户类型: 1=演员, 2=剧组/公司", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer userType;
}
