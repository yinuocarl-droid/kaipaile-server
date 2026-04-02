package com.kaipai.module.controller.admin.system;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.system.dto.AdminOperationLogDetailRespDTO;
import com.kaipai.module.model.system.dto.AdminOperationLogListItemDTO;
import com.kaipai.module.model.system.dto.AdminOperationLogQueryDTO;
import com.kaipai.module.model.system.dto.AdminRoleAiGovernanceMatrixRespDTO;
import com.kaipai.module.model.system.dto.AdminRoleCopyDTO;
import com.kaipai.module.model.system.dto.AdminRoleQueryDTO;
import com.kaipai.module.model.system.dto.AdminRoleRespDTO;
import com.kaipai.module.model.system.dto.AdminRoleSaveDTO;
import com.kaipai.module.model.system.dto.AdminRoleStatusChangeDTO;
import com.kaipai.module.model.system.dto.AdminUserBindRolesDTO;
import com.kaipai.module.model.system.dto.AdminUserCreateDTO;
import com.kaipai.module.model.system.dto.AdminUserDetailRespDTO;
import com.kaipai.module.model.system.dto.AdminUserListItemDTO;
import com.kaipai.module.model.system.dto.AdminUserPasswordResetDTO;
import com.kaipai.module.model.system.dto.AdminUserQueryDTO;
import com.kaipai.module.model.system.dto.AdminUserStatusUpdateDTO;
import com.kaipai.module.model.system.dto.AdminUserUpdateDTO;
import com.kaipai.module.server.system.service.AdminOperationLogService;
import com.kaipai.module.server.system.service.AdminRoleService;
import com.kaipai.module.server.system.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台系统管理")
@RestController
@RequestMapping("/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final AdminUserService adminUserService;
    private final AdminRoleService adminRoleService;
    private final AdminOperationLogService adminOperationLogService;

    @Operation(summary = "后台账号列表")
    @GetMapping({"/users", "/admin-users"})
    @PreAuthorize("hasAuthority('page.system.admin-users')")
    public R<PageResult<AdminUserListItemDTO>> users(@Valid AdminUserQueryDTO query) {
        return R.ok(adminUserService.adminUserList(query));
    }

    @Operation(summary = "后台账号详情")
    @GetMapping({"/users/{id}", "/admin-users/{id}"})
    @PreAuthorize("hasAuthority('page.system.admin-users')")
    public R<AdminUserDetailRespDTO> userDetail(@PathVariable("id") Long id) {
        return R.ok(adminUserService.adminUserDetail(id));
    }

    @Operation(summary = "创建后台账号")
    @PostMapping({"/users", "/admin-users"})
    @PreAuthorize("hasAuthority('action.system.admin-user.create')")
    public R<AdminUserDetailRespDTO> createUser(@Valid @RequestBody AdminUserCreateDTO dto) {
        return R.ok(adminUserService.createAdminUser(dto));
    }

    @Operation(summary = "更新后台账号")
    @PutMapping({"/users/{id}", "/admin-users/{id}"})
    @PreAuthorize("hasAuthority('action.system.admin-user.edit')")
    public R<AdminUserDetailRespDTO> updateUser(@PathVariable("id") Long id, @Valid @RequestBody AdminUserUpdateDTO dto) {
        return R.ok(adminUserService.updateAdminUser(id, dto));
    }

    @Operation(summary = "启用后台账号")
    @PostMapping({"/users/{id}/enable", "/admin-users/{id}/enable"})
    @PreAuthorize("hasAuthority('action.system.admin-user.enable')")
    public R<Void> enableUser(@PathVariable("id") Long id, @RequestBody(required = false) AdminUserStatusUpdateDTO dto) {
        AdminUserStatusUpdateDTO request = dto == null ? new AdminUserStatusUpdateDTO() : dto;
        request.setStatus(1);
        adminUserService.updateAdminUserStatus(id, request);
        return R.ok();
    }

    @Operation(summary = "禁用后台账号")
    @PostMapping({"/users/{id}/disable", "/admin-users/{id}/disable"})
    @PreAuthorize("hasAuthority('action.system.admin-user.disable')")
    public R<Void> disableUser(@PathVariable("id") Long id, @RequestBody(required = false) AdminUserStatusUpdateDTO dto) {
        AdminUserStatusUpdateDTO request = dto == null ? new AdminUserStatusUpdateDTO() : dto;
        request.setStatus(2);
        adminUserService.updateAdminUserStatus(id, request);
        return R.ok();
    }

    @Operation(summary = "重置后台账号密码")
    @PostMapping({"/users/{id}/reset-password", "/admin-users/{id}/reset-password"})
    @PreAuthorize("hasAuthority('action.system.admin-user.reset-password')")
    public R<AdminUserDetailRespDTO> resetPassword(@PathVariable("id") Long id,
                                                   @Valid @RequestBody AdminUserPasswordResetDTO dto) {
        return R.ok(adminUserService.resetAdminUserPassword(id, dto));
    }

    @Operation(summary = "绑定后台账号角色")
    @PostMapping({"/users/{id}/bind-roles", "/admin-users/{id}/bind-roles"})
    @PreAuthorize("hasAuthority('action.system.admin-user.bind-roles')")
    public R<AdminUserDetailRespDTO> bindRoles(@PathVariable("id") Long id,
                                               @Valid @RequestBody AdminUserBindRolesDTO dto) {
        return R.ok(adminUserService.bindUserRoles(id, dto));
    }

    @Operation(summary = "后台角色列表")
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('page.system.roles')")
    public R<PageResult<AdminRoleRespDTO>> roles(@Valid AdminRoleQueryDTO query) {
        return R.ok(adminRoleService.adminRoleList(query));
    }

    @Operation(summary = "后台角色详情")
    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('page.system.roles')")
    public R<AdminRoleRespDTO> roleDetail(@PathVariable("id") Long id) {
        return R.ok(adminRoleService.adminRoleDetail(id));
    }

    @Operation(summary = "AI 治理角色授权矩阵")
    @GetMapping("/roles/ai-governance-matrix")
    @PreAuthorize("hasAuthority('page.system.roles')")
    public R<AdminRoleAiGovernanceMatrixRespDTO> aiGovernanceMatrix() {
        return R.ok(adminRoleService.aiGovernanceMatrix());
    }

    @Operation(summary = "创建后台角色")
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('action.system.role.create')")
    public R<AdminRoleRespDTO> createRole(@Valid @RequestBody AdminRoleSaveDTO dto) {
        return R.ok(adminRoleService.createRole(dto));
    }

    @Operation(summary = "更新后台角色")
    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('action.system.role.edit')")
    public R<AdminRoleRespDTO> updateRole(@PathVariable("id") Long id, @Valid @RequestBody AdminRoleSaveDTO dto) {
        return R.ok(adminRoleService.updateRole(id, dto));
    }

    @Operation(summary = "启用后台角色")
    @PostMapping("/roles/{id}/enable")
    @PreAuthorize("hasAuthority('action.system.role.enable')")
    public R<AdminRoleRespDTO> enableRole(@PathVariable("id") Long id,
                                          @RequestBody(required = false) AdminRoleStatusChangeDTO dto) {
        AdminRoleStatusChangeDTO request = dto == null ? new AdminRoleStatusChangeDTO() : dto;
        request.setAdminRoleId(id);
        request.setStatus(1);
        return R.ok(adminRoleService.changeRoleStatus(request));
    }

    @Operation(summary = "禁用后台角色")
    @PostMapping("/roles/{id}/disable")
    @PreAuthorize("hasAuthority('action.system.role.disable')")
    public R<AdminRoleRespDTO> disableRole(@PathVariable("id") Long id,
                                           @RequestBody(required = false) AdminRoleStatusChangeDTO dto) {
        AdminRoleStatusChangeDTO request = dto == null ? new AdminRoleStatusChangeDTO() : dto;
        request.setAdminRoleId(id);
        request.setStatus(2);
        return R.ok(adminRoleService.changeRoleStatus(request));
    }

    @Operation(summary = "复制后台角色")
    @PostMapping("/roles/{id}/copy")
    @PreAuthorize("hasAuthority('action.system.role.copy')")
    public R<AdminRoleRespDTO> copyRole(@PathVariable("id") Long id, @Valid @RequestBody AdminRoleCopyDTO dto) {
        dto.setSourceRoleId(id);
        return R.ok(adminRoleService.copyRole(dto));
    }

    @Operation(summary = "操作日志列表")
    @GetMapping("/operation-logs")
    @PreAuthorize("hasAuthority('page.system.operation-logs')")
    public R<PageResult<AdminOperationLogListItemDTO>> operationLogs(@Valid AdminOperationLogQueryDTO query) {
        return R.ok(adminOperationLogService.adminOperationLogList(query));
    }

    @Operation(summary = "操作日志详情")
    @GetMapping("/operation-logs/{id}")
    @PreAuthorize("hasAuthority('page.system.operation-logs')")
    public R<AdminOperationLogDetailRespDTO> operationLogDetail(@PathVariable("id") Long id) {
        return R.ok(adminOperationLogService.adminOperationLogDetail(id));
    }
}
