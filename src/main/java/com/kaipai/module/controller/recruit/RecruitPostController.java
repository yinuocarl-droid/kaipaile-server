package com.kaipai.module.controller.recruit;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.recruit.dto.RecruitRoleQueryDTO;
import com.kaipai.module.model.recruit.dto.RecruitRoleRespDTO;
import com.kaipai.module.server.recruit.service.RecruitPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "招募帖子管理")
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RecruitPostController {

    private final RecruitPostService recruitPostService;

    @Operation(summary = "演员端角色搜索")
    @GetMapping("/search")
    public R<PageResult<RecruitRoleRespDTO>> search(RecruitRoleQueryDTO query) {
        return R.ok(recruitPostService.searchRoles(query));
    }

    @Operation(summary = "演员端角色详情")
    @GetMapping("/{roleId}")
    public R<RecruitRoleRespDTO> detail(@PathVariable Long roleId) {
        return R.ok(recruitPostService.detail(roleId));
    }
}
