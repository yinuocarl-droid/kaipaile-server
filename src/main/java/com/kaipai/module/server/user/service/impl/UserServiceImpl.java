package com.kaipai.module.server.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.membership.entity.MembershipAccount;
import com.kaipai.module.model.membership.entity.MembershipChangeLog;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import com.kaipai.module.model.referral.entity.InviteCode;
import com.kaipai.module.model.referral.entity.ReferralRecord;
import com.kaipai.module.model.referral.entity.UserEntitlementGrant;
import com.kaipai.module.model.refund.entity.RefundOrder;
import com.kaipai.module.model.system.entity.AdminOperationLog;
import com.kaipai.module.model.user.dto.UserAdminDetailDTO;
import com.kaipai.module.model.user.dto.UserAdminEntitlementSummaryDTO;
import com.kaipai.module.model.user.dto.UserAdminListItemDTO;
import com.kaipai.module.model.user.dto.UserAdminQueryDTO;
import com.kaipai.module.model.user.dto.UserSessionRespDTO;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.model.verify.entity.IdentityVerification;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.membership.mapper.MembershipAccountMapper;
import com.kaipai.module.server.membership.mapper.MembershipChangeLogMapper;
import com.kaipai.module.server.payment.mapper.PaymentOrderMapper;
import com.kaipai.module.server.referral.mapper.InviteCodeMapper;
import com.kaipai.module.server.referral.mapper.ReferralRecordMapper;
import com.kaipai.module.server.referral.mapper.UserEntitlementGrantMapper;
import com.kaipai.module.server.refund.mapper.RefundOrderMapper;
import com.kaipai.module.server.system.mapper.AdminOperationLogMapper;
import com.kaipai.module.server.user.mapper.UserMapper;
import com.kaipai.module.server.user.service.UserService;
import com.kaipai.module.server.verify.mapper.IdentityVerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final List<Integer> PAID_ORDER_STATUSES = List.of(1, 3, 4);
    private static final int USER_TYPE_ACTOR = 1;
    private static final int USER_TYPE_CREW = 2;
    private static final int MEMBERSHIP_STATUS_ACTIVE = 1;
    private static final int INVITE_CODE_STATUS_ACTIVE = 1;
    private static final int REFERRAL_STATUS_VALID = 1;
    private static final int REFERRAL_STATUS_UNDER_REVIEW = 3;
    private static final int REFERRAL_RISK_FLAG_NORMAL = 0;

    private final ActorProfileMapper actorProfileMapper;
    private final IdentityVerificationMapper identityVerificationMapper;
    private final MembershipAccountMapper membershipAccountMapper;
    private final MembershipChangeLogMapper membershipChangeLogMapper;
    private final UserEntitlementGrantMapper userEntitlementGrantMapper;
    private final ReferralRecordMapper referralRecordMapper;
    private final InviteCodeMapper inviteCodeMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final AdminOperationLogMapper adminOperationLogMapper;

    @Override
    public UserSessionRespDTO currentUser(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return buildCurrentUser(user);
    }

    @Override
    public UserSessionRespDTO updateCurrentUserRole(Long userId, Integer userType) {
        if (!Objects.equals(userType, USER_TYPE_ACTOR) && !Objects.equals(userType, USER_TYPE_CREW)) {
            throw new BizException("身份类型不支持");
        }

        User user = getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        if (!Objects.equals(user.getUserType(), userType)) {
            User update = new User();
            update.setUserId(userId);
            update.setUserType(userType);
            update.setUpdateUserName("");
            updateById(update);
            user.setUserType(userType);
        }

        return buildCurrentUser(user);
    }

    @Override
    public PageResult<UserAdminListItemDTO> adminUserList(UserAdminQueryDTO query) {
        Set<Long> scopedUserIds = null;
        Integer role = query.getRole() != null ? query.getRole() : query.getUserType();

        if (query.getMembershipStatus() != null) {
            scopedUserIds = intersectUserScope(scopedUserIds, selectUserIdsByMembershipStatus(query.getMembershipStatus()));
        }
        if (query.getReferralStatus() != null) {
            scopedUserIds = intersectUserScope(scopedUserIds, selectUserIdsByReferralStatus(query.getReferralStatus()));
        }
        if (query.getEntitlementStatus() != null) {
            scopedUserIds = intersectUserScope(scopedUserIds, selectUserIdsByEntitlementStatus(query.getEntitlementStatus()));
        }
        if (scopedUserIds != null && scopedUserIds.isEmpty()) {
            return PageResult.empty();
        }

        Page<User> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(User::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getPhone())) {
            wrapper.like(User::getPhone, query.getPhone().trim());
        }
        if (role != null) {
            wrapper.eq(User::getUserType, role);
        }
        if (query.getRealAuthStatus() != null) {
            wrapper.eq(User::getRealAuthStatus, query.getRealAuthStatus());
        }
        if (StringUtils.hasText(query.getNickname())) {
            Set<Long> nickMatchedUserIds = selectUserIdsByNickname(query.getNickname().trim());
            if (nickMatchedUserIds.isEmpty()) {
                return PageResult.empty();
            }
            wrapper.in(User::getUserId, nickMatchedUserIds);
        }
        if (scopedUserIds != null) {
            wrapper.in(User::getUserId, scopedUserIds);
        }
        wrapper.orderByDesc(User::getCreateTime).orderByDesc(User::getUserId);

        Page<User> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Set<Long> userIds = result.getRecords().stream()
                .map(User::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ActorProfile> actorProfileMap = selectActorProfileMap(userIds);
        Map<Long, MembershipAccount> membershipAccountMap = selectMembershipAccountMap(userIds);
        Map<Long, ReferralRecord> selfReferralMap = selectSelfReferralMap(userIds);
        Map<Long, List<UserEntitlementGrant>> entitlementMap = selectEntitlementMap(userIds);

        List<UserAdminListItemDTO> list = result.getRecords().stream()
                .map(user -> toListItem(user,
                        actorProfileMap.get(user.getUserId()),
                        membershipAccountMap.get(user.getUserId()),
                        selfReferralMap.get(user.getUserId()),
                        entitlementMap.getOrDefault(user.getUserId(), Collections.emptyList())))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public UserAdminDetailDTO adminUserDetail(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        ActorProfile actorProfile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, userId)
                .last("limit 1"));
        IdentityVerification latestVerification = identityVerificationMapper.selectOne(new LambdaQueryWrapper<IdentityVerification>()
                .eq(IdentityVerification::getUserId, userId)
                .orderByDesc(IdentityVerification::getCreateTime)
                .orderByDesc(IdentityVerification::getVerificationId)
                .last("limit 1"));
        MembershipAccount membershipAccount = membershipAccountMapper.selectOne(new LambdaQueryWrapper<MembershipAccount>()
                .eq(MembershipAccount::getUserId, userId)
                .last("limit 1"));
        MembershipChangeLog latestMembershipChange = membershipChangeLogMapper.selectOne(new LambdaQueryWrapper<MembershipChangeLog>()
                .eq(MembershipChangeLog::getUserId, userId)
                .orderByDesc(MembershipChangeLog::getCreateTime)
                .orderByDesc(MembershipChangeLog::getChangeLogId)
                .last("limit 1"));
        InviteCode inviteCode = inviteCodeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getUserId, userId)
                .last("limit 1"));
        ReferralRecord selfReferral = referralRecordMapper.selectOne(new LambdaQueryWrapper<ReferralRecord>()
                .eq(ReferralRecord::getInviteeUserId, userId)
                .orderByDesc(ReferralRecord::getCreateTime)
                .orderByDesc(ReferralRecord::getReferralId)
                .last("limit 1"));
        List<ReferralRecord> inviterRecords = referralRecordMapper.selectList(new LambdaQueryWrapper<ReferralRecord>()
                .eq(ReferralRecord::getInviterUserId, userId)
                .orderByDesc(ReferralRecord::getRegisteredAt)
                .orderByDesc(ReferralRecord::getReferralId));
        List<UserEntitlementGrant> grants = userEntitlementGrantMapper.selectList(new LambdaQueryWrapper<UserEntitlementGrant>()
                .eq(UserEntitlementGrant::getUserId, userId)
                .orderByDesc(UserEntitlementGrant::getCreateTime)
                .orderByDesc(UserEntitlementGrant::getGrantId));
        List<PaymentOrder> paymentOrders = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId)
                .orderByDesc(PaymentOrder::getCreateTime)
                .orderByDesc(PaymentOrder::getPaymentOrderId));
        List<RefundOrder> refundOrders = refundOrderMapper.selectList(new LambdaQueryWrapper<RefundOrder>()
                .eq(RefundOrder::getUserId, userId)
                .orderByDesc(RefundOrder::getCreateTime)
                .orderByDesc(RefundOrder::getRefundOrderId));

        UserAdminDetailDTO dto = new UserAdminDetailDTO();
        dto.setUserInfo(buildUserInfo(user));
        dto.setActorProfileSummary(buildActorProfileSummary(actorProfile));
        dto.setVerifySummary(buildVerifySummary(user, latestVerification));
        dto.setReferralSummary(buildReferralSummary(user, inviteCode, selfReferral, inviterRecords));
        dto.setMembershipSummary(buildMembershipSummary(membershipAccount, latestMembershipChange));
        dto.setEntitlementSummary(buildEntitlementSummary(grants));
        dto.setPaymentSummary(buildPaymentSummary(paymentOrders));
        dto.setRefundSummary(buildRefundSummary(refundOrders));
        dto.setRecentOperationLogs(buildRecentOperationLogs(latestVerification, membershipAccount, grants, refundOrders));
        return dto;
    }

    private UserAdminListItemDTO toListItem(User user,
                                            ActorProfile actorProfile,
                                            MembershipAccount membershipAccount,
                                            ReferralRecord selfReferral,
                                            List<UserEntitlementGrant> grants) {
        UserAdminListItemDTO dto = new UserAdminListItemDTO();
        dto.setUserId(user.getUserId());
        dto.setNickname(resolveNickname(user, actorProfile));
        dto.setPhone(user.getPhone());
        dto.setUserType(user.getUserType());
        dto.setRole(resolveRole(user.getUserType()));
        dto.setRealAuthStatus(user.getRealAuthStatus());
        dto.setMembershipTier(membershipAccount == null ? null : membershipAccount.getTier());
        dto.setMembershipStatus(membershipAccount == null ? null : membershipAccount.getStatus());
        dto.setReferralStatus(selfReferral == null ? null : selfReferral.getStatus());
        dto.setValidInviteCount(user.getValidInviteCount());
        dto.setEntitlementSummary(buildEntitlementSummary(grants));
        dto.setRegisteredAt(user.getCreateTime());
        dto.setLastActiveAt(user.getLastLoginTime());
        return dto;
    }

    private UserAdminDetailDTO.UserInfo buildUserInfo(User user) {
        UserAdminDetailDTO.UserInfo info = new UserAdminDetailDTO.UserInfo();
        info.setUserId(user.getUserId());
        info.setUserNo(user.getUserNo());
        info.setAccount(user.getAccount());
        info.setPhone(user.getPhone());
        info.setEmail(user.getEmail());
        info.setUserName(user.getUserName());
        info.setAvatarUrl(user.getAvatarUrl());
        info.setUserType(user.getUserType());
        info.setRole(resolveRole(user.getUserType()));
        info.setRegisterSource(user.getRegisterSource());
        info.setRealAuthStatus(user.getRealAuthStatus());
        info.setInvitedByUserId(user.getInvitedByUserId());
        info.setValidInviteCount(user.getValidInviteCount());
        info.setRegisterDeviceFingerprint(user.getRegisterDeviceFingerprint());
        info.setStatus(user.getStatus());
        info.setRemark(user.getRemark());
        info.setRegisteredAt(user.getCreateTime());
        info.setLastActiveAt(user.getLastLoginTime());
        info.setLastLoginIp(user.getLastLoginIp());
        return info;
    }

    private UserAdminDetailDTO.ActorProfileSummary buildActorProfileSummary(ActorProfile actorProfile) {
        if (actorProfile == null) {
            return null;
        }
        UserAdminDetailDTO.ActorProfileSummary summary = new UserAdminDetailDTO.ActorProfileSummary();
        summary.setActorProfileId(actorProfile.getActorProfileId());
        summary.setActorNo(actorProfile.getActorNo());
        summary.setNickName(actorProfile.getNickName());
        summary.setRealName(actorProfile.getRealName());
        summary.setGender(actorProfile.getGender());
        summary.setBirthday(actorProfile.getBirthday());
        summary.setAge(actorProfile.getAge());
        summary.setLocationProvince(actorProfile.getLocationProvince());
        summary.setLocationCity(actorProfile.getLocationCity());
        summary.setAvatarUrl(actorProfile.getAvatarUrl());
        summary.setCertified(actorProfile.getIsCertified());
        summary.setOpenApply(actorProfile.getIsOpenApply());
        summary.setProfileStatus(actorProfile.getProfileStatus());
        return summary;
    }

    private UserAdminDetailDTO.VerifySummary buildVerifySummary(User user, IdentityVerification verification) {
        UserAdminDetailDTO.VerifySummary summary = new UserAdminDetailDTO.VerifySummary();
        summary.setRealAuthStatus(user.getRealAuthStatus());
        if (verification != null) {
            summary.setLatestVerificationId(verification.getVerificationId());
            summary.setLatestVerificationStatus(verification.getStatus());
            summary.setLatestRealName(verification.getRealName());
            summary.setLatestRejectReason(verification.getRejectReason());
            summary.setLatestSubmittedAt(verification.getCreateTime());
            summary.setLatestReviewedAt(verification.getReviewedAt());
        }
        return summary;
    }

    private UserAdminDetailDTO.ReferralSummary buildReferralSummary(User user,
                                                                    InviteCode inviteCode,
                                                                    ReferralRecord selfReferral,
                                                                    List<ReferralRecord> inviterRecords) {
        UserAdminDetailDTO.ReferralSummary summary = new UserAdminDetailDTO.ReferralSummary();
        summary.setInvitedByUserId(user.getInvitedByUserId());
        summary.setValidInviteCount(user.getValidInviteCount());
        if (inviteCode != null) {
            summary.setInviteCode(inviteCode.getCode());
            summary.setInviteCodeStatus(inviteCode.getStatus());
        }
        if (selfReferral != null) {
            summary.setReferralStatus(selfReferral.getStatus());
            summary.setReferralId(selfReferral.getReferralId());
            summary.setRiskFlag(selfReferral.getRiskFlag());
            summary.setRiskReason(selfReferral.getRiskReason());
        }
        summary.setTotalInviteCount(inviterRecords.size());
        summary.setPendingInviteCount((int) inviterRecords.stream().filter(record -> Objects.equals(record.getStatus(), 0)).count());
        summary.setInvalidInviteCount((int) inviterRecords.stream().filter(record -> Objects.equals(record.getStatus(), 2)).count());
        inviterRecords.stream()
                .map(ReferralRecord::getRegisteredAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .ifPresent(summary::setLastInvitedAt);
        return summary;
    }

    private UserAdminDetailDTO.MembershipSummary buildMembershipSummary(MembershipAccount account, MembershipChangeLog latestChange) {
        if (account == null && latestChange == null) {
            return null;
        }
        UserAdminDetailDTO.MembershipSummary summary = new UserAdminDetailDTO.MembershipSummary();
        if (account != null) {
            summary.setMembershipId(account.getMembershipId());
            summary.setTier(account.getTier());
            summary.setStatus(account.getStatus());
            summary.setEffectiveTime(account.getEffectiveTime());
            summary.setExpireTime(account.getExpireTime());
            summary.setSourceType(account.getSourceType());
            summary.setSourceRefId(account.getSourceRefId());
        }
        if (latestChange != null) {
            summary.setLastChangeReason(latestChange.getChangeReason());
            summary.setLastChangeTime(latestChange.getCreateTime());
        }
        return summary;
    }

    private UserAdminEntitlementSummaryDTO buildEntitlementSummary(List<UserEntitlementGrant> grants) {
        UserAdminEntitlementSummaryDTO summary = new UserAdminEntitlementSummaryDTO();
        if (grants == null || grants.isEmpty()) {
            summary.setTotalCount(0);
            summary.setActiveCount(0);
            summary.setActiveGrantCodes(Collections.emptyList());
            return summary;
        }
        List<UserEntitlementGrant> activeGrants = grants.stream()
                .filter(grant -> Objects.equals(grant.getStatus(), 1))
                .toList();
        summary.setLatestStatus(grants.get(0).getStatus());
        summary.setTotalCount(grants.size());
        summary.setActiveCount(activeGrants.size());
        summary.setActiveGrantCodes(activeGrants.stream()
                .map(UserEntitlementGrant::getGrantCode)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .toList());
        activeGrants.stream()
                .map(UserEntitlementGrant::getExpireTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .ifPresent(summary::setLatestExpireTime);
        return summary;
    }

    private UserAdminDetailDTO.PaymentSummary buildPaymentSummary(List<PaymentOrder> paymentOrders) {
        UserAdminDetailDTO.PaymentSummary summary = new UserAdminDetailDTO.PaymentSummary();
        summary.setTotalOrderCount(paymentOrders.size());
        List<PaymentOrder> paidOrders = paymentOrders.stream()
                .filter(order -> order.getPayStatus() != null && PAID_ORDER_STATUSES.contains(order.getPayStatus()))
                .toList();
        summary.setPaidOrderCount(paidOrders.size());
        summary.setTotalPaidAmount(paidOrders.stream()
                .map(PaymentOrder::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setRecentOrders(paymentOrders.stream().limit(5).map(this::toPaymentOrderSummaryItem).toList());
        return summary;
    }

    private UserAdminDetailDTO.PaymentOrderSummaryItem toPaymentOrderSummaryItem(PaymentOrder order) {
        UserAdminDetailDTO.PaymentOrderSummaryItem item = new UserAdminDetailDTO.PaymentOrderSummaryItem();
        item.setPaymentOrderId(order.getPaymentOrderId());
        item.setOrderNo(order.getOrderNo());
        item.setAmount(order.getAmount());
        item.setPayStatus(order.getPayStatus());
        item.setPayChannel(order.getPayChannel());
        item.setCreatedAt(order.getCreateTime());
        item.setPaidAt(order.getPaidAt());
        return item;
    }

    private UserAdminDetailDTO.RefundSummary buildRefundSummary(List<RefundOrder> refundOrders) {
        UserAdminDetailDTO.RefundSummary summary = new UserAdminDetailDTO.RefundSummary();
        summary.setTotalRefundCount(refundOrders.size());
        summary.setPendingRefundCount((int) refundOrders.stream().filter(order -> Objects.equals(order.getAuditStatus(), 0)).count());
        summary.setProcessingRefundCount((int) refundOrders.stream().filter(order -> Objects.equals(order.getRefundStatus(), 1)).count());
        summary.setSuccessRefundCount((int) refundOrders.stream().filter(order -> Objects.equals(order.getRefundStatus(), 2)).count());
        summary.setTotalRefundAmount(refundOrders.stream()
                .map(RefundOrder::getRefundAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setRecentRefunds(refundOrders.stream().limit(5).map(this::toRefundSummaryItem).toList());
        return summary;
    }

    private UserAdminDetailDTO.RefundOrderSummaryItem toRefundSummaryItem(RefundOrder order) {
        UserAdminDetailDTO.RefundOrderSummaryItem item = new UserAdminDetailDTO.RefundOrderSummaryItem();
        item.setRefundOrderId(order.getRefundOrderId());
        item.setRefundNo(order.getRefundNo());
        item.setRefundAmount(order.getRefundAmount());
        item.setAuditStatus(order.getAuditStatus());
        item.setRefundStatus(order.getRefundStatus());
        item.setAppliedAt(order.getCreateTime());
        item.setRefundedAt(order.getRefundedAt());
        return item;
    }

    private List<UserAdminDetailDTO.RecentOperationLogItem> buildRecentOperationLogs(IdentityVerification verification,
                                                                                      MembershipAccount membershipAccount,
                                                                                      List<UserEntitlementGrant> grants,
                                                                                      List<RefundOrder> refundOrders) {
        List<AdminOperationLog> logs = new ArrayList<>();
        if (verification != null) {
            logs.addAll(selectRecentAdminLogs("identity_verification", Collections.singleton(verification.getVerificationId()), 5));
        }
        if (membershipAccount != null) {
            logs.addAll(selectRecentAdminLogs("membership_account", Collections.singleton(membershipAccount.getMembershipId()), 5));
        }
        Set<Long> grantIds = grants.stream().map(UserEntitlementGrant::getGrantId).collect(Collectors.toSet());
        if (!grantIds.isEmpty()) {
            logs.addAll(selectRecentAdminLogs("user_entitlement_grant", grantIds, 10));
        }
        Set<Long> refundIds = refundOrders.stream().map(RefundOrder::getRefundOrderId).collect(Collectors.toSet());
        if (!refundIds.isEmpty()) {
            logs.addAll(selectRecentAdminLogs("refund_order", refundIds, 10));
        }

        return logs.stream()
                .sorted(Comparator.comparing(AdminOperationLog::getCreateTime, Comparator.nullsLast(LocalDateTime::compareTo)).reversed()
                        .thenComparing(AdminOperationLog::getOperationLogId, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(10)
                .map(this::toRecentOperationLogItem)
                .toList();
    }

    private UserAdminDetailDTO.RecentOperationLogItem toRecentOperationLogItem(AdminOperationLog log) {
        UserAdminDetailDTO.RecentOperationLogItem item = new UserAdminDetailDTO.RecentOperationLogItem();
        item.setOperationLogId(log.getOperationLogId());
        item.setAdminUserId(log.getAdminUserId());
        item.setAdminUserName(log.getAdminUserName());
        item.setModuleCode(log.getModuleCode());
        item.setOperationCode(log.getOperationCode());
        item.setTargetType(log.getTargetType());
        item.setTargetId(log.getTargetId());
        item.setOperationResult(log.getOperationResult());
        item.setCreateTime(log.getCreateTime());
        return item;
    }

    private List<AdminOperationLog> selectRecentAdminLogs(String targetType, Collection<Long> targetIds, int limit) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Collections.emptyList();
        }
        return adminOperationLogMapper.selectList(new LambdaQueryWrapper<AdminOperationLog>()
                .eq(AdminOperationLog::getTargetType, targetType)
                .in(AdminOperationLog::getTargetId, targetIds)
                .orderByDesc(AdminOperationLog::getCreateTime)
                .orderByDesc(AdminOperationLog::getOperationLogId)
                .last("limit " + limit));
    }

    private Set<Long> selectUserIdsByMembershipStatus(Integer membershipStatus) {
        return membershipAccountMapper.selectList(new LambdaQueryWrapper<MembershipAccount>()
                        .eq(MembershipAccount::getStatus, membershipStatus))
                .stream()
                .map(MembershipAccount::getUserId)
                .collect(Collectors.toSet());
    }

    private Set<Long> selectUserIdsByReferralStatus(Integer referralStatus) {
        return referralRecordMapper.selectList(new LambdaQueryWrapper<ReferralRecord>()
                        .eq(ReferralRecord::getStatus, referralStatus))
                .stream()
                .map(ReferralRecord::getInviteeUserId)
                .collect(Collectors.toSet());
    }

    private Set<Long> selectUserIdsByEntitlementStatus(Integer entitlementStatus) {
        return userEntitlementGrantMapper.selectList(new LambdaQueryWrapper<UserEntitlementGrant>()
                        .eq(UserEntitlementGrant::getStatus, entitlementStatus))
                .stream()
                .map(UserEntitlementGrant::getUserId)
                .collect(Collectors.toSet());
    }

    private Set<Long> selectUserIdsByNickname(String nickname) {
        Set<Long> userIds = actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>()
                        .like(ActorProfile::getNickName, nickname)
                        .or()
                        .like(ActorProfile::getRealName, nickname))
                .stream()
                .map(ActorProfile::getUserId)
                .collect(Collectors.toSet());
        userIds.addAll(list(new LambdaQueryWrapper<User>().like(User::getUserName, nickname))
                .stream()
                .map(User::getUserId)
                .collect(Collectors.toSet()));
        return userIds;
    }

    private Map<Long, ActorProfile> selectActorProfileMap(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>().in(ActorProfile::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(ActorProfile::getUserId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, MembershipAccount> selectMembershipAccountMap(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return membershipAccountMapper.selectList(new LambdaQueryWrapper<MembershipAccount>().in(MembershipAccount::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(MembershipAccount::getUserId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, ReferralRecord> selectSelfReferralMap(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return referralRecordMapper.selectList(new LambdaQueryWrapper<ReferralRecord>().in(ReferralRecord::getInviteeUserId, userIds))
                .stream()
                .collect(Collectors.toMap(ReferralRecord::getInviteeUserId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, List<UserEntitlementGrant>> selectEntitlementMap(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userEntitlementGrantMapper.selectList(new LambdaQueryWrapper<UserEntitlementGrant>()
                        .in(UserEntitlementGrant::getUserId, userIds)
                        .orderByDesc(UserEntitlementGrant::getCreateTime)
                        .orderByDesc(UserEntitlementGrant::getGrantId))
                .stream()
                .collect(Collectors.groupingBy(UserEntitlementGrant::getUserId));
    }

    private Set<Long> intersectUserScope(Set<Long> current, Set<Long> incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        Set<Long> intersection = new LinkedHashSet<>(current);
        intersection.retainAll(incoming);
        return intersection;
    }

    private String resolveNickname(User user, ActorProfile actorProfile) {
        if (actorProfile != null && StringUtils.hasText(actorProfile.getNickName())) {
            return actorProfile.getNickName();
        }
        return user.getUserName();
    }

    private String resolveRole(Integer userType) {
        if (Objects.equals(userType, 1)) {
            return "actor";
        }
        if (Objects.equals(userType, 2)) {
            return "company";
        }
        if (Objects.equals(userType, 3)) {
            return "platform_admin";
        }
        return "unknown";
    }

    private UserSessionRespDTO buildCurrentUser(User user) {
        MembershipAccount membershipAccount = membershipAccountMapper.selectOne(new LambdaQueryWrapper<MembershipAccount>()
                .eq(MembershipAccount::getUserId, user.getUserId())
                .eq(MembershipAccount::getStatus, MEMBERSHIP_STATUS_ACTIVE)
                .orderByDesc(MembershipAccount::getExpireTime)
                .orderByDesc(MembershipAccount::getMembershipId)
                .last("limit 1"));
        InviteCode inviteCode = inviteCodeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getUserId, user.getUserId())
                .eq(InviteCode::getStatus, INVITE_CODE_STATUS_ACTIVE)
                .orderByDesc(InviteCode::getCreateTime)
                .orderByDesc(InviteCode::getInviteCodeId)
                .last("limit 1"));
        List<ReferralRecord> inviterRecords = Objects.equals(user.getUserType(), USER_TYPE_ACTOR)
                ? referralRecordMapper.selectList(new LambdaQueryWrapper<ReferralRecord>()
                .eq(ReferralRecord::getInviterUserId, user.getUserId()))
                : Collections.emptyList();

        int validInviteCount = (int) inviterRecords.stream()
                .filter(record -> Objects.equals(record.getStatus(), REFERRAL_STATUS_VALID))
                .count();
        int flaggedInviteCount = (int) inviterRecords.stream()
                .filter(this::isFlaggedReferralRecord)
                .count();

        UserSessionRespDTO dto = new UserSessionRespDTO();
        dto.setUserId(user.getUserId());
        dto.setPhone(user.getPhone());
        dto.setUserType(user.getUserType());
        dto.setStatus(user.getStatus());
        dto.setNickName(user.getUserName());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setRegisteredAt(user.getCreateTime());
        dto.setRealAuthStatus(user.getRealAuthStatus());
        dto.setInviteCode(inviteCode == null ? null : inviteCode.getCode());
        dto.setInvitedByUserId(user.getInvitedByUserId());
        dto.setValidInviteCount(validInviteCount);
        dto.setTotalInviteCount(inviterRecords.size());
        dto.setFlaggedInviteCount(flaggedInviteCount);
        dto.setPendingInviteCount(Math.max(0, inviterRecords.size() - validInviteCount - flaggedInviteCount));
        dto.setMembershipTier(resolveMembershipTier(membershipAccount));
        return dto;
    }

    private boolean isFlaggedReferralRecord(ReferralRecord record) {
        return !Objects.equals(record.getRiskFlag(), REFERRAL_RISK_FLAG_NORMAL)
                || Objects.equals(record.getStatus(), REFERRAL_STATUS_UNDER_REVIEW);
    }

    private String resolveMembershipTier(MembershipAccount membershipAccount) {
        if (membershipAccount == null || membershipAccount.getTier() == null || membershipAccount.getTier() <= 0) {
            return "none";
        }
        return membershipAccount.getTier() >= 2 ? "vip" : "member";
    }
}
