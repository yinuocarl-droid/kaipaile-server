package com.kaipai.module.model.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_user_role")
public class AdminUserRole extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long adminUserRoleId;
    private Long adminUserId;
    private Long adminRoleId;
}


