package com.kaipai.module.server.referral.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.referral.dto.ActorInviteStatsRespDTO;
import com.kaipai.module.model.referral.dto.ActorReferralRecordRespDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRiskDecisionDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRiskDetailDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRiskItemDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRiskQueryDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRecordDetailDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRecordItemDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRecordQueryDTO;
import com.kaipai.module.model.referral.entity.ReferralRecord;

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
