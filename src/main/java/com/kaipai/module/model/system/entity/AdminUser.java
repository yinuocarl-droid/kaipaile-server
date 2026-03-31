package com.kaipai.module.model.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_user")
public class AdminUser extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long adminUserId;
    private String account;
    private String password;
    private String userName;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
}
