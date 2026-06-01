package com.kaipai.controller.admin.ai;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.model.ai.dto.AdminAiResumeFailureActionDTO;
import com.kaipai.model.ai.dto.AdminAiResumeFailureCollaborationCatalogDTO;
import com.kaipai.model.ai.dto.AdminAiResumeFailureItemDTO;
import com.kaipai.model.ai.dto.AdminAiResumeFailureQueryDTO;
import com.kaipai.model.ai.dto.AdminAiResumeGovernanceSweepRequestDTO;
import com.kaipai.model.ai.dto.AdminAiResumeGovernanceSweepResultDTO;
import com.kaipai.model.ai.dto.AdminAiResumeHistoryItemDTO;
import com.kaipai.model.ai.dto.AdminAiResumeHistoryQueryDTO;
import com.kaipai.model.ai.dto.AdminAiResumeOverviewDTO;
import com.kaipai.service.ai.AdminAiResumeGovernanceService;
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
    @PreAuthorize("hasAuthority('page.system.ai-resume-governance')")
    public R<AdminAiResumeOverviewDTO> overview() {
        return R.ok(adminAiResumeGovernanceService.overview());
    }

    @Operation(summary = "AI 简历治理历史列表")
    @GetMapping("/histories")
    @PreAuthorize("hasAuthority('page.system.ai-resume-governance')")
    public R<PageResult<AdminAiResumeHistoryItemDTO>> histories(@Valid AdminAiResumeHistoryQueryDTO query) {
        return R.ok(adminAiResumeGovernanceService.history(query));
    }

    @Operation(summary = "AI 简历治理历史详情")
    @GetMapping("/histories/{historyId}")
    @PreAuthorize("hasAuthority('page.system.ai-resume-governance')")
    public R<AdminAiResumeHistoryItemDTO> historyDetail(@PathVariable String historyId) {
        return R.ok(adminAiResumeGovernanceService.historyDetail(historyId));
    }

    @Operation(summary = "AI 简历失败样本")
    @GetMapping("/failures")
    @PreAuthorize("hasAuthority('page.system.ai-resume-governance')")
    public R<java.util.List<AdminAiResumeFailureItemDTO>> failures(@Valid AdminAiResumeFailureQueryDTO query) {
        return R.ok(adminAiResumeGovernanceService.failures(query));
    }

    @Operation(summary = "AI 简历敏感命中样本")
    @GetMapping("/sensitive-hits")
    @PreAuthorize("hasAuthority('page.system.ai-resume-governance')")
    public R<java.util.List<AdminAiResumeFailureItemDTO>> sensitiveHits(@Valid AdminAiResumeFailureQueryDTO query) {
        return R.ok(adminAiResumeGovernanceService.sensitiveHits(query));
    }

    @Operation(summary = "AI 简历失败样本协同目录")
    @GetMapping("/collaboration-catalog")
    @PreAuthorize("hasAuthority('page.system.ai-resume-governance')")
    public R<AdminAiResumeFailureCollaborationCatalogDTO> collaborationCatalog() {
        return R.ok(adminAiResumeGovernanceService.collaborationCatalog());
    }

    @Operation(summary = "AI 简历失败样本治理规则预览")
    @PostMapping("/governance-sweep/preview")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeGovernanceSweepResultDTO> previewGovernanceSweep(
            @RequestBody(required = false) AdminAiResumeGovernanceSweepRequestDTO request) {
        return R.ok(adminAiResumeGovernanceService.previewGovernanceSweep(
                request == null ? new AdminAiResumeGovernanceSweepRequestDTO() : request
        ));
    }

    @Operation(summary = "AI 简历失败样本执行治理规则")
    @PostMapping("/governance-sweep/execute")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeGovernanceSweepResultDTO> executeGovernanceSweep(
            @RequestBody(required = false) AdminAiResumeGovernanceSweepRequestDTO request) {
        return R.ok(adminAiResumeGovernanceService.executeGovernanceSweep(
                request == null ? new AdminAiResumeGovernanceSweepRequestDTO() : request
        ));
    }

    @Operation(summary = "AI 简历失败样本人工作复核")
    @PostMapping("/failures/{failureId}/review")
    @PreAuthorize("hasAuthority('action.system.ai-resume.review')")
    public R<AdminAiResumeFailureItemDTO> reviewFailure(@PathVariable String failureId,
                                                        @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.reviewFailure(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本标记建议重试")
    @PostMapping("/failures/{failureId}/suggest-retry")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeFailureItemDTO> suggestRetry(@PathVariable String failureId,
                                                       @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.suggestRetry(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本关闭归档")
    @PostMapping("/failures/{failureId}/close")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeFailureItemDTO> closeFailure(@PathVariable String failureId,
                                                       @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.closeFailure(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本忽略")
    @PostMapping("/failures/{failureId}/ignore")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeFailureItemDTO> ignoreFailure(@PathVariable String failureId,
                                                        @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.ignoreFailure(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本分派处理人")
    @PostMapping("/failures/{failureId}/assign")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeFailureItemDTO> assignFailure(@PathVariable String failureId,
                                                        @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.assignFailure(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本确认接手")
    @PostMapping("/failures/{failureId}/acknowledge-assignment")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeFailureItemDTO> acknowledgeAssignment(@PathVariable String failureId,
                                                                @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.acknowledgeAssignment(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本人工催办")
    @PostMapping("/failures/{failureId}/remind")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeFailureItemDTO> remindFailure(@PathVariable String failureId,
                                                        @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.remindFailure(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本手工接管")
    @PostMapping("/failures/{failureId}/manual-takeover")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeFailureItemDTO> manualTakeoverFailure(@PathVariable String failureId,
                                                                @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.manualTakeoverFailure(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本跳过自动催办")
    @PostMapping("/failures/{failureId}/skip-auto-remind")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeFailureItemDTO> skipAutoRemind(@PathVariable String failureId,
                                                         @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.skipAutoRemind(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本记录通知结果")
    @PostMapping("/failures/{failureId}/record-notification")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeFailureItemDTO> recordNotification(@PathVariable String failureId,
                                                             @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.recordNotification(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本记录通知回执")
    @PostMapping("/failures/{failureId}/record-notification-receipt")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeFailureItemDTO> recordNotificationReceipt(@PathVariable String failureId,
                                                                    @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.recordNotificationReceipt(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }

    @Operation(summary = "AI 简历失败样本升级处理")
    @PostMapping("/failures/{failureId}/escalate")
    @PreAuthorize("hasAuthority('action.system.ai-resume.resolve')")
    public R<AdminAiResumeFailureItemDTO> escalateFailure(@PathVariable String failureId,
                                                          @RequestBody(required = false) AdminAiResumeFailureActionDTO action) {
        return R.ok(adminAiResumeGovernanceService.escalateFailure(
                failureId,
                action == null ? new AdminAiResumeFailureActionDTO() : action
        ));
    }
}
