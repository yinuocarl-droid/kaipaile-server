package com.kaipai.module.server.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.system.entity.AdminUser;
import com.kaipai.module.server.system.mapper.AdminUserMapper;
import com.kaipai.module.server.system.service.AdminUserService;
import org.springframework.stereotype.Service;

@Service
public class AdminUserServiceImpl extends ServiceImpl<AdminUserMapper, AdminUser> implements AdminUserService {
}
