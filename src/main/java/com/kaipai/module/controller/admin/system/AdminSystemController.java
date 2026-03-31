package com.kaipai.module.controller.admin.system;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.system.dto.AdminOperationLogDetailRespDTO;
import com.kaipai.module.model.system.dto.AdminOperationLogListItemDTO;
import com.kaipai.module.model.system.dto.AdminOperationLogQueryDTO;
import com.kaipai.module.model.system.dto.AdminRoleQueryDTO;
import com.kaipai.module.model.system.dto.AdminRoleRespDTO;
import com.kaipai.module.model.system.dto.AdminRoleSaveDTO;
import com.kaipai.module.model.system.dto.AdminUserDetailRespDTO;
import com.kaipai.module.model.system.dto.AdminUserListItemDTO;
import com.kaipai.module.model.system.dto.AdminUserQueryDTO;
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
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('page.system.admin-users')")
    public R<PageResult<AdminUserListItemDTO>> users(@Valid AdminUserQueryDTO query) {
        return R.ok(adminUserService.adminUserList(query));
    }

    @Operation(summary = "后台账号详情")
    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('page.system.admin-users')")
    public R<AdminUserDetailRespDTO> userDetail(@PathVariable("id") Long id) {
        return R.ok(adminUserService.adminUserDetail(id));
    }

    @Operation(summary = "后台角色列表")
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('page.system.roles')")
    public R<PageResult<AdminRoleRespDTO>> roles(@Valid AdminRoleQueryDTO query) {
        return R.ok(adminRoleService.adminRoleList(query));
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
