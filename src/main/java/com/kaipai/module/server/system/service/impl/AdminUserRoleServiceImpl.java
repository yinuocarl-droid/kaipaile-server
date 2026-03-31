package com.kaipai.module.server.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.system.entity.AdminUserRole;
import com.kaipai.module.server.system.mapper.AdminUserRoleMapper;
import com.kaipai.module.server.system.service.AdminUserRoleService;
import org.springframework.stereotype.Service;

@Service
public class AdminUserRoleServiceImpl extends ServiceImpl<AdminUserRoleMapper, AdminUserRole> implements AdminUserRoleService {
}
