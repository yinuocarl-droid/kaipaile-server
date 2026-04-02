package com.kaipai.module.controller.recruit;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.recruit.dto.RoleQueryDTO;
import com.kaipai.module.model.recruit.dto.RoleRespDTO;
import com.kaipai.module.model.recruit.dto.RoleSaveDTO;
import com.kaipai.module.server.recruit.service.MiniProgramRecruitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "招募帖子管理")
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RecruitPostController {

    private final MiniProgramRecruitService miniProgramRecruitService;

    @Operation(summary = "演员端角色搜索")
    @GetMapping("/search")
    public R<PageResult<RoleRespDTO>> search(RoleQueryDTO query) {
        return R.ok(miniProgramRecruitService.searchRoles(query));
    }

    @Operation(summary = "演员端角色详情")
    @GetMapping("/{roleId}")
    public R<RoleRespDTO> detail(@PathVariable Long roleId) {
        return R.ok(miniProgramRecruitService.role(roleId));
    }

    @Operation(summary = "剧组端创建角色")
    @PostMapping
    public R<RoleRespDTO> create(Authentication authentication, @Valid @RequestBody RoleSaveDTO dto) {
        return R.ok(miniProgramRecruitService.createRole(currentUserId(authentication), dto));
    }

    @Operation(summary = "剧组端更新角色")
    @PutMapping("/{roleId}")
    public R<Void> update(Authentication authentication, @PathVariable Long roleId, @Valid @RequestBody RoleSaveDTO dto) {
        miniProgramRecruitService.updateRole(currentUserId(authentication), roleId, dto);
        return R.ok();
    }

    @Operation(summary = "剧组端删除角色")
    @DeleteMapping("/{roleId}")
    public R<Void> delete(Authentication authentication, @PathVariable Long roleId) {
        miniProgramRecruitService.deleteRole(currentUserId(authentication), roleId);
        return R.ok();
    }

    @Operation(summary = "按项目查询角色")
    @GetMapping("/project/{projectId}")
    public R<PageResult<RoleRespDTO>> rolesByProject(Authentication authentication,
                                                     @PathVariable Long projectId,
                                                     RoleQueryDTO query) {
        return R.ok(miniProgramRecruitService.rolesByProject(currentUserId(authentication), projectId, query));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
