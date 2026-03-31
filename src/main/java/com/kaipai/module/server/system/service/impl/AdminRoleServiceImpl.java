package com.kaipai.module.server.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.system.dto.AdminRoleQueryDTO;
import com.kaipai.module.model.system.dto.AdminRoleRespDTO;
import com.kaipai.module.model.system.dto.AdminRoleSaveDTO;
import com.kaipai.module.model.system.entity.AdminRole;
import com.kaipai.module.server.system.mapper.AdminRoleMapper;
import com.kaipai.module.server.system.service.AdminRoleService;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminRoleServiceImpl extends ServiceImpl<AdminRoleMapper, AdminRole> implements AdminRoleService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final AdminOperationLogger adminOperationLogger;

    @Override
    public PageResult<AdminRoleRespDTO> adminRoleList(AdminRoleQueryDTO query) {
        Page<AdminRole> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<AdminRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getRoleCode())) {
            wrapper.like(AdminRole::getRoleCode, query.getRoleCode().trim());
        }
        if (StringUtils.hasText(query.getRoleName())) {
            wrapper.like(AdminRole::getRoleName, query.getRoleName().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(AdminRole::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(AdminRole::getCreateTime).orderByDesc(AdminRole::getAdminRoleId);

        Page<AdminRole> result = page(page, wrapper);
        List<AdminRoleRespDTO> list = result.getRecords().stream().map(this::toResp).toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    @Transactional
    public AdminRoleRespDTO createRole(AdminRoleSaveDTO dto) {
        ensureRoleCodeUnique(dto.getRoleCode(), null);
        AdminRole role = new AdminRole();
        applySaveDto(role, dto);
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        save(role);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("create")
                .targetType("admin_role")
                .targetId(role.getAdminRoleId())
                .afterSnapshot(snapshot(role))
                .extraContext(snapshot(role))
                .operationResult(1)
                .build());
        return toResp(role);
    }

    @Override
    @Transactional
    public AdminRoleRespDTO updateRole(Long adminRoleId, AdminRoleSaveDTO dto) {
        AdminRole role = getById(adminRoleId);
        if (role == null) {
            throw new BizException("后台角色不存在");
        }
        ensureRoleCodeUnique(dto.getRoleCode(), adminRoleId);
        AdminRole beforeRole = copyRole(role);
        applySaveDto(role, dto);
        updateById(role);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("edit")
                .targetType("admin_role")
                .targetId(role.getAdminRoleId())
                .beforeSnapshot(snapshot(beforeRole))
                .afterSnapshot(snapshot(role))
                .extraContext(snapshot(role))
                .operationResult(1)
                .build());
        return toResp(getById(adminRoleId));
    }

    private void ensureRoleCodeUnique(String roleCode, Long excludeRoleId) {
        LambdaQueryWrapper<AdminRole> wrapper = new LambdaQueryWrapper<AdminRole>()
                .eq(AdminRole::getRoleCode, roleCode.trim());
        if (excludeRoleId != null) {
            wrapper.ne(AdminRole::getAdminRoleId, excludeRoleId);
        }
        if (count(wrapper) > 0) {
            throw new BizException("角色编码已存在");
        }
    }

    private void applySaveDto(AdminRole role, AdminRoleSaveDTO dto) {
        role.setRoleCode(dto.getRoleCode().trim());
        role.setRoleName(dto.getRoleName().trim());
        role.setStatus(dto.getStatus());
        role.setRemark(dto.getRemark());
        role.setMenuPermissionsJson(writePermissions(dto.getMenuPermissions()));
        role.setPagePermissionsJson(writePermissions(dto.getPagePermissions()));
        role.setActionPermissionsJson(writePermissions(dto.getActionPermissions()));
    }

    private AdminRoleRespDTO toResp(AdminRole role) {
        AdminRoleRespDTO dto = new AdminRoleRespDTO();
        BeanUtils.copyProperties(role, dto);
        dto.setMenuPermissions(readPermissions(role.getMenuPermissionsJson()));
        dto.setPagePermissions(readPermissions(role.getPagePermissionsJson()));
        dto.setActionPermissions(readPermissions(role.getActionPermissionsJson()));
        return dto;
    }

    private List<String> readPermissions(String permissionsJson) {
        if (!StringUtils.hasText(permissionsJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(permissionsJson, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BizException("角色权限数据格式异常");
        }
    }

    private String writePermissions(List<String> permissions) {
        List<String> safePermissions = permissions == null ? Collections.emptyList() : permissions;
        try {
            return objectMapper.writeValueAsString(safePermissions);
        } catch (JsonProcessingException ex) {
            throw new BizException("角色权限数据序列化失败");
        }
    }

    private AdminRole copyRole(AdminRole role) {
        AdminRole copy = new AdminRole();
        BeanUtils.copyProperties(role, copy);
        return copy;
    }

    private AdminRoleRespDTO snapshot(AdminRole role) {
        return toResp(role);
    }
}
