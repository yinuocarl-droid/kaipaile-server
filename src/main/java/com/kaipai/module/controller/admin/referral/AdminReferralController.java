package com.kaipai.module.controller.admin.referral;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantExtendRequestDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantGrantRequestDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantItemDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantListQueryDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantRevokeRequestDTO;
import com.kaipai.module.model.referral.entity.UserEntitlementGrant;
import com.kaipai.module.server.referral.service.EntitlementRuleService;
import com.kaipai.module.server.referral.service.InviteCodeService;
import com.kaipai.module.server.referral.service.ReferralPolicyService;
import com.kaipai.module.server.referral.service.ReferralRecordService;
import com.kaipai.module.server.referral.service.UserEntitlementGrantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @Operation(summary = "资格列表")
    @GetMapping("/eligibility")
    @PreAuthorize("hasAuthority('page.referral.eligibility')")
    public R<PageResult<UserEntitlementGrantItemDTO>> eligibility(
            UserEntitlementGrantListQueryDTO queryDTO) {
        return R.ok(userEntitlementGrantService.adminGrantList(queryDTO));
    }

    @Operation(summary = "手工发放资格")
    @PostMapping("/eligibility/grant")
    @PreAuthorize("hasAuthority('action.referral.eligibility.grant')")
    public R<UserEntitlementGrantItemDTO> grant(@Valid @RequestBody UserEntitlementGrantGrantRequestDTO request) {
        UserEntitlementGrant entity = userEntitlementGrantService.grantManual(request);
        return R.ok(toDTO(entity));
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

    private UserEntitlementGrantItemDTO toDTO(UserEntitlementGrant grant) {
        return new UserEntitlementGrantItemDTO(
                grant.getGrantId(),
                grant.getUserId(),
                grant.getGrantType(),
                grant.getGrantCode(),
                grant.getStatus(),
                grant.getEffectiveTime(),
                grant.getExpireTime(),
                grant.getSourceType(),
                grant.getSourceRefId(),
                grant.getRemark()
        );
    }
}
