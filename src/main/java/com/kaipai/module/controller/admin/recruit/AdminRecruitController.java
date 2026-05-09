package com.kaipai.module.controller.admin.recruit;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.recruit.dto.AdminRecruitApplyListItemDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitApplyQueryDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitProjectListItemDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitProjectQueryDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitProjectStatusChangeDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitRoleListItemDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitRoleQueryDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitRoleStatusChangeDTO;
import com.kaipai.module.server.recruit.service.AdminRecruitGovernanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台招募治理")
@RestController
@RequestMapping("/admin/recruit")
@RequiredArgsConstructor
public class AdminRecruitController {

    private final AdminRecruitGovernanceService adminRecruitGovernanceService;

    @Operation(summary = "剧组项目列表")
    @GetMapping("/projects")
    @PreAuthorize("hasAuthority('page.recruit.projects')")
    public R<PageResult<AdminRecruitProjectListItemDTO>> projects(@Valid AdminRecruitProjectQueryDTO query) {
        return R.ok(adminRecruitGovernanceService.projectList(query));
    }

    @Operation(summary = "招募角色列表")
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('page.recruit.roles')")
    public R<PageResult<AdminRecruitRoleListItemDTO>> roles(@Valid AdminRecruitRoleQueryDTO query) {
        return R.ok(adminRecruitGovernanceService.roleList(query));
    }

    @Operation(summary = "投递记录列表")
    @GetMapping("/applies")
    @PreAuthorize("hasAuthority('page.recruit.applies')")
    public R<PageResult<AdminRecruitApplyListItemDTO>> applies(@Valid AdminRecruitApplyQueryDTO query) {
        return R.ok(adminRecruitGovernanceService.applyList(query));
    }

    @Operation(summary = "校准剧组项目状态")
    @PostMapping("/projects/{id}/status")
    @PreAuthorize("hasAuthority('action.recruit.project.status')")
    public R<Void> updateProjectStatus(@PathVariable Long id, @Valid @RequestBody AdminRecruitProjectStatusChangeDTO dto) {
        adminRecruitGovernanceService.updateProjectStatus(id, dto);
        return R.ok();
    }

    @Operation(summary = "校准招募角色状态")
    @PostMapping("/roles/{id}/status")
    @PreAuthorize("hasAuthority('action.recruit.role.status')")
    public R<Void> updateRoleStatus(@PathVariable Long id, @Valid @RequestBody AdminRecruitRoleStatusChangeDTO dto) {
        adminRecruitGovernanceService.updateRoleStatus(id, dto);
        return R.ok();
    }
}
