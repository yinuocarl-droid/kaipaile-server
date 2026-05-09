package com.kaipai.module.model.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "登录/注册响应")
public class LoginRespDTO {

    @Schema(description = "JWT Token，后续请求放入 Header: Authorization: Bearer {token}")
    private String token;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户类型: 1=演员, 2=剧组/团队")
    private Integer userType;

    @Schema(description = "昵称")
    private String nickName;

    @Schema(description = "头像地址")
    private String avatarUrl;

    @Schema(description = "注册时间")
    private LocalDateTime registeredAt;

    @Schema(description = "实名认证状态: 0未认证, 1认证中, 2已认证, 3认证失败")
    private Integer realAuthStatus;

    @Schema(description = "邀请人用户ID")
    private Long invitedByUserId;

    @Schema(description = "有效邀请数量")
    private Integer validInviteCount;
}
