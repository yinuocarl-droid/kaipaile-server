package com.kaipai.module.server.adminauth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.ResultCode;
import com.kaipai.common.util.JwtUtil;
import com.kaipai.module.model.adminauth.dto.AdminLoginReqDTO;
import com.kaipai.module.model.adminauth.dto.AdminLoginRespDTO;
import com.kaipai.module.model.adminauth.dto.AdminSessionInfoDTO;
import com.kaipai.module.model.system.entity.AdminRole;
import com.kaipai.module.model.system.entity.AdminUser;
import com.kaipai.module.model.system.entity.AdminUserRole;
import com.kaipai.module.server.adminauth.service.AdminAuthService;
import com.kaipai.module.server.system.mapper.AdminRoleMapper;
import com.kaipai.module.server.system.mapper.AdminUserMapper;
import com.kaipai.module.server.system.mapper.AdminUserRoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final String REMOVED_RECRUIT_MENU_PERMISSION = "menu." + "recruit";
    private static final String REMOVED_CAPABILITY_PAGE_PERMISSION_PREFIX = "page." + "capability.";
    private static final String REMOVED_CAPABILITY_ACTION_PERMISSION_PREFIX = "action." + "capability.";

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AdminAuthContext adminAuthContext;
    private final ObjectMapper objectMapper;

    @Override
    public AdminLoginRespDTO login(AdminLoginReqDTO dto, HttpServletRequest request) {
        AdminUser adminUser = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getAccount, dto.getAccount()));
        if (adminUser == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND.getCode(), "后台账号不存在");
        }
        if (!Integer.valueOf(1).equals(adminUser.getStatus())) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED.getCode(), "后台账号已被禁用");
        }
        if (!passwordEncoder.matches(dto.getPassword(), adminUser.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR.getCode(), "后台账号密码错误");
        }

        PermissionBundle permissionBundle = loadPermissions(adminUser.getAdminUserId());
        AdminSessionInfoDTO sessionInfo = toSessionInfo(adminUser, permissionBundle);
        String token = jwtUtil.generateAdminToken(
                adminUser.getAdminUserId(),
                adminUser.getAccount(),
                adminUser.getUserName(),
                permissionBundle.roleCodes(),
                permissionBundle.allPermissions()
        );

        AdminUser update = new AdminUser();
        update.setAdminUserId(adminUser.getAdminUserId());
        update.setLastLoginTime(LocalDateTime.now());
        update.setLastLoginIp(resolveClientIp(request));
        update.setUpdateUserName(adminUser.getUserName());
        adminUserMapper.updateById(update);

        return AdminLoginRespDTO.builder()
                .accessToken(token)
                .adminUserInfo(sessionInfo)
                .build();
    }

    @Override
    public AdminSessionInfoDTO currentSession() {
        AdminAuthenticatedUser currentAdmin = adminAuthContext.requireCurrentAdmin();
        AdminUser adminUser = adminUserMapper.selectById(currentAdmin.getAdminUserId());
        if (adminUser == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND.getCode(), "后台账号不存在");
        }
        return toSessionInfo(adminUser, loadPermissions(adminUser.getAdminUserId()));
    }

    private PermissionBundle loadPermissions(Long adminUserId) {
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(new LambdaQueryWrapper<AdminUserRole>()
                .eq(AdminUserRole::getAdminUserId, adminUserId));
        if (userRoles.isEmpty()) {
            return new PermissionBundle(Collections.emptySet(), Collections.emptySet(),
                    Collections.emptySet(), Collections.emptySet());
        }

        List<Long> roleIds = userRoles.stream().map(AdminUserRole::getAdminRoleId).distinct().toList();
        Map<Long, AdminRole> roleMap = adminRoleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> Integer.valueOf(1).equals(role.getStatus()))
                .collect(Collectors.toMap(AdminRole::getAdminRoleId, Function.identity()));

        Set<String> roleCodes = new LinkedHashSet<>();
        Set<String> menuPermissions = new LinkedHashSet<>();
        Set<String> pagePermissions = new LinkedHashSet<>();
        Set<String> actionPermissions = new LinkedHashSet<>();
        for (Long roleId : roleIds) {
            AdminRole role = roleMap.get(roleId);
            if (role == null) {
                continue;
            }
            roleCodes.add(role.getRoleCode());
            List<String> roleMenuPermissions = parsePermissions(role.getMenuPermissionsJson());
            if (roleMenuPermissions.contains(REMOVED_RECRUIT_MENU_PERMISSION)) {
                throw new BizException("后台角色菜单权限未收口");
            }
            menuPermissions.addAll(roleMenuPermissions);
            List<String> rolePagePermissions = parsePermissions(role.getPagePermissionsJson());
            List<String> roleActionPermissions = parsePermissions(role.getActionPermissionsJson());
            if (containsRemovedCapabilityPermission(rolePagePermissions, roleActionPermissions)) {
                throw new BizException("后台角色能力权限未收口");
            }
            pagePermissions.addAll(rolePagePermissions);
            actionPermissions.addAll(roleActionPermissions);
        }
        Set<String> allPermissions = new LinkedHashSet<>();
        allPermissions.addAll(menuPermissions);
        allPermissions.addAll(pagePermissions);
        allPermissions.addAll(actionPermissions);
        return new PermissionBundle(roleCodes, menuPermissions, pagePermissions, allPermissions);
    }

    private AdminSessionInfoDTO toSessionInfo(AdminUser adminUser, PermissionBundle permissionBundle) {
        AdminSessionInfoDTO dto = new AdminSessionInfoDTO();
        dto.setAdminUserId(adminUser.getAdminUserId());
        dto.setAccount(adminUser.getAccount());
        dto.setUserName(adminUser.getUserName());
        dto.setPhone(adminUser.getPhone());
        dto.setEmail(adminUser.getEmail());
        dto.setRoleCodes(new ArrayList<>(permissionBundle.roleCodes()));
        dto.setMenuPermissions(new ArrayList<>(permissionBundle.menuPermissions()));
        dto.setPagePermissions(new ArrayList<>(permissionBundle.pagePermissions()));
        dto.setActionPermissions(permissionBundle.allPermissions().stream()
                .filter(permission -> permission.startsWith("action."))
                .toList());
        return dto;
    }

    private List<String> parsePermissions(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(rawJson, STRING_LIST_TYPE);
        } catch (Exception e) {
            throw new BizException("后台角色权限数据格式异常");
        }
    }

    private boolean containsRemovedCapabilityPermission(List<String> pagePermissions, List<String> actionPermissions) {
        return pagePermissions.stream().anyMatch(permission -> permission.startsWith(REMOVED_CAPABILITY_PAGE_PERMISSION_PREFIX))
                || actionPermissions.stream().anyMatch(permission -> permission.startsWith(REMOVED_CAPABILITY_ACTION_PERMISSION_PREFIX));
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record PermissionBundle(
            Set<String> roleCodes,
            Set<String> menuPermissions,
            Set<String> pagePermissions,
            Set<String> allPermissions) {
    }
}
