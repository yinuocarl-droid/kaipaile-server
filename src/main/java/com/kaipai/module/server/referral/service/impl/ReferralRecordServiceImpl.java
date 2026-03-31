package com.kaipai.module.server.referral.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.referral.dto.AdminReferralRecordDetailDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRecordItemDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRecordQueryDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRiskDecisionDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRiskDetailDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRiskItemDTO;
import com.kaipai.module.model.referral.dto.AdminReferralRiskQueryDTO;
import com.kaipai.module.model.referral.entity.InviteCode;
import com.kaipai.module.model.referral.entity.ReferralRecord;
import com.kaipai.module.model.referral.entity.UserEntitlementGrant;
import com.kaipai.module.model.system.entity.AdminOperationLog;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.referral.mapper.InviteCodeMapper;
import com.kaipai.module.server.referral.mapper.ReferralRecordMapper;
import com.kaipai.module.server.referral.mapper.UserEntitlementGrantMapper;
import com.kaipai.module.server.referral.service.ReferralRecordService;
import com.kaipai.module.server.system.mapper.AdminOperationLogMapper;
import com.kaipai.module.server.user.mapper.UserMapper;
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

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_VALID = 1;
    private static final int STATUS_INVALID = 2;
    private static final int STATUS_UNDER_REVIEW = 3;
    private static final int RISK_FLAG_NORMAL = 0;

    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final InviteCodeMapper inviteCodeMapper;
    private final UserEntitlementGrantMapper userEntitlementGrantMapper;
    private final AdminOperationLogMapper adminOperationLogMapper;
    private final AdminOperationLogger adminOperationLogger;

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
        if (record.getValidatedAt() == null) {
            record.setValidatedAt(LocalDateTime.now());
        }
        updateById(record);
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
}
