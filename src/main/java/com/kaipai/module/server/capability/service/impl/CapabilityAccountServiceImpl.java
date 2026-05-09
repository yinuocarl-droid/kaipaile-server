package com.kaipai.module.server.capability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.ResultCode;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.level.dto.ActorLevelCapabilityRespDTO;
import com.kaipai.module.model.level.dto.ActorLevelInfoRespDTO;
import com.kaipai.module.model.level.dto.ActorShareCapabilityRespDTO;
import com.kaipai.module.model.capability.dto.AdminCapabilityAccountDetailDTO;
import com.kaipai.module.model.capability.dto.AdminCapabilityAccountItemDTO;
import com.kaipai.module.model.capability.dto.CapabilityAccountCloseDTO;
import com.kaipai.module.model.capability.dto.CapabilityAccountExtendDTO;
import com.kaipai.module.model.capability.dto.CapabilityAccountOpenDTO;
import com.kaipai.module.model.capability.dto.CapabilityAccountQueryDTO;
import com.kaipai.module.model.capability.dto.CapabilityChangeLogItemDTO;
import com.kaipai.module.model.capability.dto.CapabilityChangeLogQueryDTO;
import com.kaipai.module.model.capability.entity.CapabilityAccount;
import com.kaipai.module.model.capability.entity.CapabilityChangeLog;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import com.kaipai.module.model.referral.entity.UserEntitlementGrant;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.capability.mapper.CapabilityAccountMapper;
import com.kaipai.module.server.capability.mapper.CapabilityChangeLogMapper;
import com.kaipai.module.server.capability.service.CapabilityAccountService;
import com.kaipai.module.server.capability.service.CapabilityChangeLogService;
import com.kaipai.module.server.payment.mapper.PaymentOrderMapper;
import com.kaipai.module.server.referral.mapper.UserEntitlementGrantMapper;
import com.kaipai.module.server.referral.service.ReferralRecordService;
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
public class CapabilityAccountServiceImpl extends ServiceImpl<CapabilityAccountMapper, CapabilityAccount> implements CapabilityAccountService {

    private static final int REAL_AUTH_APPROVED = 2;

    private final CapabilityChangeLogService capabilityChangeLogService;
    private final CapabilityChangeLogMapper capabilityChangeLogMapper;
    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final UserEntitlementGrantMapper userEntitlementGrantMapper;
    private final ReferralRecordService referralRecordService;
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
                && user.getRealAuthStatus() == REAL_AUTH_APPROVED;
        CapabilityAccount capabilityAccount = lambdaQuery()
                .eq(CapabilityAccount::getUserId, userId)
                .eq(CapabilityAccount::getStatus, 1)
                .orderByDesc(CapabilityAccount::getExpireTime)
                .orderByDesc(CapabilityAccount::getCapabilityId)
                .last("limit 1")
                .one();
        int inviteCount = referralRecordService.countValidInviteCount(userId);
        int level = calculateLevel(inviteCount, isCertified, profileCompletion);

