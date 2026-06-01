package com.kaipai.service.system;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.model.system.entity.AdminUserRole;

import java.util.Collection;

public interface AdminUserRoleService extends IService<AdminUserRole> {

    void replaceRoles(Long adminUserId, Collection<Long> roleIds);
}


