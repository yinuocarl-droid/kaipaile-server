package com.kaipai.module.model.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_role")
public class AdminRole extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long adminRoleId;
    private String roleCode;
    private String roleName;
    private Integer status;
    private String remark;
    private String menuPermissionsJson;
    private String pagePermissionsJson;
    private String actionPermissionsJson;
}


