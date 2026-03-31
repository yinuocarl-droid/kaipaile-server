package com.kaipai.module.controller.referral;

import com.kaipai.module.server.referral.service.EntitlementRuleService;
import com.kaipai.module.server.referral.service.InviteCodeService;
import com.kaipai.module.server.referral.service.ReferralPolicyService;
import com.kaipai.module.server.referral.service.ReferralRecordService;
import com.kaipai.module.server.referral.service.UserEntitlementGrantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "裂变邀请")
@RestController
@RequestMapping("/referral")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralRecordService referralRecordService;

    private final InviteCodeService inviteCodeService;

    private final ReferralPolicyService referralPolicyService;

    private final UserEntitlementGrantService userEntitlementGrantService;

    private final EntitlementRuleService entitlementRuleService;
}
