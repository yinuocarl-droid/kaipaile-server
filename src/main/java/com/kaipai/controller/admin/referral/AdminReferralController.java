package com.kaipai.controller.admin.referral;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.model.referral.dto.AdminReferralRecordDetailDTO;
import com.kaipai.model.referral.dto.AdminReferralRecordItemDTO;
import com.kaipai.model.referral.dto.AdminReferralRecordQueryDTO;
import com.kaipai.model.referral.dto.AdminReferralPolicyDetailDTO;
import com.kaipai.model.referral.dto.AdminReferralPolicyQueryDTO;
import com.kaipai.model.referral.dto.AdminReferralPolicySaveDTO;
import com.kaipai.model.referral.dto.AdminReferralPolicyStatusDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskDecisionDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskDetailDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskItemDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskQueryDTO;
import com.kaipai.model.referral.dto.UserEntitlementGrantExtendRequestDTO;
import com.kaipai.model.referral.dto.UserEntitlementGrantGrantRequestDTO;
import com.kaipai.model.referral.dto.UserEntitlementGrantDetailDTO;
import com.kaipai.model.referral.dto.UserEntitlementGrantItemDTO;
import com.kaipai.model.referral.dto.UserEntitlementGrantListQueryDTO;
import com.kaipai.model.referral.dto.UserEntitlementGrantRevokeRequestDTO;
import com.kaipai.service.referral.EntitlementRuleService;
import com.kaipai.service.referral.InviteCodeService;
import com.kaipai.service.referral.ReferralPolicyService;
import com.kaipai.service.referral.ReferralRecordService;
import com.kaipai.service.referral.UserEntitlementGrantService;
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
import org.springframework.web.bind.annotation.PutMapping;

@Tag(name = "后台邀请裂变")
@RestController
@RequestMapping("/admin/referral")
@RequiredArgsConstructor
public class AdminReferralController {

    private final ReferralRecordService referralRecordService;
    private final InviteCodeService inviteCodeService;
    private final ReferralPolicyService referralPolicyService;
    private final UserEntitlementGrantService userEntitlementGrantService;
    private final EntitlementRuleService entitlementRuleService;

    @Operation(summary = "邀请记录列表")
    @GetMapping("/records")
    @PreAuthorize("hasAuthority('page.referral.records')")
    public R<PageResult<AdminReferralRecordItemDTO>> recordList(@Valid AdminReferralRecordQueryDTO query) {
        return R.ok(referralRecordService.adminRecordList(query));
    }

    @Operation(summary = "邀请记录详情")
    @GetMapping("/records/{id}")
    @PreAuthorize("hasAuthority('page.referral.records')")
    public R<AdminReferralRecordDetailDTO> recordDetail(@PathVariable Long id) {
        return R.ok(referralRecordService.adminRecordDetail(id));
    }

    @Operation(summary = "异常邀请列表")
    @GetMapping("/risk/list")
    @PreAuthorize("hasAuthority('page.referral.risk')")
    public R<PageResult<AdminReferralRiskItemDTO>> riskList(@Valid AdminReferralRiskQueryDTO query) {
        return R.ok(referralRecordService.adminRiskList(query));
    }

    @Operation(summary = "异常邀请详情")
    @GetMapping("/risk/{id}")
    @PreAuthorize("hasAuthority('page.referral.risk')")
    public R<AdminReferralRiskDetailDTO> riskDetail(@PathVariable Long id) {
        return R.ok(referralRecordService.adminRiskDetail(id));
    }

    @Operation(summary = "异常邀请通过")
    @PostMapping("/risk/{id}/approve")
    @PreAuthorize("hasAuthority('action.referral.risk.approve')")
    public R<Void> approveRisk(@PathVariable Long id, @RequestBody(required = false) AdminReferralRiskDecisionDTO request) {
        referralRecordService.approveRisk(id, request == null ? new AdminReferralRiskDecisionDTO() : request);
        return R.ok();
    }

    @Operation(summary = "异常邀请作废")
    @PostMapping("/risk/{id}/invalidate")
    @PreAuthorize("hasAuthority('action.referral.risk.invalidate')")
    public R<Void> invalidateRisk(@PathVariable Long id, @RequestBody(required = false) AdminReferralRiskDecisionDTO request) {
        referralRecordService.invalidateRisk(id, request == null ? new AdminReferralRiskDecisionDTO() : request);
        return R.ok();
    }