        ActorLevelInfoRespDTO dto = new ActorLevelInfoRespDTO();
        dto.setLevel(level);
        dto.setInviteCount(inviteCount);
        dto.setNextLevelRequirement(nextLevelRequirement(level));
        dto.setIsCertified(isCertified);
        dto.setProfileCompletion(profileCompletion);
        dto.setCapabilityTier(resolveCapabilityTier(capabilityAccount));
        dto.setLevelCapability(buildLevelCapability(level, user));
        dto.setShareCapability(buildShareCapability(level, dto.getCapabilityTier(), isCertified, profileCompletion));
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
                dto.setPaidSkinFreePreview(false);
            }
            case 1 -> {
                dto.setMaxScenes(2);
                dto.setCanCustomColor(false);
                dto.setCanCustomLayout(false);
                dto.setAiQuotaPerMonth(resolveAiQuotaPerMonth(level, user));
                dto.setPaidSkinFreePreview(false);
            }
            case 2 -> {
                dto.setMaxScenes(3);
                dto.setCanCustomColor(false);
                dto.setCanCustomLayout(false);
                dto.setAiQuotaPerMonth(resolveAiQuotaPerMonth(level, user));
                dto.setPaidSkinFreePreview(false);
            }
            case 3 -> {
                dto.setMaxScenes(3);
                dto.setCanCustomColor(true);
                dto.setCanCustomLayout(false);
                dto.setAiQuotaPerMonth(resolveAiQuotaPerMonth(level, user));
                dto.setPaidSkinFreePreview(false);
            }
            case 4 -> {
                dto.setMaxScenes(5);
                dto.setCanCustomColor(true);
                dto.setCanCustomLayout(true);
                dto.setAiQuotaPerMonth(resolveAiQuotaPerMonth(level, user));
                dto.setPaidSkinFreePreview(false);
            }
            default -> {
                dto.setMaxScenes(5);
                dto.setCanCustomColor(true);
                dto.setCanCustomLayout(true);
                dto.setAiQuotaPerMonth(resolveAiQuotaPerMonth(level, user));
                dto.setPaidSkinFreePreview(true);
            }
        }
        return dto;
    }

    private ActorShareCapabilityRespDTO buildShareCapability(int level,
                                                             String capabilityTier,
                                                             boolean isCertified,
                                                             int profileCompletion) {
        ActorShareCapabilityRespDTO dto = new ActorShareCapabilityRespDTO();
        boolean hasPaidCapability = !"base".equals(capabilityTier);
        boolean canUseBasicCard = level > 0;
        dto.setCanUseBasicCard(canUseBasicCard);
        dto.setCanUsePersonalizedTheme(hasPaidCapability);
        dto.setCanUseCustomMiniProgramCard(hasPaidCapability);
        dto.setCanUseCustomPoster(true);
        dto.setCanUseCustomInviteCard(hasPaidCapability);
        if (!hasPaidCapability) {
            dto.getReasonCodes().add("capability_required");
        }
        if (!isCertified) {
            dto.getReasonCodes().add("verify_required");
        }
        if (profileCompletion < 70) {
            dto.getReasonCodes().add("profile_completion_required");
        }
        if (level < 5) {
            dto.getReasonCodes().add("level_required");
        }
        return dto;
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

    private String resolveCapabilityTier(CapabilityAccount capabilityAccount) {
        if (capabilityAccount == null || capabilityAccount.getTier() == null || capabilityAccount.getTier() <= 0) {
            return "base";
        }
        return capabilityAccount.getTier() >= 2 ? "pro" : "plus";
    }

    @Override
    public PageResult<AdminCapabilityAccountItemDTO> adminAccountList(CapabilityAccountQueryDTO query) {
        Page<CapabilityAccount> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<CapabilityAccount> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(CapabilityAccount::getUserId, query.getUserId());
        }
        if (query.getTier() != null) {
            wrapper.eq(CapabilityAccount::getTier, query.getTier());
        }
        if (query.getStatus() != null) {
            wrapper.eq(CapabilityAccount::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(CapabilityAccount::getSourceType, query.getSourceType().trim());
        }
        if (query.getEffectiveTimeFrom() != null) {
            wrapper.ge(CapabilityAccount::getEffectiveTime, query.getEffectiveTimeFrom());
        }
        if (query.getEffectiveTimeTo() != null) {
            wrapper.le(CapabilityAccount::getEffectiveTime, query.getEffectiveTimeTo());
        }
        if (query.getExpireTimeFrom() != null) {
            wrapper.ge(CapabilityAccount::getExpireTime, query.getExpireTimeFrom());
        }
        if (query.getExpireTimeTo() != null) {
            wrapper.le(CapabilityAccount::getExpireTime, query.getExpireTimeTo());
        }
        if (StringUtils.hasText(query.getPhone())) {
            Set<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<User>().like(User::getPhone, query.getPhone().trim()))
                    .stream()
                    .map(User::getUserId)
                    .collect(Collectors.toSet());
            if (userIds.isEmpty()) {
                return PageResult.empty();
            }
            wrapper.in(CapabilityAccount::getUserId, userIds);
        }
        wrapper.orderByDesc(CapabilityAccount::getCreateTime).orderByDesc(CapabilityAccount::getCapabilityId);

        Page<CapabilityAccount> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Set<Long> userIds = result.getRecords().stream()
                .map(CapabilityAccount::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, ActorProfile> actorProfileMap = actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>()
                        .in(ActorProfile::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(ActorProfile::getUserId, Function.identity(), (left, right) -> left));
        Map<Long, CapabilityChangeLog> latestLogMap = capabilityChangeLogMapper.selectList(new LambdaQueryWrapper<CapabilityChangeLog>()
                        .in(CapabilityChangeLog::getUserId, userIds)
                        .orderByDesc(CapabilityChangeLog::getCreateTime)
                        .orderByDesc(CapabilityChangeLog::getChangeLogId))
                .stream()
                .collect(Collectors.toMap(CapabilityChangeLog::getUserId, Function.identity(), (left, right) -> left));

        List<AdminCapabilityAccountItemDTO> list = result.getRecords().stream()
                .map(account -> toAccountItem(account, userMap.get(account.getUserId()), actorProfileMap.get(account.getUserId()), latestLogMap.get(account.getUserId())))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AdminCapabilityAccountDetailDTO adminAccountDetail(Long userId) {
        CapabilityAccount account = lambdaQuery().eq(CapabilityAccount::getUserId, userId).one();
        if (account == null) {
            throw new BizException("能力账户不存在");
        }
        User user = userMapper.selectById(userId);
        ActorProfile actorProfile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, userId)
                .last("limit 1"));
        List<CapabilityChangeLogItemDTO> changeLogs = capabilityChangeLogService.adminLogList(buildDetailLogQuery(userId)).getList();
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

        AdminCapabilityAccountDetailDTO detail = new AdminCapabilityAccountDetailDTO();
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
    public void openAccount(Long userId, CapabilityAccountOpenDTO dto) {
        if (dto.getExpireTime().isBefore(dto.getEffectiveTime())) {
            throw new BizException("有效期结束时间必须晚于生效时间");
        }
        CapabilityAccount account = lambdaQuery().eq(CapabilityAccount::getUserId, userId).one();
        Map<String, Object> beforeSnapshot = account == null ? null : snapshot(account);
        Integer beforeTier = account != null ? account.getTier() : 0;
        if (account == null) {
            account = new CapabilityAccount();
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
                .moduleCode("capability")
                .operationCode("open")
                .targetType("capability_account")
                .targetId(account.getCapabilityId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(account))
                .extraContext(accountContext(account, dto.getRemark()))
                .operationResult(1)
                .build());
    }

    @Override
    @Transactional
    public void extendAccount(Long userId, CapabilityAccountExtendDTO dto) {
        CapabilityAccount account = lambdaQuery().eq(CapabilityAccount::getUserId, userId).one();
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
                .moduleCode("capability")
                .operationCode("extend")
                .targetType("capability_account")
                .targetId(account.getCapabilityId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(account))
                .extraContext(accountContext(account, dto.getRemark()))
                .operationResult(1)
                .build());
    }

    @Override
    @Transactional
    public void closeAccount(Long userId, CapabilityAccountCloseDTO dto) {
        CapabilityAccount account = lambdaQuery().eq(CapabilityAccount::getUserId, userId).one();
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
                .moduleCode("capability")
                .operationCode("close")
                .targetType("capability_account")
                .targetId(account.getCapabilityId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(account))
                .extraContext(accountContext(account, dto.getRemark()))
                .operationResult(1)
                .build());
    }

    private CapabilityChangeLogQueryDTO buildDetailLogQuery(Long userId) {
        CapabilityChangeLogQueryDTO query = new CapabilityChangeLogQueryDTO();
        query.setUserId(userId);
        query.setPageNo(1);
        query.setPageSize(20);
        return query;
    }

    private AdminCapabilityAccountItemDTO toAccountItem(CapabilityAccount account,
                                                        User user,
                                                        ActorProfile actorProfile,
                                                        CapabilityChangeLog latestLog) {
        AdminCapabilityAccountItemDTO item = new AdminCapabilityAccountItemDTO();
        item.setCapabilityId(account.getCapabilityId());
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

    private AdminCapabilityAccountDetailDTO.CurrentAccount toCurrentAccount(CapabilityAccount account) {
        AdminCapabilityAccountDetailDTO.CurrentAccount currentAccount = new AdminCapabilityAccountDetailDTO.CurrentAccount();
        currentAccount.setCapabilityId(account.getCapabilityId());
        currentAccount.setTier(account.getTier());
        currentAccount.setStatus(account.getStatus());
        currentAccount.setEffectiveTime(account.getEffectiveTime());
        currentAccount.setExpireTime(account.getExpireTime());
        currentAccount.setSourceType(account.getSourceType());
        currentAccount.setSourceRefId(account.getSourceRefId());
        return currentAccount;
    }

    private AdminCapabilityAccountDetailDTO.PaymentOrderSummary toPaymentSummary(PaymentOrder order) {
        AdminCapabilityAccountDetailDTO.PaymentOrderSummary item = new AdminCapabilityAccountDetailDTO.PaymentOrderSummary();
        item.setPaymentOrderId(order.getPaymentOrderId());
        item.setOrderNo(order.getOrderNo());
        item.setAmount(order.getAmount());
        item.setPayStatus(order.getPayStatus());
        item.setPayChannel(order.getPayChannel());
        item.setCreateTime(order.getCreateTime());
        item.setPaidAt(order.getPaidAt());
        return item;
    }

    private AdminCapabilityAccountDetailDTO.GrantSummary toGrantSummary(UserEntitlementGrant grant) {
        AdminCapabilityAccountDetailDTO.GrantSummary item = new AdminCapabilityAccountDetailDTO.GrantSummary();
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
                           String sourceType, Long sourceRefId, String remark, CapabilityAccount account) {
        CapabilityChangeLog log = new CapabilityChangeLog();
        log.setUserId(userId);
        log.setBeforeTier(beforeTier);
        log.setAfterTier(afterTier);
        log.setChangeReason(reason);
        log.setSourceType(sourceType);
        log.setSourceRefId(sourceRefId);
        log.setEffectiveTime(account.getEffectiveTime());
        log.setExpireTime(account.getExpireTime());
        log.setRemark(remark);
        capabilityChangeLogService.save(log);
    }

    private Map<String, Object> snapshot(CapabilityAccount account) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("capabilityId", account.getCapabilityId());
        snapshot.put("userId", account.getUserId());
        snapshot.put("tier", account.getTier());
        snapshot.put("status", account.getStatus());
        snapshot.put("effectiveTime", account.getEffectiveTime());
        snapshot.put("expireTime", account.getExpireTime());
        snapshot.put("sourceType", account.getSourceType());
        snapshot.put("sourceRefId", account.getSourceRefId());
        return snapshot;
    }

    private Map<String, Object> accountContext(CapabilityAccount account, String remark) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("capability_account_id", account.getCapabilityId());
        context.put("user_id", account.getUserId());
        context.put("capability_tier_after", account.getTier());
        context.put("capability_status_after", account.getStatus());
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
