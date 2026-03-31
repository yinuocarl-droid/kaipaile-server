package com.kaipai.module.server.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.system.entity.AdminRole;
import com.kaipai.module.server.system.mapper.AdminRoleMapper;
import com.kaipai.module.server.system.service.AdminRoleService;
import org.springframework.stereotype.Service;

@Service
public class AdminRoleServiceImpl extends ServiceImpl<AdminRoleMapper, AdminRole> implements AdminRoleService {
}
