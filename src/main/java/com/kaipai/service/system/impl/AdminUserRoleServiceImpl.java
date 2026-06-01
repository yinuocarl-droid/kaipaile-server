package com.kaipai.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.model.system.entity.AdminUserRole;
import com.kaipai.mapper.system.AdminUserRoleMapper;
import com.kaipai.service.system.AdminUserRoleService;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserRoleServiceImpl extends ServiceImpl<AdminUserRoleMapper, AdminUserRole> implements AdminUserRoleService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceRoles(Long adminUserId, Collection<Long> roleIds) {
        remove(new LambdaQueryWrapper<AdminUserRole>()
                .eq(AdminUserRole::getAdminUserId, adminUserId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<AdminUserRole> binds = roleIds.stream().distinct().map(roleId -> {
            AdminUserRole binding = new AdminUserRole();
            binding.setAdminUserId(adminUserId);
            binding.setAdminRoleId(roleId);
            return binding;
        }).collect(Collectors.toList());
        saveBatch(binds);
    }
}


