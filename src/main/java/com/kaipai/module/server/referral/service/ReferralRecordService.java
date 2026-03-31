package com.kaipai.module.server.referral.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.referral.dto.AdminReferralRiskDecisionDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRiskDetailDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRiskItemDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRiskQueryDTO;
import com.kaipai.module.model.referral.entity.ReferralRecord;

public interface ReferralRecordService extends IService<ReferralRecord> {

    PageResult<AdminReferralRiskItemDTO> adminRiskList(AdminReferralRiskQueryDTO query);

    AdminReferralRiskDetailDTO adminRiskDetail(Long referralId);

    void approveRisk(Long referralId, AdminReferralRiskDecisionDTO request);

    void invalidateRisk(Long referralId, AdminReferralRiskDecisionDTO request);

    void resolveRisk(Long referralId, AdminReferralRiskDecisionDTO request);
}
