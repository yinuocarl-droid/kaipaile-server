package com.kaipai.service.referral;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.referral.dto.ActorInviteStatsRespDTO;
import com.kaipai.model.referral.dto.ActorReferralRecordRespDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskDecisionDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskDetailDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskItemDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskQueryDTO;
import com.kaipai.model.referral.dto.AdminReferralRecordDetailDTO;
import com.kaipai.model.referral.dto.AdminReferralRecordItemDTO;
import com.kaipai.model.referral.dto.AdminReferralRecordQueryDTO;
import com.kaipai.model.referral.entity.ReferralRecord;

import java.util.List;

public interface ReferralRecordService extends IService<ReferralRecord> {

    ActorInviteStatsRespDTO actorStats(Long userId);

    List<ActorReferralRecordRespDTO> actorRecords(Long userId);

    void reconcileInviteeReferral(Long inviteeUserId);

    int countValidInviteCount(Long userId);

    PageResult<AdminReferralRecordItemDTO> adminRecordList(AdminReferralRecordQueryDTO query);

    AdminReferralRecordDetailDTO adminRecordDetail(Long referralId);

    PageResult<AdminReferralRiskItemDTO> adminRiskList(AdminReferralRiskQueryDTO query);

    AdminReferralRiskDetailDTO adminRiskDetail(Long referralId);

    void approveRisk(Long referralId, AdminReferralRiskDecisionDTO request);

    void invalidateRisk(Long referralId, AdminReferralRiskDecisionDTO request);

    void resolveRisk(Long referralId, AdminReferralRiskDecisionDTO request);
}
