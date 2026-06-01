package com.kaipai.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.system.dto.AdminRoleBriefDTO;
import com.kaipai.model.system.dto.AdminUserBindRolesDTO;
import com.kaipai.model.system.dto.AdminUserCreateDTO;
import com.kaipai.model.system.dto.AdminUserDetailRespDTO;
import com.kaipai.model.system.dto.AdminUserListItemDTO;
import com.kaipai.model.system.dto.AdminUserPasswordResetDTO;
import com.kaipai.model.system.dto.AdminUserQueryDTO;
import com.kaipai.model.system.dto.AdminUserStatusUpdateDTO;
import com.kaipai.model.system.dto.AdminUserUpdateDTO;
import com.kaipai.model.system.entity.AdminRole;
import com.kaipai.model.system.entity.AdminUser;
import com.kaipai.model.system.entity.AdminUserRole;
import com.kaipai.mapper.system.AdminUserMapper;
import com.kaipai.service.system.AdminRoleService;
import com.kaipai.service.system.AdminUserRoleService;
import com.kaipai.service.system.AdminUserService;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl extends ServiceImpl<AdminUserMapper, AdminUser> implements AdminUserService {

    private final AdminUserRoleService adminUserRoleService;
    private final AdminRoleService adminRoleService;
    private final AdminOperationLogger adminOperationLogger;
    private final PasswordEncoder passwordEncoder;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserDetailRespDTO createAdminUser(AdminUserCreateDTO dto) {
        String account = dto.getAccount().trim();
        ensureAccountUnique(account, null);
        AdminUser user = new AdminUser();
        user.setAccount(account);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setUserName(dto.getUserName().trim());
        user.setPhone(trimToNull(dto.getPhone()));
        user.setEmail(trimToNull(dto.getEmail()));
        user.setStatus(1);
        save(user);
        List<AdminRole> rolesAfter = bindRolesToUser(user.getAdminUserId(), dto.getRoleCodes());
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("create")
                .targetType("admin_user")
                .targetId(user.getAdminUserId())
                .beforeSnapshot(Collections.emptyMap())
                .afterSnapshot(userSnapshot(user, rolesAfter))
                .extraContext(buildCreateContext(account, rolesAfter))
                .operationResult(1)
                .build());
        return adminUserDetail(user.getAdminUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserDetailRespDTO updateAdminUser(Long adminUserId, AdminUserUpdateDTO dto) {
        AdminUser user = requireUser(adminUserId);
        String account = dto.getAccount().trim();
        ensureAccountUnique(account, adminUserId);
        List<AdminRole> roles = loadRolesForUser(adminUserId);
        Map<String, Object> beforeSnapshot = userSnapshot(user, roles);
        user.setAccount(account);
        user.setUserName(dto.getUserName().trim());
        user.setPhone(trimToNull(dto.getPhone()));
        user.setEmail(trimToNull(dto.getEmail()));
        updateById(user);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("edit")
                .targetType("admin_user")
                .targetId(adminUserId)
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(userSnapshot(user, roles))
                .extraContext(buildEditContext(user))
                .operationResult(1)
                .build());
        return adminUserDetail(adminUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAdminUserStatus(Long adminUserId, AdminUserStatusUpdateDTO dto) {
        AdminUser user = requireUser(adminUserId);
        if (Objects.equals(user.getStatus(), dto.getStatus())) {
            return;
        }
        List<AdminRole> roles = loadRolesForUser(adminUserId);
        int beforeStatus = user.getStatus();
        Map<String, Object> beforeSnapshot = userSnapshot(user, roles);
        user.setStatus(dto.getStatus());
        updateById(user);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode(dto.getStatus() == 1 ? "enable" : "disable")
                .targetType("admin_user")
                .targetId(adminUserId)
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(userSnapshot(user, roles))
                .extraContext(buildStatusContext(user.getAccount(), beforeStatus, dto.getStatus(), dto.getReason()))
                .operationResult(1)
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserDetailRespDTO resetAdminUserPassword(Long adminUserId, AdminUserPasswordResetDTO dto) {
        AdminUser user = requireUser(adminUserId);
        List<AdminRole> roles = loadRolesForUser(adminUserId);
        Map<String, Object> beforeSnapshot = userSnapshot(user, roles);
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        updateById(user);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("reset-password")
                .targetType("admin_user")
                .targetId(adminUserId)
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(userSnapshot(user, roles))
                .extraContext(buildResetContext(user.getAccount(), dto))
                .operationResult(1)
                .build());
        return adminUserDetail(adminUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserDetailRespDTO bindUserRoles(Long adminUserId, AdminUserBindRolesDTO dto) {
        AdminUser user = requireUser(adminUserId);
        List<AdminRole> beforeRoles = loadRolesForUser(adminUserId);
        List<String> beforeCodes = toRoleCodes(beforeRoles);
        List<AdminRole> afterRoles = bindRolesToUser(adminUserId, dto.getRoleCodes());
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("bind-roles")
                .targetType("admin_user")
                .targetId(adminUserId)
                .beforeSnapshot(userSnapshot(user, beforeRoles))
                .afterSnapshot(userSnapshot(user, afterRoles))
                .extraContext(buildBindContext(user.getAccount(), beforeCodes, toRoleCodes(afterRoles), dto.getReason()))
                .operationResult(1)
                .build());
        return adminUserDetail(adminUserId);
    }

    private AdminUser requireUser(Long adminUserId) {
        AdminUser user = getById(adminUserId);
        if (user == null) {
            throw new BizException("后台账号不存在");
        }
        return user;
    }

    private void ensureAccountUnique(String account, Long excludeAdminUserId) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getAccount, account.trim());
        if (excludeAdminUserId != null) {
            wrapper.ne(AdminUser::getAdminUserId, excludeAdminUserId);
        }
        if (count(wrapper) > 0) {
            throw new BizException("后台账号已存在");
        }
    }

    private List<AdminRole> bindRolesToUser(Long adminUserId, List<String> roleCodes) {
        List<AdminRole> roles = loadActiveRoles(roleCodes);
        adminUserRoleService.replaceRoles(adminUserId, roleIds(roles));
        return roles;
    }

    private Set<Long> roleIds(List<AdminRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptySet();
        }
        return roles.stream().map(AdminRole::getAdminRoleId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<AdminRole> loadActiveRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = roleCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<AdminRole> roles = adminRoleService.lambdaQuery()
                .in(AdminRole::getRoleCode, normalized)
                .eq(AdminRole::getStatus, 1)
                .list();
        if (roles.size() != normalized.size()) {
            throw new BizException("角色编码不存在或已禁用");
        }
        return roles;
    }

    private List<AdminRole> loadRolesForUser(Long adminUserId) {
        List<AdminUserRole> bindings = adminUserRoleService.lambdaQuery()
                .eq(AdminUserRole::getAdminUserId, adminUserId)
                .list();
        if (bindings.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> roleIds = bindings.stream().map(AdminUserRole::getAdminRoleId).collect(Collectors.toSet());
        return adminRoleService.listByIds(roleIds);
    }

    private Map<String, Object> userSnapshot(AdminUser user, List<AdminRole> roles) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (user == null) {
            return snapshot;
        }
        snapshot.put("adminUserId", user.getAdminUserId());
        snapshot.put("account", user.getAccount());
        snapshot.put("status", user.getStatus());
        snapshot.put("roleCodes", toRoleCodes(roles));
        return snapshot;
    }

    private List<String> toRoleCodes(List<AdminRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream()
                .map(AdminRole::getRoleCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildCreateContext(String account, List<AdminRole> rolesAfter) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("target_admin_account", account);
        context.put("role_codes_after", toRoleCodes(rolesAfter));
        return context;
    }

    private Map<String, Object> buildStatusContext(String account, int before, int after, String reason) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("target_admin_account", account);
        context.put("status_before", before);
        context.put("status_after", after);
        context.put("reason", trimToNull(reason));
        return context;
    }

    private Map<String, Object> buildEditContext(AdminUser user) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("target_admin_user_id", user.getAdminUserId());
        context.put("target_admin_account", user.getAccount());
        context.put("user_name", user.getUserName());
        context.put("phone", user.getPhone());
        context.put("email", user.getEmail());
        return context;
    }

    private Map<String, Object> buildResetContext(String account, AdminUserPasswordResetDTO dto) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("target_admin_account", account);
        context.put("reset_result", StringUtils.hasText(dto.getResetResult()) ? dto.getResetResult() : "success");
        context.put("credential_delivery_mode", trimToNull(dto.getCredentialDeliveryMode()));
        context.put("reason", trimToNull(dto.getReason()));
        return context;
    }

    private Map<String, Object> buildBindContext(String account, List<String> before, List<String> after, String reason) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("target_admin_account", account);
        context.put("role_codes_before", before);
        context.put("role_codes_after", after);
        context.put("reason", trimToNull(reason));
        return context;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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


