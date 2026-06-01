package com.kaipai.service.referral.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.referral.dto.ActorInviteStatsRespDTO;
import com.kaipai.model.referral.dto.ActorReferralRecordRespDTO;
import com.kaipai.model.referral.dto.AdminReferralRecordDetailDTO;
import com.kaipai.model.referral.dto.AdminReferralRecordItemDTO;
import com.kaipai.model.referral.dto.AdminReferralRecordQueryDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskDecisionDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskDetailDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskItemDTO;
import com.kaipai.model.referral.dto.AdminReferralRiskQueryDTO;
import com.kaipai.model.referral.entity.InviteCode;
import com.kaipai.model.referral.entity.ReferralPolicy;
import com.kaipai.model.referral.entity.ReferralRecord;
import com.kaipai.model.referral.entity.UserEntitlementGrant;
import com.kaipai.model.system.entity.AdminOperationLog;
import com.kaipai.model.user.entity.User;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.service.actor.support.ActorProfileCompletionCalculator;
import com.kaipai.mapper.referral.InviteCodeMapper;
import com.kaipai.mapper.referral.ReferralPolicyMapper;
import com.kaipai.mapper.referral.ReferralRecordMapper;
import com.kaipai.mapper.referral.UserEntitlementGrantMapper;
import com.kaipai.service.referral.ReferralRecordService;
import com.kaipai.mapper.system.AdminOperationLogMapper;
import com.kaipai.mapper.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReferralRecordServiceImpl extends ServiceImpl<ReferralRecordMapper, ReferralRecord> implements ReferralRecordService {

    private static final int REAL_AUTH_APPROVED = 2;
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_VALID = 1;
    private static final int STATUS_INVALID = 2;
    private static final int STATUS_UNDER_REVIEW = 3;
    private static final int RISK_FLAG_NORMAL = 0;
    private static final int DEFAULT_PROFILE_COMPLETION_THRESHOLD = 70;
    private static final int GRANT_STATUS_ACTIVE = 1;

    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final InviteCodeMapper inviteCodeMapper;
    private final ReferralPolicyMapper referralPolicyMapper;
    private final UserEntitlementGrantMapper userEntitlementGrantMapper;
    private final AdminOperationLogMapper adminOperationLogMapper;
    private final AdminOperationLogger adminOperationLogger;

    @Override
    public ActorInviteStatsRespDTO actorStats(Long userId) {
        List<ReferralRecord> records = list(new LambdaQueryWrapper<ReferralRecord>()
                .eq(ReferralRecord::getInviterUserId, userId));
        ActorInviteStatsRespDTO dto = new ActorInviteStatsRespDTO();
        dto.setTotalInviteCount(records.size());
        dto.setValidInviteCount((int) records.stream().filter(record -> Objects.equals(record.getStatus(), STATUS_VALID)).count());
        dto.setFlaggedInviteCount((int) records.stream().filter(this::isFlaggedRecord).count());
        dto.setPendingInviteCount(Math.max(0, dto.getTotalInviteCount() - dto.getValidInviteCount() - dto.getFlaggedInviteCount()));
        return dto;
    }

    @Override
    public List<ActorReferralRecordRespDTO> actorRecords(Long userId) {
        List<ReferralRecord> records = list(new LambdaQueryWrapper<ReferralRecord>()
                .eq(ReferralRecord::getInviterUserId, userId)
                .orderByDesc(ReferralRecord::getRegisteredAt)
                .orderByDesc(ReferralRecord::getReferralId));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> inviteeUserIds = records.stream()
                .map(ReferralRecord::getInviteeUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> inviteeUserMap = inviteeUserIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(inviteeUserIds).stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, ActorProfile> inviteeProfileMap = inviteeUserIds.isEmpty()
                ? Collections.emptyMap()
                : actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>().in(ActorProfile::getUserId, inviteeUserIds))
                .stream()
                .collect(Collectors.toMap(ActorProfile::getUserId, Function.identity(), (left, right) -> left));

        return records.stream()
                .map(record -> toActorRecord(record, inviteeUserMap.get(record.getInviteeUserId()), inviteeProfileMap.get(record.getInviteeUserId())))
                .toList();
    }

    @Override
    @Transactional
    public void reconcileInviteeReferral(Long inviteeUserId) {
        if (inviteeUserId == null) {
            return;
        }
        ReferralRecord record = lambdaQuery()
                .eq(ReferralRecord::getInviteeUserId, inviteeUserId)
                .orderByDesc(ReferralRecord::getRegisteredAt)
                .orderByDesc(ReferralRecord::getReferralId)
                .last("limit 1")
                .one();
        if (record == null || Objects.equals(record.getStatus(), STATUS_INVALID) || isFlaggedRecord(record)) {
            return;
        }

        ReferralPolicy activePolicy = selectActivePolicy();
        if (!Objects.equals(record.getStatus(), STATUS_VALID) && isQualificationSatisfied(inviteeUserId, activePolicy)) {
            record.setStatus(STATUS_VALID);
            record.setRiskFlag(RISK_FLAG_NORMAL);
            record.setRiskReason(null);
            if (record.getValidatedAt() == null) {
                record.setValidatedAt(LocalDateTime.now());
            }
            updateById(record);
            refreshInviterValidInviteCount(record.getInviterUserId());
        }

        if (Objects.equals(record.getStatus(), STATUS_VALID)) {
            ensureAutoGrant(record, activePolicy);
        }
    }

    @Override
    public int countValidInviteCount(Long userId) {
        if (userId == null) {
            return 0;
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ReferralRecord>()
                .eq(ReferralRecord::getInviterUserId, userId)
                .eq(ReferralRecord::getStatus, STATUS_VALID));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public PageResult<AdminReferralRecordItemDTO> adminRecordList(AdminReferralRecordQueryDTO query) {
        Page<ReferralRecord> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<ReferralRecord> wrapper = buildRecordQuery(query);
        wrapper.orderByDesc(ReferralRecord::getRegisteredAt).orderByDesc(ReferralRecord::getReferralId);
        Page<ReferralRecord> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Set<Long> userIds = new LinkedHashSet<>();
        result.getRecords().forEach(record -> {
            userIds.add(record.getInviterUserId());
            userIds.add(record.getInviteeUserId());
        });
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, ActorProfile> actorProfileMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>().in(ActorProfile::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(ActorProfile::getUserId, Function.identity(), (left, right) -> left));

        List<AdminReferralRecordItemDTO> list = result.getRecords().stream()
                .map(record -> toRecordItem(record, userMap, actorProfileMap))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AdminReferralRecordDetailDTO adminRecordDetail(Long referralId) {
        ReferralRecord record = getById(referralId);
        if (record == null) {
            throw new BizException("邀请记录不存在");
        }
        User inviter = userMapper.selectById(record.getInviterUserId());
        User invitee = userMapper.selectById(record.getInviteeUserId());
        ActorProfile inviterProfile = selectActorProfile(record.getInviterUserId());
        ActorProfile inviteeProfile = selectActorProfile(record.getInviteeUserId());
        List<UserEntitlementGrant> grants = userEntitlementGrantMapper.selectList(new LambdaQueryWrapper<UserEntitlementGrant>()
                .eq(UserEntitlementGrant::getUserId, record.getInviteeUserId())
                .orderByDesc(UserEntitlementGrant::getCreateTime)
                .orderByDesc(UserEntitlementGrant::getGrantId)
                .last("limit 10"));

        AdminReferralRecordDetailDTO detail = new AdminReferralRecordDetailDTO();
        detail.setRecordInfo(toRecordDetailInfo(record));
        detail.setInviterInfo(toRecordUserInfo(inviter, inviterProfile));
        detail.setInviteeInfo(toRecordUserInfo(invitee, inviteeProfile));
        detail.setRiskInfo(buildRecordRiskInfo(record, grants));
        return detail;
    }

    @Override
    public PageResult<AdminReferralRiskItemDTO> adminRiskList(AdminReferralRiskQueryDTO query) {
        Page<ReferralRecord> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<ReferralRecord> wrapper = buildRiskQuery(query);
        wrapper.orderByDesc(ReferralRecord::getRegisteredAt).orderByDesc(ReferralRecord::getReferralId);
        Page<ReferralRecord> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Set<Long> userIds = new LinkedHashSet<>();
        result.getRecords().forEach(record -> {
            userIds.add(record.getInviterUserId());
            userIds.add(record.getInviteeUserId());
        });
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, ActorProfile> actorProfileMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>().in(ActorProfile::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(ActorProfile::getUserId, Function.identity(), (left, right) -> left));

        List<AdminReferralRiskItemDTO> list = result.getRecords().stream()
                .map(record -> toRiskItem(record, userMap, actorProfileMap))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AdminReferralRiskDetailDTO adminRiskDetail(Long referralId) {
        ReferralRecord record = getById(referralId);
        if (record == null) {
            throw new BizException("邀请记录不存在");
        }
        User inviter = userMapper.selectById(record.getInviterUserId());
        User invitee = userMapper.selectById(record.getInviteeUserId());
        ActorProfile inviterProfile = selectActorProfile(record.getInviterUserId());
        ActorProfile inviteeProfile = selectActorProfile(record.getInviteeUserId());
        InviteCode inviteCode = record.getInviteCodeId() == null ? null : inviteCodeMapper.selectById(record.getInviteCodeId());

        AdminReferralRiskDetailDTO detail = new AdminReferralRiskDetailDTO();
        detail.setRecordInfo(toRecordInfo(record));
        detail.setInviterInfo(toUserInfo(inviter, inviterProfile));
        detail.setInviteeInfo(toUserInfo(invitee, inviteeProfile));
        detail.setRiskInfo(toRiskInfo(record));
        detail.setDeviceHitSummary(buildDeviceHitSummary(record));
        detail.setSameHourHitSummary(buildSameHourHitSummary(record, inviteCode));
        detail.setHistoryLogs(loadHistoryLogs(record.getReferralId()));
        return detail;
    }

    @Override
    @Transactional
    public void approveRisk(Long referralId, AdminReferralRiskDecisionDTO request) {
        ReferralRecord record = requireOperableRiskRecord(referralId);
        Map<String, Object> beforeSnapshot = snapshot(record);
        record.setStatus(STATUS_VALID);
        record.setRiskFlag(RISK_FLAG_NORMAL);
        record.setRiskReason(null);
        if (record.getValidatedAt() == null) {
            record.setValidatedAt(LocalDateTime.now());
        }
        updateById(record);
        refreshInviterValidInviteCount(record.getInviterUserId());
        ensureAutoGrant(record, selectActivePolicy());
        logRiskAction("approve", record, beforeSnapshot, request);
    }

    @Override
    @Transactional
    public void invalidateRisk(Long referralId, AdminReferralRiskDecisionDTO request) {
        ReferralRecord record = requireOperableRiskRecord(referralId);
        Map<String, Object> beforeSnapshot = snapshot(record);
        record.setStatus(STATUS_INVALID);
        record.setRiskFlag(RISK_FLAG_NORMAL);
        updateById(record);
        refreshInviterValidInviteCount(record.getInviterUserId());
        logRiskAction("invalidate", record, beforeSnapshot, request);
    }

    @Override
    @Transactional
    public void resolveRisk(Long referralId, AdminReferralRiskDecisionDTO request) {
        ReferralRecord record = requireOperableRiskRecord(referralId);
        Map<String, Object> beforeSnapshot = snapshot(record);
        record.setStatus(record.getValidatedAt() == null ? STATUS_PENDING : STATUS_VALID);
        record.setRiskFlag(RISK_FLAG_NORMAL);
        updateById(record);
        refreshInviterValidInviteCount(record.getInviterUserId());
        if (Objects.equals(record.getStatus(), STATUS_VALID)) {
            ensureAutoGrant(record, selectActivePolicy());
        }
        logRiskAction("resolve", record, beforeSnapshot, request);
    }

    private LambdaQueryWrapper<ReferralRecord> buildRiskQuery(AdminReferralRiskQueryDTO query) {
        LambdaQueryWrapper<ReferralRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getInviteCode())) {
            wrapper.eq(ReferralRecord::getInviteCodeSnapshot, query.getInviteCode().trim());
        }
        if (query.getInviterUserId() != null) {
            wrapper.eq(ReferralRecord::getInviterUserId, query.getInviterUserId());
        }
        if (query.getInviteeUserId() != null) {
            wrapper.eq(ReferralRecord::getInviteeUserId, query.getInviteeUserId());
        }
        if (StringUtils.hasText(query.getRiskReason())) {
            wrapper.like(ReferralRecord::getRiskReason, query.getRiskReason().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(ReferralRecord::getStatus, query.getStatus());
        }
        if (query.getRiskFlag() != null) {
            wrapper.eq(ReferralRecord::getRiskFlag, query.getRiskFlag());
        }
        if (query.getRegisteredAtFrom() != null) {
            wrapper.ge(ReferralRecord::getRegisteredAt, query.getRegisteredAtFrom());
        }
        if (query.getRegisteredAtTo() != null) {
            wrapper.le(ReferralRecord::getRegisteredAt, query.getRegisteredAtTo());
        }
        return wrapper;
    }

    private LambdaQueryWrapper<ReferralRecord> buildRecordQuery(AdminReferralRecordQueryDTO query) {
        LambdaQueryWrapper<ReferralRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getInviteCode())) {
            wrapper.eq(ReferralRecord::getInviteCodeSnapshot, query.getInviteCode().trim());
        }
        if (query.getInviterUserId() != null) {
            wrapper.eq(ReferralRecord::getInviterUserId, query.getInviterUserId());
        }
        if (query.getInviteeUserId() != null) {
            wrapper.eq(ReferralRecord::getInviteeUserId, query.getInviteeUserId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(ReferralRecord::getStatus, query.getStatus());
        }
        if (query.getRiskFlag() != null) {
            wrapper.eq(ReferralRecord::getRiskFlag, query.getRiskFlag());
        }
        if (query.getRegisteredAtFrom() != null) {
            wrapper.ge(ReferralRecord::getRegisteredAt, query.getRegisteredAtFrom());
        }
        if (query.getRegisteredAtTo() != null) {
            wrapper.le(ReferralRecord::getRegisteredAt, query.getRegisteredAtTo());
        }
        if (query.getValidatedAtFrom() != null) {
            wrapper.ge(ReferralRecord::getValidatedAt, query.getValidatedAtFrom());
        }
        if (query.getValidatedAtTo() != null) {
            wrapper.le(ReferralRecord::getValidatedAt, query.getValidatedAtTo());
        }
        return wrapper;
    }

    private AdminReferralRecordItemDTO toRecordItem(ReferralRecord record,
                                                    Map<Long, User> userMap,
                                                    Map<Long, ActorProfile> actorProfileMap) {
        AdminReferralRecordItemDTO item = new AdminReferralRecordItemDTO();
        item.setReferralId(record.getReferralId());
        item.setInviterUserId(record.getInviterUserId());
        item.setInviterName(resolveDisplayName(userMap.get(record.getInviterUserId()), actorProfileMap.get(record.getInviterUserId())));
        item.setInviteCode(record.getInviteCodeSnapshot());
        item.setInviteeUserId(record.getInviteeUserId());
        item.setInviteeName(resolveDisplayName(userMap.get(record.getInviteeUserId()), actorProfileMap.get(record.getInviteeUserId())));
        item.setStatus(record.getStatus());
        item.setRiskFlag(record.getRiskFlag());
        item.setRegisteredAt(record.getRegisteredAt());
        item.setValidatedAt(record.getValidatedAt());
        return item;
    }

    private AdminReferralRiskItemDTO toRiskItem(ReferralRecord record,
                                                Map<Long, User> userMap,
                                                Map<Long, ActorProfile> actorProfileMap) {
        AdminReferralRiskItemDTO item = new AdminReferralRiskItemDTO();
        item.setReferralId(record.getReferralId());
        item.setInviteCode(record.getInviteCodeSnapshot());
        item.setInviterUserId(record.getInviterUserId());
        item.setInviteeUserId(record.getInviteeUserId());
        item.setInviterName(resolveDisplayName(userMap.get(record.getInviterUserId()), actorProfileMap.get(record.getInviterUserId())));
        item.setInviteeName(resolveDisplayName(userMap.get(record.getInviteeUserId()), actorProfileMap.get(record.getInviteeUserId())));
        item.setRiskReason(record.getRiskReason());
        item.setStatus(record.getStatus());
        item.setRiskFlag(record.getRiskFlag());
        item.setRegisteredAt(record.getRegisteredAt());
        return item;
    }

    private AdminReferralRiskDetailDTO.RecordInfo toRecordInfo(ReferralRecord record) {
        AdminReferralRiskDetailDTO.RecordInfo info = new AdminReferralRiskDetailDTO.RecordInfo();
        info.setReferralId(record.getReferralId());
        info.setInviteCode(record.getInviteCodeSnapshot());
        info.setInviteCodeId(record.getInviteCodeId());
        info.setInviterUserId(record.getInviterUserId());
        info.setInviteeUserId(record.getInviteeUserId());
        info.setStatus(record.getStatus());
        info.setRiskFlag(record.getRiskFlag());
        info.setRiskReason(record.getRiskReason());
        info.setRegisterDeviceFingerprint(record.getRegisterDeviceFingerprint());
        info.setRegisteredAt(record.getRegisteredAt());
        info.setValidatedAt(record.getValidatedAt());
        return info;
    }

    private AdminReferralRecordDetailDTO.RecordInfo toRecordDetailInfo(ReferralRecord record) {
        AdminReferralRecordDetailDTO.RecordInfo info = new AdminReferralRecordDetailDTO.RecordInfo();
        info.setReferralId(record.getReferralId());
        info.setInviteCode(record.getInviteCodeSnapshot());
        info.setInviteCodeId(record.getInviteCodeId());
        info.setStatus(record.getStatus());
        info.setRiskFlag(record.getRiskFlag());
        info.setRiskReason(record.getRiskReason());
        info.setRegisterDeviceFingerprint(record.getRegisterDeviceFingerprint());
        info.setRegisteredAt(record.getRegisteredAt());
        info.setValidatedAt(record.getValidatedAt());
        return info;
    }

    private AdminReferralRiskDetailDTO.UserInfo toUserInfo(User user, ActorProfile actorProfile) {
        if (user == null && actorProfile == null) {
            return null;
        }
        AdminReferralRiskDetailDTO.UserInfo info = new AdminReferralRiskDetailDTO.UserInfo();
        info.setUserId(user == null ? actorProfile.getUserId() : user.getUserId());
        info.setUserName(user == null ? null : user.getUserName());
        info.setPhone(user == null ? null : user.getPhone());
        info.setNickname(actorProfile != null && StringUtils.hasText(actorProfile.getNickName()) ? actorProfile.getNickName() : user == null ? null : user.getUserName());
        info.setRealAuthStatus(user == null ? null : user.getRealAuthStatus());
        info.setValidInviteCount(user == null ? null : user.getValidInviteCount());
        return info;
    }

    private AdminReferralRecordDetailDTO.UserInfo toRecordUserInfo(User user, ActorProfile actorProfile) {
        if (user == null && actorProfile == null) {
            return null;
        }
        AdminReferralRecordDetailDTO.UserInfo info = new AdminReferralRecordDetailDTO.UserInfo();
        info.setUserId(user == null ? actorProfile.getUserId() : user.getUserId());
        info.setUserName(user == null ? null : user.getUserName());
        info.setPhone(user == null ? null : user.getPhone());
        info.setNickname(actorProfile != null && StringUtils.hasText(actorProfile.getNickName()) ? actorProfile.getNickName() : user == null ? null : user.getUserName());
        info.setRealAuthStatus(user == null ? null : user.getRealAuthStatus());
        info.setValidInviteCount(user == null ? null : user.getValidInviteCount());
        return info;
    }

    private AdminReferralRiskDetailDTO.RiskInfo toRiskInfo(ReferralRecord record) {
        AdminReferralRiskDetailDTO.RiskInfo info = new AdminReferralRiskDetailDTO.RiskInfo();
        info.setCurrentStatus(record.getStatus());
        info.setRiskFlag(record.getRiskFlag());
        info.setRiskReason(record.getRiskReason());
        return info;
    }

    private AdminReferralRecordDetailDTO.RiskInfo buildRecordRiskInfo(ReferralRecord record, List<UserEntitlementGrant> grants) {
        AdminReferralRecordDetailDTO.RiskInfo info = new AdminReferralRecordDetailDTO.RiskInfo();
        info.setStatus(record.getStatus());
        info.setRiskFlag(record.getRiskFlag());
        info.setRiskReason(record.getRiskReason());
        info.setRegisterDeviceFingerprint(record.getRegisterDeviceFingerprint());
        info.setSameDeviceHitCount(buildDeviceHitSummary(record).getHitCount());
        info.setRelatedGrantCodes(grants.stream()
                .map(UserEntitlementGrant::getGrantCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList());
        return info;
    }

    private AdminReferralRiskDetailDTO.DeviceHitSummary buildDeviceHitSummary(ReferralRecord record) {
        AdminReferralRiskDetailDTO.DeviceHitSummary summary = new AdminReferralRiskDetailDTO.DeviceHitSummary();
        summary.setDeviceFingerprint(record.getRegisterDeviceFingerprint());
        if (!StringUtils.hasText(record.getRegisterDeviceFingerprint())) {
            summary.setHitCount(0);
            summary.setRelatedReferralIds(Collections.emptyList());
            return summary;
        }
        List<ReferralRecord> hitRecords = lambdaQuery()
                .eq(ReferralRecord::getRegisterDeviceFingerprint, record.getRegisterDeviceFingerprint())
                .orderByDesc(ReferralRecord::getRegisteredAt)
                .orderByDesc(ReferralRecord::getReferralId)
                .list();
        summary.setHitCount(hitRecords.size());
        summary.setRelatedReferralIds(hitRecords.stream().map(ReferralRecord::getReferralId).limit(10).toList());
        return summary;
    }

    private AdminReferralRiskDetailDTO.SameHourHitSummary buildSameHourHitSummary(ReferralRecord record, InviteCode inviteCode) {
        AdminReferralRiskDetailDTO.SameHourHitSummary summary = new AdminReferralRiskDetailDTO.SameHourHitSummary();
        summary.setInviteCode(inviteCode == null ? record.getInviteCodeSnapshot() : inviteCode.getCode());
        if (!StringUtils.hasText(record.getInviteCodeSnapshot()) || record.getRegisteredAt() == null) {
            summary.setHitCount(0);
            summary.setRelatedReferralIds(Collections.emptyList());
            return summary;
        }
        LocalDateTime hourStart = record.getRegisteredAt().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime hourEnd = hourStart.plusHours(1);
        List<ReferralRecord> hitRecords = lambdaQuery()
                .eq(ReferralRecord::getInviteCodeSnapshot, record.getInviteCodeSnapshot())
                .ge(ReferralRecord::getRegisteredAt, hourStart)
                .lt(ReferralRecord::getRegisteredAt, hourEnd)
                .orderByDesc(ReferralRecord::getRegisteredAt)
                .orderByDesc(ReferralRecord::getReferralId)
                .list();
        summary.setHourStart(hourStart);
        summary.setHourEnd(hourEnd);
        summary.setHitCount(hitRecords.size());
        summary.setRelatedReferralIds(hitRecords.stream().map(ReferralRecord::getReferralId).limit(10).toList());
        return summary;
    }

    private List<AdminReferralRiskDetailDTO.HistoryLogItem> loadHistoryLogs(Long referralId) {
        return adminOperationLogMapper.selectList(new LambdaQueryWrapper<AdminOperationLog>()
                        .eq(AdminOperationLog::getModuleCode, "referral")
                        .eq(AdminOperationLog::getTargetType, "referral_record")
                        .eq(AdminOperationLog::getTargetId, referralId)
                        .orderByDesc(AdminOperationLog::getCreateTime)
                        .orderByDesc(AdminOperationLog::getOperationLogId)
                        .last("limit 20"))
                .stream()
                .map(this::toHistoryLogItem)
                .toList();
    }

    private AdminReferralRiskDetailDTO.HistoryLogItem toHistoryLogItem(AdminOperationLog log) {
        AdminReferralRiskDetailDTO.HistoryLogItem item = new AdminReferralRiskDetailDTO.HistoryLogItem();
        item.setOperationLogId(log.getOperationLogId());
        item.setAdminUserId(log.getAdminUserId());
        item.setAdminUserName(log.getAdminUserName());
        item.setOperationCode(log.getOperationCode());
        item.setOperationResult(log.getOperationResult());
        item.setExtraContextJson(log.getExtraContextJson());
        item.setCreateTime(log.getCreateTime());
        return item;
    }

    private ReferralRecord requireOperableRiskRecord(Long referralId) {
        ReferralRecord record = getById(referralId);
        if (record == null) {
            throw new BizException("邀请记录不存在");
        }
        if (!Objects.equals(record.getStatus(), STATUS_UNDER_REVIEW)) {
            throw new BizException("只有复核中的异常邀请可以处理");
        }
        return record;
    }

    private void logRiskAction(String action,
                               ReferralRecord record,
                               Map<String, Object> beforeSnapshot,
                               AdminReferralRiskDecisionDTO request) {
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("referral")
                .operationCode(action)
                .targetType("referral_record")
                .targetId(record.getReferralId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(record))
                .extraContext(buildExtraContext(record, action, request))
                .operationResult(1)
                .build());
    }

    private Map<String, Object> snapshot(ReferralRecord record) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("referralId", record.getReferralId());
        snapshot.put("inviterUserId", record.getInviterUserId());
        snapshot.put("inviteeUserId", record.getInviteeUserId());
        snapshot.put("inviteCodeId", record.getInviteCodeId());
        snapshot.put("inviteCodeSnapshot", record.getInviteCodeSnapshot());
        snapshot.put("status", record.getStatus());
        snapshot.put("riskFlag", record.getRiskFlag());
        snapshot.put("riskReason", record.getRiskReason());
        snapshot.put("registeredAt", record.getRegisteredAt());
        snapshot.put("validatedAt", record.getValidatedAt());
        return snapshot;
    }

    private Map<String, Object> buildExtraContext(ReferralRecord record, String action, AdminReferralRiskDecisionDTO request) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("referral_record_id", record.getReferralId());
        context.put("inviter_user_id", record.getInviterUserId());
        context.put("invitee_user_id", record.getInviteeUserId());
        context.put("risk_code", record.getRiskReason());
        context.put("risk_status_after", record.getStatus());
        context.put("decision", action);
        context.put("remark", request == null ? null : request.getRemark());
        return context;
    }

    private ActorReferralRecordRespDTO toActorRecord(ReferralRecord record, User inviteeUser, ActorProfile inviteeProfile) {
        ActorReferralRecordRespDTO dto = new ActorReferralRecordRespDTO();
        dto.setId(record.getReferralId());
        dto.setInviteeNickname(maskInviteeNickname(resolveDisplayName(inviteeUser, inviteeProfile)));
        dto.setRegisteredAt(record.getRegisteredAt());
        dto.setStatus(resolveActorStatus(record));
        dto.setStatusLabel(resolveActorStatusLabel(record));
        dto.setRiskReason(record.getRiskReason());
        dto.setIsValid(Objects.equals(record.getStatus(), STATUS_VALID));
        dto.setFlagged(isFlaggedRecord(record));
        dto.setValidatedAt(record.getValidatedAt());
        return dto;
    }

    private String resolveActorStatus(ReferralRecord record) {
        return switch (record.getStatus() == null ? STATUS_PENDING : record.getStatus()) {
            case STATUS_VALID -> "valid";
            case STATUS_INVALID -> "invalid";
            case STATUS_UNDER_REVIEW -> "review";
            default -> "pending";
        };
    }

    private String resolveActorStatusLabel(ReferralRecord record) {
        return switch (resolveActorStatus(record)) {
            case "valid" -> "已生效";
            case "invalid" -> "已作废";
            case "review" -> "人工审核";
            default -> "待生效";
        };
    }

    private String maskInviteeNickname(String value) {
        String source = value == null ? "" : value.trim();
        if (source.isEmpty()) {
            return "新用户";
        }
        if (source.length() <= 1) {
            return source + "*";
        }
        if (source.length() == 2) {
            return source.charAt(0) + "*";
        }
        int maskedLength = Math.min(source.length() - 2, 3);
        return source.charAt(0) + "*".repeat(maskedLength) + source.charAt(source.length() - 1);
    }

    private boolean isFlaggedRecord(ReferralRecord record) {
        return !Objects.equals(record.getRiskFlag(), RISK_FLAG_NORMAL)
                || Objects.equals(record.getStatus(), STATUS_UNDER_REVIEW);
    }

    private boolean isQualificationSatisfied(Long inviteeUserId, ReferralPolicy activePolicy) {
        User invitee = userMapper.selectById(inviteeUserId);
        if (invitee == null) {
            return false;
        }
        if (requiresRealAuth(activePolicy) && !Objects.equals(invitee.getRealAuthStatus(), REAL_AUTH_APPROVED)) {
            return false;
        }
        if (!requiresProfileCompletion(activePolicy)) {
            return true;
        }
        ActorProfile profile = selectActorProfile(inviteeUserId);
        int completion = ActorProfileCompletionCalculator.calculate(profile);
        return completion >= resolveProfileCompletionThreshold(activePolicy);
    }

    private void ensureAutoGrant(ReferralRecord record, ReferralPolicy activePolicy) {
        if (record == null || !Objects.equals(record.getStatus(), STATUS_VALID) || !isAutoGrantEnabled(activePolicy)) {
            return;
        }
        UserEntitlementGrant existingGrant = userEntitlementGrantMapper.selectOne(new LambdaQueryWrapper<UserEntitlementGrant>()
                .eq(UserEntitlementGrant::getUserId, record.getInviteeUserId())
                .eq(UserEntitlementGrant::getSourceType, "referral")
                .eq(UserEntitlementGrant::getSourceRefId, record.getReferralId())
                .orderByDesc(UserEntitlementGrant::getCreateTime)
                .orderByDesc(UserEntitlementGrant::getGrantId)
                .last("limit 1"));
        if (existingGrant != null) {
            return;
        }

        AutoGrantRule rule = parseAutoGrantRule(activePolicy);
        LocalDateTime effectiveTime = record.getValidatedAt() == null ? LocalDateTime.now() : record.getValidatedAt();
        UserEntitlementGrant grant = new UserEntitlementGrant();
        grant.setUserId(record.getInviteeUserId());
        grant.setGrantType(rule.grantType());
        grant.setGrantCode(resolveAutoGrantCode(record, rule));
        grant.setStatus(GRANT_STATUS_ACTIVE);
        grant.setEffectiveTime(effectiveTime);
        grant.setExpireTime(rule.durationDays() == null || rule.durationDays() <= 0 ? null : effectiveTime.plusDays(rule.durationDays()));
        grant.setSourceType("referral");
        grant.setSourceRefId(record.getReferralId());
        grant.setRemark(buildAutoGrantRemark(record, activePolicy, rule));
        userEntitlementGrantMapper.insert(grant);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("referral")
                .operationCode("auto_grant")
                .targetType("user_entitlement_grant")
                .targetId(grant.getGrantId())
                .afterSnapshot(snapshotGrant(grant))
                .extraContext(buildAutoGrantContext(record, grant, activePolicy))
                .operationResult(1)
                .build());
    }

    private ReferralPolicy selectActivePolicy() {
        return referralPolicyMapper.selectOne(new LambdaQueryWrapper<ReferralPolicy>()
                .eq(ReferralPolicy::getEnabled, 1)
                .orderByDesc(ReferralPolicy::getLastUpdate)
                .orderByDesc(ReferralPolicy::getPolicyId)
                .last("limit 1"));
    }

    private boolean requiresRealAuth(ReferralPolicy activePolicy) {
        return activePolicy == null || !Objects.equals(activePolicy.getRequireRealAuth(), 0);
    }

    private boolean requiresProfileCompletion(ReferralPolicy activePolicy) {
        return activePolicy != null && Objects.equals(activePolicy.getRequireProfileCompletion(), 1);
    }

    private int resolveProfileCompletionThreshold(ReferralPolicy activePolicy) {
        if (activePolicy == null || activePolicy.getProfileCompletionThreshold() == null
                || activePolicy.getProfileCompletionThreshold() <= 0) {
            return DEFAULT_PROFILE_COMPLETION_THRESHOLD;
        }
        return activePolicy.getProfileCompletionThreshold();
    }

    private boolean isAutoGrantEnabled(ReferralPolicy activePolicy) {
        return activePolicy != null && Objects.equals(activePolicy.getAutoGrantEnabled(), 1);
    }

    private AutoGrantRule parseAutoGrantRule(ReferralPolicy activePolicy) {
        String grantType = "invite_eligibility";
        String grantCodePrefix = "INVITE_ELIGIBILITY";
        Integer durationDays = null;
        String extraRemark = null;
        if (activePolicy == null || !StringUtils.hasText(activePolicy.getGrantRuleJson())) {
            return new AutoGrantRule(grantType, grantCodePrefix, durationDays, extraRemark);
        }
        try {
            JSONObject root = JSONUtil.parseObj(activePolicy.getGrantRuleJson());
            if (StringUtils.hasText(root.getStr("grantType"))) {
                grantType = root.getStr("grantType").trim();
            }
            if (StringUtils.hasText(root.getStr("grantCodePrefix"))) {
                grantCodePrefix = root.getStr("grantCodePrefix").trim();
            } else if (StringUtils.hasText(root.getStr("grantCode"))) {
                grantCodePrefix = root.getStr("grantCode").trim();
            }
            Object durationValue = root.get("durationDays");
            if (durationValue != null) {
                durationDays = Integer.valueOf(String.valueOf(durationValue));
            }
            if (StringUtils.hasText(root.getStr("remark"))) {
                extraRemark = root.getStr("remark").trim();
            }
        } catch (Exception ignored) {
            return new AutoGrantRule(grantType, grantCodePrefix, durationDays, extraRemark);
        }
        return new AutoGrantRule(grantType, grantCodePrefix, durationDays, extraRemark);
    }

    private String resolveAutoGrantCode(ReferralRecord record, AutoGrantRule rule) {
        String prefix = sanitizeGrantCode(rule.grantCodePrefix());
        String candidate = prefix + "_" + record.getReferralId();
        if (countGrantCode(record.getInviteeUserId(), candidate) == 0) {
            return candidate;
        }
        candidate = candidate + "_U" + record.getInviteeUserId();
        if (countGrantCode(record.getInviteeUserId(), candidate) == 0) {
            return candidate;
        }
        return candidate + "_" + System.currentTimeMillis();
    }

    private int countGrantCode(Long userId, String grantCode) {
        Long count = userEntitlementGrantMapper.selectCount(new LambdaQueryWrapper<UserEntitlementGrant>()
                .eq(UserEntitlementGrant::getUserId, userId)
                .eq(UserEntitlementGrant::getGrantCode, grantCode));
        return count == null ? 0 : count.intValue();
    }

    private String sanitizeGrantCode(String value) {
        String source = StringUtils.hasText(value) ? value.trim().toUpperCase() : "INVITE_ELIGIBILITY";
        String sanitized = source.replaceAll("[^A-Z0-9_-]", "_");
        return sanitized.isEmpty() ? "INVITE_ELIGIBILITY" : sanitized;
    }

    private String buildAutoGrantRemark(ReferralRecord record, ReferralPolicy activePolicy, AutoGrantRule rule) {
        StringBuilder builder = new StringBuilder("auto referral grant");
        if (activePolicy != null && activePolicy.getPolicyId() != null) {
            builder.append("; policyId=").append(activePolicy.getPolicyId());
        }
        builder.append("; referralId=").append(record.getReferralId());
        if (StringUtils.hasText(rule.extraRemark())) {
            builder.append("; ").append(rule.extraRemark());
        }
        return builder.toString();
    }

    private Map<String, Object> snapshotGrant(UserEntitlementGrant grant) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("grantId", grant.getGrantId());
        snapshot.put("userId", grant.getUserId());
        snapshot.put("grantType", grant.getGrantType());
        snapshot.put("grantCode", grant.getGrantCode());
        snapshot.put("status", grant.getStatus());
        snapshot.put("effectiveTime", grant.getEffectiveTime());
        snapshot.put("expireTime", grant.getExpireTime());
        snapshot.put("sourceType", grant.getSourceType());
        snapshot.put("sourceRefId", grant.getSourceRefId());
        snapshot.put("remark", grant.getRemark());
        return snapshot;
    }

    private Map<String, Object> buildAutoGrantContext(ReferralRecord record,
                                                      UserEntitlementGrant grant,
                                                      ReferralPolicy activePolicy) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("referral_record_id", record.getReferralId());
        context.put("inviter_user_id", record.getInviterUserId());
        context.put("invitee_user_id", record.getInviteeUserId());
        context.put("policy_id", activePolicy == null ? null : activePolicy.getPolicyId());
        context.put("grant_code", grant.getGrantCode());
        return context;
    }

    private void refreshInviterValidInviteCount(Long inviterUserId) {
        if (inviterUserId == null) {
            return;
        }
        User update = new User();
        update.setUserId(inviterUserId);
        update.setValidInviteCount(countValidInviteCount(inviterUserId));
        update.setUpdateUserName("");
        userMapper.updateById(update);
    }

    private ActorProfile selectActorProfile(Long userId) {
        return actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, userId)
                .last("limit 1"));
    }

    private String resolveDisplayName(User user, ActorProfile actorProfile) {
        if (actorProfile != null && StringUtils.hasText(actorProfile.getNickName())) {
            return actorProfile.getNickName();
        }
        return user == null ? null : user.getUserName();
    }

    private record AutoGrantRule(String grantType, String grantCodePrefix, Integer durationDays, String extraRemark) {
    }
}