    @Operation(summary = "标记异常邀请复核完成")
    @PostMapping("/risk/{id}/resolve")
    @PreAuthorize("hasAuthority('action.referral.risk.resolve')")
    public R<Void> resolveRisk(@PathVariable Long id, @RequestBody(required = false) AdminReferralRiskDecisionDTO request) {
        referralRecordService.resolveRisk(id, request == null ? new AdminReferralRiskDecisionDTO() : request);
        return R.ok();
    }

    @Operation(summary = "邀请规则列表")
    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('page.referral.policies')")
    public R<PageResult<AdminReferralPolicyDetailDTO>> policyList(@Valid AdminReferralPolicyQueryDTO query) {
        return R.ok(referralPolicyService.adminPolicyList(query));
    }

    @Operation(summary = "邀请规则详情")
    @GetMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('page.referral.policies')")
    public R<AdminReferralPolicyDetailDTO> policyDetail(@PathVariable Long id) {
        return R.ok(referralPolicyService.adminPolicyDetail(id));
    }

    @Operation(summary = "创建邀请规则")
    @PostMapping("/policies")
    @PreAuthorize("hasAuthority('action.referral.policy.create')")
    public R<AdminReferralPolicyDetailDTO> createPolicy(@Valid @RequestBody AdminReferralPolicySaveDTO dto) {
        return R.ok(referralPolicyService.createPolicy(dto));
    }

    @Operation(summary = "更新邀请规则")
    @PutMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('action.referral.policy.edit')")
    public R<AdminReferralPolicyDetailDTO> updatePolicy(@PathVariable Long id, @Valid @RequestBody AdminReferralPolicySaveDTO dto) {
        return R.ok(referralPolicyService.updatePolicy(id, dto));
    }

    @Operation(summary = "启用邀请规则")
    @PostMapping("/policies/{id}/enable")
    @PreAuthorize("hasAuthority('action.referral.policy.enable')")
    public R<AdminReferralPolicyDetailDTO> enablePolicy(@PathVariable Long id,
                                                        @RequestBody(required = false) AdminReferralPolicyStatusDTO dto) {
        return R.ok(referralPolicyService.changePolicyEnabled(id, true, dto == null ? null : dto.getReason()));
    }

    @Operation(summary = "停用邀请规则")
    @PostMapping("/policies/{id}/disable")
    @PreAuthorize("hasAuthority('action.referral.policy.disable')")
    public R<AdminReferralPolicyDetailDTO> disablePolicy(@PathVariable Long id,
                                                         @RequestBody(required = false) AdminReferralPolicyStatusDTO dto) {
        return R.ok(referralPolicyService.changePolicyEnabled(id, false, dto == null ? null : dto.getReason()));
    }

    @Operation(summary = "资格列表")
    @GetMapping("/eligibility")
    @PreAuthorize("hasAuthority('page.referral.eligibility')")
    public R<PageResult<UserEntitlementGrantItemDTO>> eligibility(
            @Valid UserEntitlementGrantListQueryDTO queryDTO) {
        return R.ok(userEntitlementGrantService.adminGrantList(queryDTO));
    }

    @Operation(summary = "资格详情")
    @GetMapping("/eligibility/{grantId}")
    @PreAuthorize("hasAuthority('page.referral.eligibility')")
    public R<UserEntitlementGrantDetailDTO> eligibilityDetail(@PathVariable Long grantId) {
        return R.ok(userEntitlementGrantService.adminGrantDetail(grantId));
    }

    @Operation(summary = "手工发放资格")
    @PostMapping("/eligibility/grant")
    @PreAuthorize("hasAuthority('action.referral.eligibility.grant')")
    public R<UserEntitlementGrantItemDTO> grant(@Valid @RequestBody UserEntitlementGrantGrantRequestDTO request) {
        return R.ok(userEntitlementGrantService.adminGrantItem(userEntitlementGrantService.grantManual(request).getGrantId()));
    }

    @Operation(summary = "手工撤销资格")
    @PostMapping("/eligibility/revoke")
    @PreAuthorize("hasAuthority('action.referral.eligibility.revoke')")
    public R<Void> revoke(@Valid @RequestBody UserEntitlementGrantRevokeRequestDTO request) {
        userEntitlementGrantService.revokeManual(request);
        return R.ok();
    }

    @Operation(summary = "延期资格过期")
    @PostMapping("/eligibility/extend")
    @PreAuthorize("hasAuthority('action.referral.eligibility.extend')")
    public R<Void> extend(@Valid @RequestBody UserEntitlementGrantExtendRequestDTO request) {
        userEntitlementGrantService.extendGrant(request);
        return R.ok();
    }
}
