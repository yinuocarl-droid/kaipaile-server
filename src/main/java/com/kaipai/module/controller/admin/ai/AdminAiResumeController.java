package com.kaipai.module.controller.admin.ai;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureActionDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureItemDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureQueryDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeHistoryItemDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeHistoryQueryDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeOverviewDTO;
import com.kaipai.module.server.ai.service.AdminAiResumeGovernanceService;
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

@Tag(name = "后台 AI 简历治理")
@RestController
@RequestMapping("/admin/ai/resume")
@RequiredArgsConstructor
public class AdminAiResumeController {

    private final AdminAiResumeGovernanceService adminAiResumeGovernanceService;

    @Operation(summary = "AI 简历治理概览")
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('page.system.ai-resume-governance') or hasAuthority('page.system.operation-logs')")
    public R<AdminAiResumeOverviewDTO> overview() {
        return R.ok(adminAiResumeGovernanceService.overview());
    }

    @Operation(summary = "AI 简历治理历史列表")
    @GetMapping("/histories")
    @PreAuthorize("hasAuthority('page.system.ai-resume-governance') or hasAuthority('page.system.operation-logs')")
    public R<PageResult<AdminAiResumeHistoryItemDTO>> histories(@Valid AdminAiResumeHistoryQueryDTO query) {
        return R.ok(adminAiResumeGovernanceService.history(query));
    }

    @Operation(summary = "AI 简历治理历史详情")
    @GetMapping("/histories/{historyId}")
    @PreAuthorize("hasAuthority('page.system.ai-resume-governance') or hasAuthority('page.system.operation-logs')")
    public R<AdminAiResumeHistoryItemDTO> historyDetail(@PathVariable String historyId) {
        return R.ok(adminAiResumeGovernanceService.historyDetail(historyId));
    }

    @Operation(summary = "AI 简历失败样本")
    @GetMapping("/failures")
    @PreAuthorize("hasAuthority('page.system.ai-resume-governance') or hasAuthority('page.system.operation-logs')")
    public R<java.util.List<AdminAiResumeFailureItemDTO>> failures(@Valid AdminAiResumeFailureQueryDTO query) {
        return R.ok(adminAiResumeGovernanceService.failures(query));
    }

    @Operation(summary = "AI 简历敏感命中样本")
    @GetMapping("/sensitive-hits")
    @PreAuthorize("hasAuthority('page.system.ai-resume-governance') or hasAuthority('page.system.operation-logs')")
    public R<java.util.List<AdminAiResumeFailureItemDTO>> sensitiveHits(@Valid AdminAiResumeFailureQueryDTO query) {
        return R.ok(adminAiResumeGovernanceService.sensitiveHits(query));
    }

    @Operation(summary = "AI 简历失败样本人工作复核")
    @PostMapping("/failures/{failureId}/review")
    @PreAuthorize("hasAuthority('action.system.ai-resume.review') or hasAuthority('page.system.operation-logs')")
    public R<AdminAiResumeFailureItemDTO> reviewFailure(@PathVariable String failureId,
                                                        @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.reviewFailure(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本标记建议重试")
    @PostMapping("/failures/{failureId}/suggest-retry")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve') or hasAuthority('page.system.operation-logs')")
    public R<AdminAiResumeFailureItemDTO> suggestRetry(@PathVariable String failureId,
                                                       @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.suggestRetry(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }
}
