package com.kaipai.model.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "当前登录用户会话信息")
public class UserSessionRespDTO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "用户类型: 1=演员, 2=剧组/团队")
    private Integer userType;

    @Schema(description = "用户状态")
    private Integer status;

    @Schema(description = "昵称")
    private String nickName;

    @Schema(description = "头像地址")
    private String avatarUrl;

    @Schema(description = "注册时间")
    private LocalDateTime registeredAt;

    @Schema(description = "实名认证状态: 0未认证, 1认证中, 2已认证, 3认证失败")
    private Integer realAuthStatus;

    @Schema(description = "邀请码")
    private String inviteCode;

    @Schema(description = "邀请人用户ID")
    private Long invitedByUserId;

    @Schema(description = "有效邀请数量")
    private Integer validInviteCount;

    @Schema(description = "总邀请数量")
    private Integer totalInviteCount;

    @Schema(description = "待校验邀请数量")
    private Integer pendingInviteCount;

    @Schema(description = "风险邀请数量")
    private Integer flaggedInviteCount;

    @Schema(description = "能力层级: base/plus/pro")
    private String capabilityTier;
}
