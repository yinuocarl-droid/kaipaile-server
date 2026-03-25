package com.kaipai.module.model.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "登录/注册响应")
public class LoginRespDTO {

    @Schema(description = "JWT Token，后续请求放入 Header: Authorization: Bearer {token}")
    private String token;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户类型: 1=演员, 2=剧组/公司")
    private Integer userType;

    @Schema(description = "昵称")
    private String nickName;

    @Schema(description = "头像地址")
    private String avatarUrl;
}
