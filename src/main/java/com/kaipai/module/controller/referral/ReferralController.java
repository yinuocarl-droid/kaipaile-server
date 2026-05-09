package com.kaipai.module.controller.referral;

import com.kaipai.common.result.R;
import com.kaipai.module.model.referral.dto.ActorInviteInfoRespDTO;
import com.kaipai.module.model.referral.dto.ActorInviteStatsRespDTO;
import com.kaipai.module.model.referral.dto.ActorReferralRecordRespDTO;
import com.kaipai.module.model.referral.dto.InviteQrCodeResult;
import com.kaipai.module.model.referral.entity.InviteCode;
import com.kaipai.module.server.referral.service.EntitlementRuleService;
import com.kaipai.module.server.referral.service.InviteCodeService;
import com.kaipai.module.server.referral.service.InviteQrCodeService;
import com.kaipai.module.server.referral.service.ReferralPolicyService;
import com.kaipai.module.server.referral.service.ReferralRecordService;
import com.kaipai.module.server.referral.service.UserEntitlementGrantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "裂变邀请")
@RestController
@RequestMapping("/referral")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralRecordService referralRecordService;

    private final InviteCodeService inviteCodeService;

    private final InviteQrCodeService inviteQrCodeService;

    private final ReferralPolicyService referralPolicyService;

    private final UserEntitlementGrantService userEntitlementGrantService;

    private final EntitlementRuleService entitlementRuleService;

    @Operation(summary = "获取邀请信息")
    @GetMapping("/code")
    public R<ActorInviteInfoRespDTO> inviteInfo(Authentication authentication) {
        Long userId = currentUserId(authentication);
        ActorInviteStatsRespDTO stats = referralRecordService.actorStats(userId);
        InviteCode inviteCode = inviteCodeService.ensureActiveInviteCode(userId);
        String inviteLink = "/pages/login/index?inviteCode=" + inviteCode.getCode();
        InviteQrCodeResult qrCodeResult = inviteQrCodeService.buildInviteQrCode(inviteCode.getCode());

        ActorInviteInfoRespDTO dto = new ActorInviteInfoRespDTO();
        dto.setInviteCode(inviteCode.getCode());
        dto.setInviteLink(inviteLink);
        dto.setQrCodeUrl(qrCodeResult.getDataUrl());
        dto.setQrCodeType(qrCodeResult.getQrCodeType());
        dto.setQrCodeScene(qrCodeResult.getScene());
        dto.setQrCodePage(qrCodeResult.getPage());
        dto.setValidInviteCount(stats.getValidInviteCount());
        dto.setTotalInviteCount(stats.getTotalInviteCount());
        dto.setPendingInviteCount(stats.getPendingInviteCount());
        dto.setFlaggedInviteCount(stats.getFlaggedInviteCount());
        return R.ok(dto);
    }

    @Operation(summary = "获取邀请统计")
    @GetMapping("/stats")
    public R<ActorInviteStatsRespDTO> inviteStats(Authentication authentication) {
        return R.ok(referralRecordService.actorStats(currentUserId(authentication)));
    }

    @Operation(summary = "获取邀请记录")
    @GetMapping("/records")
    public R<List<ActorReferralRecordRespDTO>> inviteRecords(Authentication authentication) {
        return R.ok(referralRecordService.actorRecords(currentUserId(authentication)));
    }

    @Operation(summary = "获取邀请二维码")
    @GetMapping("/qrcode")
    public R<String> inviteQrCode(Authentication authentication) {
        InviteCode inviteCode = inviteCodeService.ensureActiveInviteCode(currentUserId(authentication));
        return R.ok(inviteQrCodeService.buildInviteQrCode(inviteCode.getCode()).getDataUrl());
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new com.kaipai.common.exception.BizException("未登录或登录态失效");
        }
        return userId;
    }
}
