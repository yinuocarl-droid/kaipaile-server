package com.kaipai.controller.admin.verify;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.model.verify.dto.IdentityVerificationAuditReqDTO;
import com.kaipai.model.verify.dto.IdentityVerificationDetailRespDTO;
import com.kaipai.model.verify.dto.IdentityVerificationListItemDTO;
import com.kaipai.model.verify.dto.IdentityVerificationListReqDTO;
import com.kaipai.service.verify.IdentityVerificationService;
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

@Tag(name = "后台实名认证审核")
@RestController
@RequestMapping("/admin/verify")
@RequiredArgsConstructor
public class AdminVerifyController {

    private final IdentityVerificationService identityVerificationService;

    @Operation(summary = "实名认证列表")
    @GetMapping("/list")
    @PreAuthorize("hasAnyAuthority('page.verify.pending','page.verify.history')")
    public R<PageResult<IdentityVerificationListItemDTO>> list(@Valid IdentityVerificationListReqDTO req) {
        return R.ok(identityVerificationService.adminList(req));
    }

    @Operation(summary = "实名认证详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('page.verify.detail')")
    public R<IdentityVerificationDetailRespDTO> detail(@PathVariable Long id) {
        return R.ok(identityVerificationService.adminDetail(id));
    }

    @Operation(summary = "审核通过")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('action.verify.approve')")
    public R<Void> approve(@PathVariable Long id, @Valid @RequestBody IdentityVerificationAuditReqDTO req) {
        identityVerificationService.approve(id, req);
        return R.ok();
    }

    @Operation(summary = "审核拒绝")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('action.verify.reject')")
    public R<Void> reject(@PathVariable Long id, @Valid @RequestBody IdentityVerificationAuditReqDTO req) {
        identityVerificationService.reject(id, req);
        return R.ok();
    }
}
