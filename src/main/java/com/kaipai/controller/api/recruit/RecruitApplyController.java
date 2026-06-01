package com.kaipai.controller.api.recruit;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.model.recruit.dto.RecruitApplyQueryDTO;
import com.kaipai.model.recruit.dto.RecruitApplyRejectDTO;
import com.kaipai.model.recruit.dto.RecruitApplyRespDTO;
import com.kaipai.model.recruit.dto.RecruitApplySubmitDTO;
import com.kaipai.service.recruit.RecruitApplyService;
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

@Tag(name = "演员投递管理")
@RestController
@RequestMapping("/apply")
@RequiredArgsConstructor
public class RecruitApplyController {

    private final RecruitApplyService recruitApplyService;

    @Operation(summary = "提交投递")
    @PostMapping
    public R<RecruitApplyRespDTO> submit(Authentication authentication, @Valid @RequestBody RecruitApplySubmitDTO dto) {
        return R.ok(recruitApplyService.submit(currentUserId(authentication), dto.getRoleId(), dto.getRemark()));
    }

    @Operation(summary = "取消投递")
    @DeleteMapping("/{applyId}")
    public R<Void> cancel(Authentication authentication, @PathVariable Long applyId) {
        recruitApplyService.cancel(currentUserId(authentication), applyId);
        return R.ok();
    }

    @Operation(summary = "我的投递列表")
    @GetMapping("/mine")
    public R<PageResult<RecruitApplyRespDTO>> mine(Authentication authentication, RecruitApplyQueryDTO query) {
        return R.ok(recruitApplyService.myApplies(currentUserId(authentication), query));
    }

    @Operation(summary = "角色投递列表")
    @GetMapping("/role/{roleId}")
    public R<PageResult<RecruitApplyRespDTO>> roleApplies(Authentication authentication,
                                                          @PathVariable Long roleId,
                                                          RecruitApplyQueryDTO query) {
        return R.ok(recruitApplyService.roleApplies(currentUserId(authentication), roleId, query));
    }

    @Operation(summary = "投递详情")
    @GetMapping("/{applyId}")
    public R<RecruitApplyRespDTO> detail(Authentication authentication, @PathVariable Long applyId) {
        return R.ok(recruitApplyService.detail(currentUserId(authentication), applyId));
    }

    @Operation(summary = "通过投递")
    @PutMapping("/{applyId}/approve")
    public R<Void> approve(Authentication authentication, @PathVariable Long applyId) {
        recruitApplyService.approve(currentUserId(authentication), applyId);
        return R.ok();
    }

    @Operation(summary = "拒绝投递")
    @PutMapping("/{applyId}/reject")
    public R<Void> reject(Authentication authentication,
                          @PathVariable Long applyId,
                          @RequestBody(required = false) RecruitApplyRejectDTO dto) {
        recruitApplyService.reject(currentUserId(authentication), applyId, dto == null ? null : dto.getRemark());
        return R.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
