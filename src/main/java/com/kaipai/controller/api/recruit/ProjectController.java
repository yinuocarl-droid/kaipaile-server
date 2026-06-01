package com.kaipai.controller.api.recruit;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.model.recruit.dto.ProjectQueryDTO;
import com.kaipai.model.recruit.dto.ProjectRespDTO;
import com.kaipai.model.recruit.dto.ProjectSaveDTO;
import com.kaipai.service.recruit.MiniProgramRecruitService;
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

@Tag(name = "项目管理")
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectController {

    private final MiniProgramRecruitService miniProgramRecruitService;

    @Operation(summary = "剧组端创建项目")
    @PostMapping
    public R<ProjectRespDTO> create(Authentication authentication, @Valid @RequestBody ProjectSaveDTO dto) {
        return R.ok(miniProgramRecruitService.createProject(currentUserId(authentication), dto));
    }

    @Operation(summary = "剧组端更新项目")
    @PutMapping("/{projectId}")
    public R<Void> update(Authentication authentication, @PathVariable Long projectId, @Valid @RequestBody ProjectSaveDTO dto) {
        miniProgramRecruitService.updateProject(currentUserId(authentication), projectId, dto);
        return R.ok();
    }

    @Operation(summary = "剧组端删除项目")
    @DeleteMapping("/{projectId}")
    public R<Void> delete(Authentication authentication, @PathVariable Long projectId) {
        miniProgramRecruitService.deleteProject(currentUserId(authentication), projectId);
        return R.ok();
    }

    @Operation(summary = "剧组端项目详情")
    @GetMapping("/{projectId}")
    public R<ProjectRespDTO> detail(Authentication authentication, @PathVariable Long projectId) {
        return R.ok(miniProgramRecruitService.project(currentUserId(authentication), projectId));
    }

    @Operation(summary = "我的项目列表")
    @GetMapping("/mine")
    public R<PageResult<ProjectRespDTO>> mine(Authentication authentication, ProjectQueryDTO query) {
        return R.ok(miniProgramRecruitService.myProjects(currentUserId(authentication), query));
    }

    @Operation(summary = "项目列表")
    @GetMapping("/list")
    public R<PageResult<ProjectRespDTO>> list(ProjectQueryDTO query) {
        return R.ok(miniProgramRecruitService.projectList(query));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
