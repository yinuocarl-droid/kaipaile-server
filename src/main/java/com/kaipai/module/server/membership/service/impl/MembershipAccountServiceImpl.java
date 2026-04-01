package com.kaipai.module.server.membership.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.ResultCode;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.fortune.entity.FortuneReport;
import com.kaipai.module.model.level.dto.ActorLevelCapabilityRespDTO;
import com.kaipai.module.model.level.dto.ActorLevelInfoRespDTO;
import com.kaipai.module.model.level.dto.ActorShareCapabilityRespDTO;
import com.kaipai.module.model.membership.dto.AdminMembershipAccountDetailDTO;
import com.kaipai.module.model.membership.dto.AdminMembershipAccountItemDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountCloseDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountExtendDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountOpenDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountQueryDTO;
import com.kaipai.module.model.membership.dto.MembershipChangeLogItemDTO;
import com.kaipai.module.model.membership.dto.MembershipChangeLogQueryDTO;
import com.kaipai.module.model.membership.entity.MembershipAccount;
import com.kaipai.module.model.membership.entity.MembershipChangeLog;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import com.kaipai.module.model.referral.entity.UserEntitlementGrant;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.fortune.mapper.FortuneReportMapper;
import com.kaipai.module.server.membership.mapper.MembershipAccountMapper;
import com.kaipai.module.server.membership.mapper.MembershipChangeLogMapper;
import com.kaipai.module.server.membership.service.MembershipAccountService;
import com.kaipai.module.server.membership.service.MembershipChangeLogService;
import com.kaipai.module.server.payment.mapper.PaymentOrderMapper;
import com.kaipai.module.server.referral.mapper.UserEntitlementGrantMapper;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MembershipAccountServiceImpl extends ServiceImpl<MembershipAccountMapper, MembershipAccount> implements MembershipAccountService {

    private static final int REAL_AUTH_APPROVED = 2;

    private final MembershipChangeLogService membershipChangeLogService;
    private final MembershipChangeLogMapper membershipChangeLogMapper;
    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final FortuneReportMapper fortuneReportMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final UserEntitlementGrantMapper userEntitlementGrantMapper;
    private final AdminOperationLogger adminOperationLogger;

    @Override
    public ActorLevelInfoRespDTO actorLevelInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        ActorProfile profile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, userId)
                .last("limit 1"));
        int profileCompletion = calculateProfileCompletion(profile);
        boolean isCertified = user.getRealAuthStatus() != null
                && user.getRealAuthStatus() == REAL_AUTH_APPROVED
                && profile != null
                && Boolean.TRUE.equals(profile.getIsCertified());
        MembershipAccount membershipAccount = lambdaQuery()
                .eq(MembershipAccount::getUserId, userId)
                .eq(MembershipAccount::getStatus, 1)
                .orderByDesc(MembershipAccount::getExpireTime)
                .orderByDesc(MembershipAccount::getMembershipId)
                .last("limit 1")
                .one();
        int inviteCount = user.getValidInviteCount() == null ? 0 : user.getValidInviteCount();
        int level = calculateLevel(inviteCount, isCertified, profileCompletion);

        ActorLevelInfoRespDTO dto = new ActorLevelInfoRespDTO();
        dto.setLevel(level);
        dto.setInviteCount(inviteCount);
        dto.setNextLevelRequirement(nextLevelRequirement(level));
        dto.setIsCertified(isCertified);
        dto.setProfileCompletion(profileCompletion);
        dto.setMembershipTier(resolveMembershipTier(membershipAccount));
        dto.setLevelCapability(buildLevelCapability(level, user));
        dto.setShareCapability(buildShareCapability(level, dto.getMembershipTier(), hasFortuneReport(userId)));
        return dto;
    }

    private ActorLevelCapabilityRespDTO buildLevelCapability(int level, User user) {
        ActorLevelCapabilityRespDTO dto = new ActorLevelCapabilityRespDTO();
        switch (level) {
            case 0 -> {
                dto.setMaxScenes(0);
                dto.setCanCustomColor(false);
                dto.setCanCustomLayout(false);
                dto.setAiQuotaPerMonth(0);
                dto.setCanUseLuckyColor(false);
                dto.setPaidSkinFreePreview(false);
            }
            case 1 -> {
                dto.setMaxScenes(2);
                dto.setCanCustomColor(false);
                dto.setCanCustomLayout(false);
                dto.setAiQuotaPerMonth(resolveAiQuotaPerMonth(level, user));
                dto.setCanUseLuckyColor(false);
                dto.setPaidSkinFreePreview(false);
            }
            case 2 -> {
                dto.setMaxScenes(3);
                dto.setCanCustomColor(false);
                dto.setCanCustomLayout(false);
                dto.setAiQuotaPerMonth(resolveAiQuotaPerMonth(level, user));
                dto.setCanUseLuckyColor(false);
                dto.setPaidSkinFreePreview(false);
            }
            case 3 -> {
                dto.setMaxScenes(3);
                dto.setCanCustomColor(true);
                dto.setCanCustomLayout(false);
                dto.setAiQuotaPerMonth(resolveAiQuotaPerMonth(level, user));
                dto.setCanUseLuckyColor(false);
                dto.setPaidSkinFreePreview(false);
            }
            case 4 -> {
                dto.setMaxScenes(5);
                dto.setCanCustomColor(true);
                dto.setCanCustomLayout(true);
                dto.setAiQuotaPerMonth(resolveAiQuotaPerMonth(level, user));
                dto.setCanUseLuckyColor(false);
                dto.setPaidSkinFreePreview(false);
            }
            default -> {
                dto.setMaxScenes(5);
                dto.setCanCustomColor(true);
                dto.setCanCustomLayout(true);
                dto.setAiQuotaPerMonth(resolveAiQuotaPerMonth(level, user));
                dto.setCanUseLuckyColor(true);
                dto.setPaidSkinFreePreview(true);
            }
        }
        return dto;
    }

    private ActorShareCapabilityRespDTO buildShareCapability(int level, String membershipTier, boolean hasFortuneReport) {
        ActorShareCapabilityRespDTO dto = new ActorShareCapabilityRespDTO();
        boolean isMember = !"none".equals(membershipTier);
        dto.setCanUseBasicCard(level > 0);
        dto.setCanUsePersonalizedTheme(isMember);
        dto.setCanUseCustomMiniProgramCard(isMember);
        dto.setCanUseCustomPoster(isMember);
        dto.setCanUseCustomInviteCard(isMember);
        dto.setCanApplyFortuneTheme(isMember && level >= 5 && hasFortuneReport);
        if (!isMember) {
            dto.getReasonCodes().add("member_required");
        }
        if (level == 0) {
            dto.getReasonCodes().add("verify_required");
        }
        if (!hasFortuneReport) {
            dto.getReasonCodes().add("fortune_missing");
        }
        if (level < 5) {
            dto.getReasonCodes().add("level_required");
        }
        return dto;
    }

    private boolean hasFortuneReport(Long userId) {
        FortuneReport report = fortuneReportMapper.selectOne(new LambdaQueryWrapper<FortuneReport>()
                .eq(FortuneReport::getUserId, userId)
                .orderByDesc(FortuneReport::getReportMonth)
                .orderByDesc(FortuneReport::getCreateTime)
                .last("limit 1"));
        return report != null && StringUtils.hasText(report.getLuckyColor());
    }

    private int resolveAiQuotaPerMonth(int level, User user) {
        if (level <= 0) {
            return 0;
        }
        if (user != null
                && user.getCreateTime() != null
                && user.getCreateTime().plusDays(31).isAfter(LocalDateTime.now())
                && level <= 2) {
            return 3;
        }
        return switch (level) {
            case 1, 2 -> 1;
            case 3 -> 3;
            case 4 -> 4;
            default -> 5;
        };
    }

    private String resolveMembershipTier(MembershipAccount membershipAccount) {
        if (membershipAccount == null || membershipAccount.getTier() == null || membershipAccount.getTier() <= 0) {
            return "none";
        }
        return membershipAccount.getTier() >= 2 ? "vip" : "member";
    }

    @Override
    public PageResult<AdminMembershipAccountItemDTO> adminAccountList(MembershipAccountQueryDTO query) {
        Page<MembershipAccount> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<MembershipAccount> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(MembershipAccount::getUserId, query.getUserId());
        }
        if (query.getTier() != null) {
            wrapper.eq(MembershipAccount::getTier, query.getTier());
        }
        if (query.getStatus() != null) {
            wrapper.eq(MembershipAccount::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(MembershipAccount::getSourceType, query.getSourceType().trim());
        }
        if (query.getEffectiveTimeFrom() != null) {
            wrapper.ge(MembershipAccount::getEffectiveTime, query.getEffectiveTimeFrom());
        }
        if (query.getEffectiveTimeTo() != null) {
            wrapper.le(MembershipAccount::getEffectiveTime, query.getEffectiveTimeTo());
        }
        if (query.getExpireTimeFrom() != null) {
            wrapper.ge(MembershipAccount::getExpireTime, query.getExpireTimeFrom());
        }
        if (query.getExpireTimeTo() != null) {
            wrapper.le(MembershipAccount::getExpireTime, query.getExpireTimeTo());
        }
        if (StringUtils.hasText(query.getPhone())) {
            Set<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<User>().like(User::getPhone, query.getPhone().trim()))
                    .stream()
                    .map(User::getUserId)
                    .collect(Collectors.toSet());
            if (userIds.isEmpty()) {
                return PageResult.empty();
            }
            wrapper.in(MembershipAccount::getUserId, userIds);
        }
        wrapper.orderByDesc(MembershipAccount::getCreateTime).orderByDesc(MembershipAccount::getMembershipId);

        Page<MembershipAccount> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Set<Long> userIds = result.getRecords().stream()
                .map(MembershipAccount::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, ActorProfile> actorProfileMap = actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>()
                        .in(ActorProfile::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(ActorProfile::getUserId, Function.identity(), (left, right) -> left));
        Map<Long, MembershipChangeLog> latestLogMap = membershipChangeLogMapper.selectList(new LambdaQueryWrapper<MembershipChangeLog>()
                        .in(MembershipChangeLog::getUserId, userIds)
                        .orderByDesc(MembershipChangeLog::getCreateTime)
                        .orderByDesc(MembershipChangeLog::getChangeLogId))
                .stream()
                .collect(Collectors.toMap(MembershipChangeLog::getUserId, Function.identity(), (left, right) -> left));

        List<AdminMembershipAccountItemDTO> list = result.getRecords().stream()
                .map(account -> toAccountItem(account, userMap.get(account.getUserId()), actorProfileMap.get(account.getUserId()), latestLogMap.get(account.getUserId())))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AdminMembershipAccountDetailDTO adminAccountDetail(Long userId) {
        MembershipAccount account = lambdaQuery().eq(MembershipAccount::getUserId, userId).one();
        if (account == null) {
            throw new BizException("会员账户不存在");
        }
        User user = userMapper.selectById(userId);
        ActorProfile actorProfile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, userId)
                .last("limit 1"));
        List<MembershipChangeLogItemDTO> changeLogs = membershipChangeLogService.adminLogList(buildDetailLogQuery(userId)).getList();
        List<PaymentOrder> paymentOrders = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId)
                .orderByDesc(PaymentOrder::getCreateTime)
                .orderByDesc(PaymentOrder::getPaymentOrderId)
                .last("limit 10"));
        List<UserEntitlementGrant> grants = userEntitlementGrantMapper.selectList(new LambdaQueryWrapper<UserEntitlementGrant>()
                .eq(UserEntitlementGrant::getUserId, userId)
                .orderByDesc(UserEntitlementGrant::getCreateTime)
                .orderByDesc(UserEntitlementGrant::getGrantId)
                .last("limit 10"));

        AdminMembershipAccountDetailDTO detail = new AdminMembershipAccountDetailDTO();
        detail.setUserId(userId);
        detail.setNickname(actorProfile != null && StringUtils.hasText(actorProfile.getNickName()) ? actorProfile.getNickName() : user == null ? null : user.getUserName());
        detail.setPhone(user == null ? null : user.getPhone());
        detail.setCurrentAccount(toCurrentAccount(account));
        detail.setRelatedPaymentOrders(paymentOrders.stream().map(this::toPaymentSummary).toList());
        detail.setRelatedGrants(grants.stream().map(this::toGrantSummary).toList());
        detail.setChangeLogs(changeLogs);
        return detail;
    }

    @Override
    @Transactional
    public void openAccount(Long userId, MembershipAccountOpenDTO dto) {
        if (dto.getExpireTime().isBefore(dto.getEffectiveTime())) {
            throw new BizException("有效期结束时间必须晚于生效时间");
        }
        MembershipAccount account = lambdaQuery().eq(MembershipAccount::getUserId, userId).one();
        Map<String, Object> beforeSnapshot = account == null ? null : snapshot(account);
        Integer beforeTier = account != null ? account.getTier() : 0;
        if (account == null) {
            account = new MembershipAccount();
            account.setUserId(userId);
        }
        account.setTier(dto.getTier());
        account.setStatus(1);
        account.setEffectiveTime(dto.getEffectiveTime());
        account.setExpireTime(dto.getExpireTime());
        account.setSourceType(dto.getSourceType());
        account.setSourceRefId(dto.getSourceRefId());
        saveOrUpdate(account);
        logChange(userId, beforeTier, account.getTier(), "手动开通", dto.getSourceType(), dto.getSourceRefId(), dto.getRemark(), account);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("membership")
                .operationCode("open")
                .targetType("membership_account")
                .targetId(account.getMembershipId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(account))
                .extraContext(accountContext(account, dto.getRemark()))
                .operationResult(1)
                .build());
    }

    @Override
    @Transactional
    public void extendAccount(Long userId, MembershipAccountExtendDTO dto) {
        MembershipAccount account = lambdaQuery().eq(MembershipAccount::getUserId, userId).one();
        if (account == null) {
            throw new BizException(ResultCode.PARAM_ERROR.getMessage());
        }
        Map<String, Object> beforeSnapshot = snapshot(account);
        Integer beforeTier = account.getTier();
        account.setExpireTime(dto.getExpireTime());
        account.setStatus(1);
        updateById(account);
        logChange(userId, beforeTier, account.getTier(), "手动延期", account.getSourceType(), account.getSourceRefId(), dto.getRemark(), account);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("membership")
                .operationCode("extend")
                .targetType("membership_account")
                .targetId(account.getMembershipId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(account))
                .extraContext(accountContext(account, dto.getRemark()))
                .operationResult(1)
                .build());
    }

    @Override
    @Transactional
    public void closeAccount(Long userId, MembershipAccountCloseDTO dto) {
        MembershipAccount account = lambdaQuery().eq(MembershipAccount::getUserId, userId).one();
        if (account == null) {
            throw new BizException(ResultCode.PARAM_ERROR.getMessage());
        }
        Map<String, Object> beforeSnapshot = snapshot(account);
        Integer beforeTier = account.getTier();
        account.setStatus(3);
        account.setExpireTime(LocalDateTime.now());
        updateById(account);
        logChange(userId, beforeTier, account.getTier(), "手动关闭", account.getSourceType(), account.getSourceRefId(), dto.getRemark(), account);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("membership")
                .operationCode("close")
                .targetType("membership_account")
                .targetId(account.getMembershipId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(account))
                .extraContext(accountContext(account, dto.getRemark()))
                .operationResult(1)
                .build());
    }

    private MembershipChangeLogQueryDTO buildDetailLogQuery(Long userId) {
        MembershipChangeLogQueryDTO query = new MembershipChangeLogQueryDTO();
        query.setUserId(userId);
        query.setPageNo(1);
        query.setPageSize(20);
        return query;
    }

    private AdminMembershipAccountItemDTO toAccountItem(MembershipAccount account,
                                                        User user,
                                                        ActorProfile actorProfile,
                                                        MembershipChangeLog latestLog) {
        AdminMembershipAccountItemDTO item = new AdminMembershipAccountItemDTO();
        item.setMembershipId(account.getMembershipId());
        item.setUserId(account.getUserId());
        item.setNickname(actorProfile != null && StringUtils.hasText(actorProfile.getNickName()) ? actorProfile.getNickName() : user == null ? null : user.getUserName());
        item.setPhone(user == null ? null : user.getPhone());
        item.setTier(account.getTier());
        item.setStatus(account.getStatus());
        item.setEffectiveTime(account.getEffectiveTime());
        item.setExpireTime(account.getExpireTime());
        item.setSourceType(account.getSourceType());
        item.setSourceRefId(account.getSourceRefId());
        item.setRecentChangeTime(latestLog == null ? null : latestLog.getCreateTime());
        return item;
    }

    private AdminMembershipAccountDetailDTO.CurrentAccount toCurrentAccount(MembershipAccount account) {
        AdminMembershipAccountDetailDTO.CurrentAccount currentAccount = new AdminMembershipAccountDetailDTO.CurrentAccount();
        currentAccount.setMembershipId(account.getMembershipId());
        currentAccount.setTier(account.getTier());
        currentAccount.setStatus(account.getStatus());
        currentAccount.setEffectiveTime(account.getEffectiveTime());
        currentAccount.setExpireTime(account.getExpireTime());
        currentAccount.setSourceType(account.getSourceType());
        currentAccount.setSourceRefId(account.getSourceRefId());
        return currentAccount;
    }

    private AdminMembershipAccountDetailDTO.PaymentOrderSummary toPaymentSummary(PaymentOrder order) {
        AdminMembershipAccountDetailDTO.PaymentOrderSummary item = new AdminMembershipAccountDetailDTO.PaymentOrderSummary();
        item.setPaymentOrderId(order.getPaymentOrderId());
        item.setOrderNo(order.getOrderNo());
        item.setAmount(order.getAmount());
        item.setPayStatus(order.getPayStatus());
        item.setPayChannel(order.getPayChannel());
        item.setCreateTime(order.getCreateTime());
        item.setPaidAt(order.getPaidAt());
        return item;
    }

    private AdminMembershipAccountDetailDTO.GrantSummary toGrantSummary(UserEntitlementGrant grant) {
        AdminMembershipAccountDetailDTO.GrantSummary item = new AdminMembershipAccountDetailDTO.GrantSummary();
        item.setGrantId(grant.getGrantId());
        item.setGrantType(grant.getGrantType());
        item.setGrantCode(grant.getGrantCode());
        item.setStatus(grant.getStatus());
        item.setEffectiveTime(grant.getEffectiveTime());
        item.setExpireTime(grant.getExpireTime());
        item.setSourceType(grant.getSourceType());
        item.setSourceRefId(grant.getSourceRefId());
        item.setRemark(grant.getRemark());
        return item;
    }

    private void logChange(Long userId, Integer beforeTier, Integer afterTier, String reason,
                           String sourceType, Long sourceRefId, String remark, MembershipAccount account) {
        MembershipChangeLog log = new MembershipChangeLog();
        log.setUserId(userId);
        log.setBeforeTier(beforeTier);
        log.setAfterTier(afterTier);
        log.setChangeReason(reason);
        log.setSourceType(sourceType);
        log.setSourceRefId(sourceRefId);
        log.setEffectiveTime(account.getEffectiveTime());
        log.setExpireTime(account.getExpireTime());
        log.setRemark(remark);
        membershipChangeLogService.save(log);
    }

    private Map<String, Object> snapshot(MembershipAccount account) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("membershipId", account.getMembershipId());
        snapshot.put("userId", account.getUserId());
        snapshot.put("tier", account.getTier());
        snapshot.put("status", account.getStatus());
        snapshot.put("effectiveTime", account.getEffectiveTime());
        snapshot.put("expireTime", account.getExpireTime());
        snapshot.put("sourceType", account.getSourceType());
        snapshot.put("sourceRefId", account.getSourceRefId());
        return snapshot;
    }

    private Map<String, Object> accountContext(MembershipAccount account, String remark) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("membership_account_id", account.getMembershipId());
        context.put("user_id", account.getUserId());
        context.put("membership_tier_after", account.getTier());
        context.put("membership_status_after", account.getStatus());
        context.put("effective_time", account.getEffectiveTime());
        context.put("expire_time", account.getExpireTime());
        context.put("source_type", account.getSourceType());
        context.put("source_id", account.getSourceRefId());
        context.put("remark", remark);
        return context;
    }

    private int calculateProfileCompletion(ActorProfile profile) {
        if (profile == null) {
            return 0;
        }

        int score = 0;
        if (hasText(profile.getAvatarUrl())) {
            score += 10;
        }
        if (hasText(profile.getNickName()) && profile.getGender() != null && profile.getAge() != null
                && profile.getHeight() != null && hasText(profile.getLocationCity())) {
            score += 15;
        }
        if (hasText(profile.getPhotoUrls())) {
            score += 15;
        }
        if (hasText(profile.getVideoUrl())) {
            score += 15;
        }
        if (hasText(profile.getIntro()) && profile.getIntro().trim().length() >= 20) {
            score += 10;
        }
        if (hasText(profile.getSkillTag())) {
            score += 5;
        }
        if (hasText(profile.getExperienceDesc())) {
            score += 15;
        }
        if (hasText(profile.getStyleTag())) {
            score += 10;
        }
        return score;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int calculateLevel(int inviteCount, boolean isCertified, int profileCompletion) {
        if (!isCertified || profileCompletion < 70) {
            return 0;
        }
        if (inviteCount >= 8) {
            return 5;
        }
        if (inviteCount >= 5) {
            return 4;
        }
        if (inviteCount >= 3) {
            return 3;
        }
        if (inviteCount >= 1) {
            return 2;
        }
        return 1;
    }

    private Integer nextLevelRequirement(int level) {
        return switch (level) {
            case 0, 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            case 4 -> 8;
            default -> null;
        };
    }
}
