package com.kaipai.module.server.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.system.dto.AdminRoleBriefDTO;
import com.kaipai.module.model.system.dto.AdminUserDetailRespDTO;
import com.kaipai.module.model.system.dto.AdminUserListItemDTO;
import com.kaipai.module.model.system.dto.AdminUserQueryDTO;
import com.kaipai.module.model.system.entity.AdminRole;
import com.kaipai.module.model.system.entity.AdminUser;
import com.kaipai.module.model.system.entity.AdminUserRole;
import com.kaipai.module.server.system.mapper.AdminUserMapper;
import com.kaipai.module.server.system.service.AdminRoleService;
import com.kaipai.module.server.system.service.AdminUserService;
import com.kaipai.module.server.system.service.AdminUserRoleService;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl extends ServiceImpl<AdminUserMapper, AdminUser> implements AdminUserService {

    private final AdminUserRoleService adminUserRoleService;
    private final AdminRoleService adminRoleService;

    @Override
    public PageResult<AdminUserListItemDTO> adminUserList(AdminUserQueryDTO query) {
        Set<Long> filteredUserIds = resolveFilteredUserIds(query.getRoleCode());
        if (filteredUserIds != null && filteredUserIds.isEmpty()) {
            return PageResult.empty();
        }

        Page<AdminUser> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getAccount())) {
            wrapper.like(AdminUser::getAccount, query.getAccount().trim());
        }
        if (StringUtils.hasText(query.getUserName())) {
            wrapper.like(AdminUser::getUserName, query.getUserName().trim());
        }
        if (StringUtils.hasText(query.getPhone())) {
            wrapper.like(AdminUser::getPhone, query.getPhone().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(AdminUser::getStatus, query.getStatus());
        }
        if (filteredUserIds != null) {
            wrapper.in(AdminUser::getAdminUserId, filteredUserIds);
        }
        wrapper.orderByDesc(AdminUser::getCreateTime).orderByDesc(AdminUser::getAdminUserId);

        Page<AdminUser> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Map<Long, List<AdminRoleBriefDTO>> roleMap = buildUserRoleMap(
                result.getRecords().stream().map(AdminUser::getAdminUserId).collect(Collectors.toSet()));
        List<AdminUserListItemDTO> list = result.getRecords().stream().map(user -> {
            AdminUserListItemDTO dto = new AdminUserListItemDTO();
            BeanUtils.copyProperties(user, dto);
            dto.setRoles(roleMap.getOrDefault(user.getAdminUserId(), Collections.emptyList()));
            return dto;
        }).collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AdminUserDetailRespDTO adminUserDetail(Long adminUserId) {
        AdminUser user = getById(adminUserId);
        if (user == null) {
            throw new BizException("后台账号不存在");
        }
        AdminUserDetailRespDTO dto = new AdminUserDetailRespDTO();
        BeanUtils.copyProperties(user, dto);
        dto.setRoles(buildUserRoleMap(Collections.singleton(adminUserId))
                .getOrDefault(adminUserId, Collections.emptyList()));
        return dto;
    }

    private Set<Long> resolveFilteredUserIds(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return null;
        }
        List<AdminRole> roles = adminRoleService.lambdaQuery()
                .eq(AdminRole::getRoleCode, roleCode.trim())
                .list();
        if (roles.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> roleIds = roles.stream().map(AdminRole::getAdminRoleId).collect(Collectors.toSet());
        return adminUserRoleService.lambdaQuery()
                .in(AdminUserRole::getAdminRoleId, roleIds)
                .list()
                .stream()
                .map(AdminUserRole::getAdminUserId)
                .collect(Collectors.toSet());
    }

    private Map<Long, List<AdminRoleBriefDTO>> buildUserRoleMap(Collection<Long> adminUserIds) {
        if (adminUserIds == null || adminUserIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AdminUserRole> bindings = adminUserRoleService.lambdaQuery()
                .in(AdminUserRole::getAdminUserId, adminUserIds)
                .list();
        if (bindings.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> roleIds = bindings.stream().map(AdminUserRole::getAdminRoleId).collect(Collectors.toSet());
        Map<Long, AdminRole> roleMap = adminRoleService.listByIds(roleIds).stream()
                .collect(Collectors.toMap(AdminRole::getAdminRoleId, role -> role));

        Map<Long, List<AdminRoleBriefDTO>> result = new LinkedHashMap<>();
        for (AdminUserRole binding : bindings) {
            AdminRole role = roleMap.get(binding.getAdminRoleId());
            if (role == null) {
                continue;
            }
            result.computeIfAbsent(binding.getAdminUserId(), key -> new java.util.ArrayList<>())
                    .add(toBriefRole(role));
        }
        return result;
    }

    private AdminRoleBriefDTO toBriefRole(AdminRole role) {
        AdminRoleBriefDTO dto = new AdminRoleBriefDTO();
        dto.setAdminRoleId(role.getAdminRoleId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setStatus(role.getStatus());
        return dto;
    }
}
