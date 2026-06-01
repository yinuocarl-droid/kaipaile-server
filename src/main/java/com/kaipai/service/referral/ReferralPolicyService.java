package com.kaipai.service.referral;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.referral.dto.AdminReferralPolicyDetailDTO;
import com.kaipai.model.referral.dto.AdminReferralPolicyQueryDTO;
import com.kaipai.model.referral.dto.AdminReferralPolicySaveDTO;
import com.kaipai.model.referral.entity.ReferralPolicy;

public interface ReferralPolicyService extends IService<ReferralPolicy> {

    PageResult<AdminReferralPolicyDetailDTO> adminPolicyList(AdminReferralPolicyQueryDTO query);

    AdminReferralPolicyDetailDTO adminPolicyDetail(Long policyId);

    AdminReferralPolicyDetailDTO createPolicy(AdminReferralPolicySaveDTO dto);

    AdminReferralPolicyDetailDTO updatePolicy(Long policyId, AdminReferralPolicySaveDTO dto);

    AdminReferralPolicyDetailDTO changePolicyEnabled(Long policyId, boolean enabled, String reason);
}
