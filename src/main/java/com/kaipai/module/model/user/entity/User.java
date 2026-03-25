package com.kaipai.module.model.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long userId;

    private String userNo;

    private String account;

    private String phone;

    private String email;

    private String password;

    private String userName;

    private String avatarUrl;

    /** 用户类型: 1演员, 2公司/剧组, 3平台管理员 */
    private Integer userType;

    /** 注册来源: 1手机, 2邮箱, 3微信小程序 */
    private Integer registerSource;

    /** 实名状态: 0未认证, 1认证中, 2已认证, 3认证失败 */
    private Integer realAuthStatus;

    /** 状态: 1正常, 2禁用, 3冻结 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    private String remark;

    private String extendedField;
}
