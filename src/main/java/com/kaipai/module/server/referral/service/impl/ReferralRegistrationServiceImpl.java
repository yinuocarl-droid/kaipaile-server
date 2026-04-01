package com.kaipai.module.server.referral.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.auth.dto.RegisterReqDTO;
import com.kaipai.module.model.referral.entity.InviteCode;
import com.kaipai.module.model.referral.entity.ReferralPolicy;
import com.kaipai.module.model.referral.entity.ReferralRecord;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.referral.mapper.InviteCodeMapper;
import com.kaipai.module.server.referral.mapper.ReferralPolicyMapper;
import com.kaipai.module.server.referral.mapper.ReferralRecordMapper;
import com.kaipai.module.server.referral.service.ReferralRegistrationService;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReferralRegistrationServiceImpl implements ReferralRegistrationService {

    private static final int INVITE_CODE_STATUS_ACTIVE = 1;
    private static final int REAL_AUTH_APPROVED = 2;
    private static final int REFERRAL_STATUS_PENDING = 0;
    private static final int REFERRAL_STATUS_UNDER_REVIEW = 3;
    private static final int RISK_FLAG_NORMAL = 0;
    private static final int RISK_FLAG_REVIEW = 1;
    private static final int DEFAULT_SAME_DEVICE_LIMIT = 1;
    private static final int DEFAULT_HOURLY_INVITE_LIMIT = 5;
    private static final String RISK_REASON_SAME_DEVICE_LIMIT = "same_device_limit";
    private static final String RISK_REASON_HOURLY_INVITE_LIMIT = "hourly_invite_limit";

    private final InviteCodeMapper inviteCodeMapper;
    private final ReferralRecordMapper referralRecordMapper;
    private final ReferralPolicyMapper referralPolicyMapper;
    private final UserMapper userMapper;

    @Override
    public void bindInviteOnRegister(User user, RegisterReqDTO registerReq) {
        user.setRegisterDeviceFingerprint(normalizeDeviceFingerprint(registerReq.getDeviceFingerprint()));
        if (!StringUtils.hasText(registerReq.getInviteCode())) {
            return;
        }

        InviteCode inviteCode = resolveInviteCode(registerReq.getInviteCode());
        User inviter = requireInviter(inviteCode.getUserId());
        user.setInvitedByUserId(inviter.getUserId());
        if (user.getUserId() == null) {
            return;
        }
        persistReferralRecord(user, inviteCode);
    }

    private InviteCode resolveInviteCode(String rawInviteCode) {
        String normalizedCode = rawInviteCode.trim().toUpperCase();
        InviteCode inviteCode = inviteCodeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getCode, normalizedCode)
                .eq(InviteCode::getStatus, INVITE_CODE_STATUS_ACTIVE)
                .last("limit 1"));
        if (inviteCode == null) {
            throw new BizException("邀请码无效");
        }
        return inviteCode;
    }

    private User requireInviter(Long inviterUserId) {
        User inviter = userMapper.selectById(inviterUserId);
        if (inviter == null) {
            throw new BizException("邀请人不存在");
        }
        if (!Objects.equals(inviter.getRealAuthStatus(), REAL_AUTH_APPROVED)) {
            throw new BizException("邀请人未完成实名认证");
        }
        return inviter;
    }

    private void persistReferralRecord(User invitee, InviteCode inviteCode) {
        ReferralPolicy activePolicy = selectActivePolicy();
        ReferralRecord record = new ReferralRecord();
        record.setInviterUserId(inviteCode.getUserId());
        record.setInviteeUserId(invitee.getUserId());
        record.setInviteCodeId(inviteCode.getInviteCodeId());
        record.setInviteCodeSnapshot(inviteCode.getCode());
        record.setRegisterDeviceFingerprint(invitee.getRegisterDeviceFingerprint());
        record.setRegisteredAt(resolveRegisteredAt(invitee));

        String riskReason = detectRisk(record, activePolicy);
        if (riskReason == null) {
            record.setStatus(REFERRAL_STATUS_PENDING);
            record.setRiskFlag(RISK_FLAG_NORMAL);
        } else {
            record.setStatus(REFERRAL_STATUS_UNDER_REVIEW);
            record.setRiskFlag(RISK_FLAG_REVIEW);
            record.setRiskReason(riskReason);
        }

        referralRecordMapper.insert(record);
    }

    private ReferralPolicy selectActivePolicy() {
        return referralPolicyMapper.selectOne(new LambdaQueryWrapper<ReferralPolicy>()
                .eq(ReferralPolicy::getEnabled, 1)
                .orderByDesc(ReferralPolicy::getLastUpdate)
                .orderByDesc(ReferralPolicy::getPolicyId)
                .last("limit 1"));
    }

    private String detectRisk(ReferralRecord record, ReferralPolicy activePolicy) {
        if (hitsSameDeviceLimit(record, activePolicy)) {
            return RISK_REASON_SAME_DEVICE_LIMIT;
        }
        if (hitsHourlyInviteLimit(record, activePolicy)) {
            return RISK_REASON_HOURLY_INVITE_LIMIT;
        }
        return null;
    }

    private boolean hitsSameDeviceLimit(ReferralRecord record, ReferralPolicy activePolicy) {
        if (!StringUtils.hasText(record.getRegisterDeviceFingerprint())) {
            return false;
        }
        Integer limit = activePolicy == null ? null : activePolicy.getSameDeviceLimit();
        int effectiveLimit = limit == null || limit <= 0 ? DEFAULT_SAME_DEVICE_LIMIT : limit;
        Long existingCount = referralRecordMapper.selectCount(new LambdaQueryWrapper<ReferralRecord>()
                .eq(ReferralRecord::getRegisterDeviceFingerprint, record.getRegisterDeviceFingerprint()));
        return existingCount != null && existingCount >= effectiveLimit;
    }

    private boolean hitsHourlyInviteLimit(ReferralRecord record, ReferralPolicy activePolicy) {
        LocalDateTime registeredAt = record.getRegisteredAt();
        if (registeredAt == null) {
            return false;
        }
        Integer limit = activePolicy == null ? null : activePolicy.getHourlyInviteLimit();
        int effectiveLimit = limit == null || limit <= 0 ? DEFAULT_HOURLY_INVITE_LIMIT : limit;
        LocalDateTime hourStart = registeredAt.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime hourEnd = hourStart.plusHours(1);
        Long existingCount = referralRecordMapper.selectCount(new LambdaQueryWrapper<ReferralRecord>()
                .eq(ReferralRecord::getInviteCodeSnapshot, record.getInviteCodeSnapshot())
                .ge(ReferralRecord::getRegisteredAt, hourStart)
                .lt(ReferralRecord::getRegisteredAt, hourEnd));
        return existingCount != null && existingCount >= effectiveLimit;
    }

    private LocalDateTime resolveRegisteredAt(User user) {
        return user.getCreateTime() != null ? user.getCreateTime() : LocalDateTime.now();
    }

    private String normalizeDeviceFingerprint(String deviceFingerprint) {
        if (!StringUtils.hasText(deviceFingerprint)) {
            return null;
        }
        return deviceFingerprint.trim();
    }
}
