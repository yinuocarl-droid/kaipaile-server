package com.kaipai.module.server.referral.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.payment.entity.PaymentOrder;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantDetailDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantExtendRequestDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantGrantRequestDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantItemDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantListQueryDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantRevokeRequestDTO;
import com.kaipai.module.model.referral.entity.ReferralPolicy;
import com.kaipai.module.model.referral.entity.UserEntitlementGrant;
import com.kaipai.module.model.system.entity.AdminOperationLog;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.payment.mapper.PaymentOrderMapper;
import com.kaipai.module.server.referral.mapper.ReferralRecordMapper;
import com.kaipai.module.server.referral.mapper.ReferralPolicyMapper;
import com.kaipai.module.server.referral.mapper.UserEntitlementGrantMapper;
import com.kaipai.module.server.referral.service.UserEntitlementGrantService;
import com.kaipai.module.server.system.mapper.AdminOperationLogMapper;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEntitlementGrantServiceImpl extends ServiceImpl<UserEntitlementGrantMapper, UserEntitlementGrant> implements UserEntitlementGrantService {

    private static final Pattern POLICY_ID_PATTERN = Pattern.compile("policyId=(\\d+)");

    private final AdminOperationLogger adminOperationLogger;
    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final AdminOperationLogMapper adminOperationLogMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final ReferralPolicyMapper referralPolicyMapper;
    private final ReferralRecordMapper referralRecordMapper;

    @Override
    public PageResult<UserEntitlementGrantItemDTO> adminGrantList(UserEntitlementGrantListQueryDTO query) {
        Set<Long> scopedUserIds = selectScopedUserIds(query);
        if (scopedUserIds != null && scopedUserIds.isEmpty()) {
            return PageResult.empty();
        }

        Page<UserEntitlementGrant> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<UserEntitlementGrant> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(UserEntitlementGrant::getUserId, query.getUserId());
        }
        if (scopedUserIds != null) {
            wrapper.in(UserEntitlementGrant::getUserId, scopedUserIds);
        }
        if (StringUtils.hasText(query.getGrantType())) {
            wrapper.eq(UserEntitlementGrant::getGrantType, query.getGrantType().trim());
        }
        if (StringUtils.hasText(query.getGrantCode())) {
            wrapper.eq(UserEntitlementGrant::getGrantCode, query.getGrantCode().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(UserEntitlementGrant::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(UserEntitlementGrant::getSourceType, query.getSourceType().trim());
        }
        if (query.getSourceRefId() != null) {
            wrapper.eq(UserEntitlementGrant::getSourceRefId, query.getSourceRefId());
        }
        if (query.getEffectiveFrom() != null) {
            wrapper.ge(UserEntitlementGrant::getEffectiveTime, query.getEffectiveFrom());
        }
        if (query.getEffectiveTo() != null) {
            wrapper.le(UserEntitlementGrant::getEffectiveTime, query.getEffectiveTo());
        }
        if (query.getExpireFrom() != null) {
            wrapper.ge(UserEntitlementGrant::getExpireTime, query.getExpireFrom());
        }
        if (query.getExpireTo() != null) {
            wrapper.le(UserEntitlementGrant::getExpireTime, query.getExpireTo());
        }
        page(page, wrapper.orderByDesc(UserEntitlementGrant::getCreateTime).orderByDesc(UserEntitlementGrant::getGrantId));
        if (page.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Set<Long> userIds = page.getRecords().stream()
                .map(UserEntitlementGrant::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> userMap = selectUserMap(userIds);
        Map<Long, ActorProfile> actorProfileMap = selectActorProfileMap(userIds);
        return new PageResult<>(page.getTotal(),
                page.getRecords().stream().map(grant -> toItemDTO(grant, userMap, actorProfileMap)).collect(Collectors.toList()));
    }

    @Override
    public UserEntitlementGrantItemDTO adminGrantItem(Long grantId) {
        UserEntitlementGrant grant = getById(grantId);
        if (grant == null) {
            throw new BizException("grant record not found");
        }
        Set<Long> userIds = Collections.singleton(grant.getUserId());
        Map<Long, User> userMap = selectUserMap(userIds);
        Map<Long, ActorProfile> actorProfileMap = selectActorProfileMap(userIds);
        return toItemDTO(grant, userMap, actorProfileMap);
    }

    @Override
    public UserEntitlementGrantDetailDTO adminGrantDetail(Long grantId) {
        UserEntitlementGrant grant = getById(grantId);
        if (grant == null) {
            throw new BizException("grant record not found");
        }

        User user = userMapper.selectById(grant.getUserId());
        ActorProfile actorProfile = selectActorProfile(grant.getUserId());
        List<AdminOperationLog> logs = adminOperationLogMapper.selectList(new LambdaQueryWrapper<AdminOperationLog>()
                .eq(AdminOperationLog::getModuleCode, "referral")
                .eq(AdminOperationLog::getTargetType, "user_entitlement_grant")
                .eq(AdminOperationLog::getTargetId, grantId)
                .orderByDesc(AdminOperationLog::getCreateTime)
                .orderByDesc(AdminOperationLog::getOperationLogId)
                .last("limit 20"));

        UserEntitlementGrantDetailDTO detail = new UserEntitlementGrantDetailDTO();
        detail.setGrantInfo(toGrantInfo(grant, user, actorProfile));
        detail.setSourceInfo(toSourceInfo(grant));
        detail.setRelatedOrder(toRelatedOrder(grant));
        detail.setRelatedPolicy(toRelatedPolicy(grant));
        detail.setOperatorLogSummary(toOperatorLogSummary(grantId, logs));
        return detail;
    }

    @Override
    public UserEntitlementGrant grantManual(UserEntitlementGrantGrantRequestDTO request) {
        if (request == null || request.getUserId() == null) {
            throw new BizException("用户不能为空");
        }
        if (userMapper.selectById(request.getUserId()) == null) {
            throw new BizException("用户不存在");
        }
        if (!StringUtils.hasText(request.getGrantType())) {
            throw new BizException("资格类型不能为空");
        }
        if (!StringUtils.hasText(request.getGrantCode())) {
            throw new BizException("资格编码不能为空");
        }
        LambdaQueryWrapper<UserEntitlementGrant> existsFlag = new LambdaQueryWrapper<>();
        existsFlag.eq(UserEntitlementGrant::getUserId, request.getUserId())
                .eq(UserEntitlementGrant::getGrantCode, request.getGrantCode().trim());
        if (count(existsFlag) > 0) {
            throw new BizException("grant with same code already exists");
        }
        UserEntitlementGrant grant = new UserEntitlementGrant();
        grant.setUserId(request.getUserId());
        grant.setGrantType(request.getGrantType().trim());
        grant.setGrantCode(request.getGrantCode().trim());
        grant.setStatus(1);
        grant.setEffectiveTime(request.getEffectiveTime() == null ? LocalDateTime.now() : request.getEffectiveTime());
        grant.setExpireTime(request.getExpireTime());
        grant.setSourceType(StringUtils.hasText(request.getSourceType()) ? request.getSourceType().trim() : "manual");
        grant.setSourceRefId(request.getSourceRefId());
        grant.setRemark(request.getRemark());
        save(grant);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("referral")
                .operationCode("grant")
                .targetType("user_entitlement_grant")
                .targetId(grant.getGrantId())
                .afterSnapshot(snapshot(grant))
                .extraContext(grantContext(grant, request.getRemark()))
                .operationResult(1)
                .build());
        return grant;
    }

    @Override
    public void revokeManual(UserEntitlementGrantRevokeRequestDTO request) {
        UserEntitlementGrant grant = getById(request.getGrantId());
        if (grant == null) {
            throw new BizException("grant record not found");
        }
        if (grant.getStatus() != null && grant.getStatus() == 3) {
            throw new BizException("grant already revoked");
        }
        Map<String, Object> beforeSnapshot = snapshot(grant);
        grant.setStatus(3);
        grant.setRemark(request.getRemark());
        grant.setExpireTime(LocalDateTime.now());
        updateById(grant);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("referral")
                .operationCode("revoke")
                .targetType("user_entitlement_grant")
                .targetId(grant.getGrantId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(grant))
                .extraContext(grantContext(grant, request.getRemark()))
                .operationResult(1)
                .build());
    }

    @Override
    public void extendGrant(UserEntitlementGrantExtendRequestDTO request) {
        UserEntitlementGrant grant = getById(request.getGrantId());
        if (grant == null) {
            throw new BizException("grant record not found");
        }
        if (grant.getStatus() == null || grant.getStatus() != 1) {
            throw new BizException("only active grants can be extended");
        }
        Map<String, Object> beforeSnapshot = snapshot(grant);
        grant.setExpireTime(request.getExpireTime());
        grant.setRemark(request.getRemark());
        updateById(grant);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("referral")
                .operationCode("extend")
                .targetType("user_entitlement_grant")
                .targetId(grant.getGrantId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(grant))
                .extraContext(grantContext(grant, request.getRemark()))
                .operationResult(1)
                .build());
    }

    private UserEntitlementGrantItemDTO toItemDTO(UserEntitlementGrant grant,
                                                  Map<Long, User> userMap,
                                                  Map<Long, ActorProfile> actorProfileMap) {
        User user = userMap.get(grant.getUserId());
        ActorProfile actorProfile = actorProfileMap.get(grant.getUserId());
        return new UserEntitlementGrantItemDTO(
                grant.getGrantId(),
                grant.getUserId(),
                resolveNickname(user, actorProfile),
                resolvePhone(user, actorProfile),
                grant.getGrantType(),
                grant.getGrantCode(),
                grant.getStatus(),
                grant.getEffectiveTime(),
                grant.getExpireTime(),
                grant.getSourceType(),
                grant.getSourceRefId(),
                grant.getRemark()
        );
    }

    private UserEntitlementGrantDetailDTO.GrantInfo toGrantInfo(UserEntitlementGrant grant,
                                                                User user,
                                                                ActorProfile actorProfile) {
        UserEntitlementGrantDetailDTO.GrantInfo info = new UserEntitlementGrantDetailDTO.GrantInfo();
        info.setGrantId(grant.getGrantId());
        info.setUserId(grant.getUserId());
        info.setUserName(user == null ? null : user.getUserName());
        info.setNickname(resolveNickname(user, actorProfile));
        info.setPhone(resolvePhone(user, actorProfile));
        info.setUserType(user == null ? null : user.getUserType());
        info.setRealAuthStatus(user == null ? null : user.getRealAuthStatus());
        info.setValidInviteCount(user == null ? null : user.getValidInviteCount());
        info.setGrantType(grant.getGrantType());
        info.setGrantCode(grant.getGrantCode());
        info.setStatus(grant.getStatus());
        info.setEffectiveTime(grant.getEffectiveTime());
        info.setExpireTime(grant.getExpireTime());
        info.setSourceType(grant.getSourceType());
        info.setSourceRefId(grant.getSourceRefId());
        info.setRemark(grant.getRemark());
        info.setCreateUserId(grant.getCreateUserId());
        info.setCreateUserName(grant.getCreateUserName());
        info.setCreateTime(grant.getCreateTime());
        info.setUpdateUserId(grant.getUpdateUserId());
        info.setUpdateUserName(grant.getUpdateUserName());
        info.setLastUpdate(grant.getLastUpdate());
        return info;
    }

    private UserEntitlementGrantDetailDTO.SourceInfo toSourceInfo(UserEntitlementGrant grant) {
        UserEntitlementGrantDetailDTO.SourceInfo info = new UserEntitlementGrantDetailDTO.SourceInfo();
        info.setSourceType(grant.getSourceType());
        info.setSourceRefId(grant.getSourceRefId());
        if (!StringUtils.hasText(grant.getSourceType())) {
            return info;
        }
        if (Objects.equals("payment", grant.getSourceType()) && grant.getSourceRefId() != null) {
            PaymentOrder order = paymentOrderMapper.selectById(grant.getSourceRefId());
            if (order != null) {
                info.setSourceTitle(order.getOrderNo());
                info.setSourceStatus(order.getPayStatus() == null ? null : String.valueOf(order.getPayStatus()));
                info.setRelatedBizType(order.getBizType());
                info.setRelatedBizId(order.getBizRefId());
            }
            return info;
        }
        if (Objects.equals("policy", grant.getSourceType()) && grant.getSourceRefId() != null) {
            ReferralPolicy policy = referralPolicyMapper.selectById(grant.getSourceRefId());
            if (policy != null) {
                info.setSourceTitle(policy.getPolicyName());
                info.setSourceStatus(policy.getEnabled() == null ? null : String.valueOf(policy.getEnabled()));
            }
            return info;
        }
        if (Objects.equals("referral", grant.getSourceType()) && grant.getSourceRefId() != null) {
            var referral = referralRecordMapper.selectById(grant.getSourceRefId());
            if (referral != null) {
                info.setSourceTitle(StringUtils.hasText(referral.getInviteCodeSnapshot())
                        ? referral.getInviteCodeSnapshot()
                        : "referral#" + referral.getReferralId());
                info.setSourceStatus(referral.getStatus() == null ? null : String.valueOf(referral.getStatus()));
                info.setRelatedBizType("invite_referral");
                info.setRelatedBizId(referral.getInviteeUserId());
            }
            return info;
        }
        if (Objects.equals("manual", grant.getSourceType())) {
            info.setSourceTitle("manual");
        }
        return info;
    }

    private UserEntitlementGrantDetailDTO.RelatedOrderInfo toRelatedOrder(UserEntitlementGrant grant) {
        if (!Objects.equals("payment", grant.getSourceType()) || grant.getSourceRefId() == null) {
            return null;
        }
        PaymentOrder order = paymentOrderMapper.selectById(grant.getSourceRefId());
        if (order == null) {
            return null;
        }
        UserEntitlementGrantDetailDTO.RelatedOrderInfo info = new UserEntitlementGrantDetailDTO.RelatedOrderInfo();
        info.setPaymentOrderId(order.getPaymentOrderId());
        info.setOrderNo(order.getOrderNo());
        info.setBizType(order.getBizType());
        info.setBizRefId(order.getBizRefId());
        info.setAmount(order.getAmount());
        info.setPayStatus(order.getPayStatus());
        info.setPayChannel(order.getPayChannel());
        info.setPaidAt(order.getPaidAt());
        return info;
    }

    private UserEntitlementGrantDetailDTO.RelatedPolicyInfo toRelatedPolicy(UserEntitlementGrant grant) {
        ReferralPolicy policy = resolveRelatedPolicy(grant);
        if (policy == null) {
            return null;
        }
        UserEntitlementGrantDetailDTO.RelatedPolicyInfo info = new UserEntitlementGrantDetailDTO.RelatedPolicyInfo();
        info.setPolicyId(policy.getPolicyId());
        info.setPolicyName(policy.getPolicyName());
        info.setEnabled(policy.getEnabled());
        info.setAutoGrantEnabled(policy.getAutoGrantEnabled());
        info.setUpdateUserName(policy.getUpdateUserName());
        info.setLastUpdate(policy.getLastUpdate());
        return info;
    }

    private UserEntitlementGrantDetailDTO.OperatorLogSummary toOperatorLogSummary(Long grantId, List<AdminOperationLog> logs) {
        UserEntitlementGrantDetailDTO.OperatorLogSummary summary = new UserEntitlementGrantDetailDTO.OperatorLogSummary();
        summary.setTotalCount(adminOperationLogMapper.selectCount(new LambdaQueryWrapper<AdminOperationLog>()
                .eq(AdminOperationLog::getModuleCode, "referral")
                .eq(AdminOperationLog::getTargetType, "user_entitlement_grant")
                .eq(AdminOperationLog::getTargetId, grantId)));
        summary.setRecentLogs(logs.stream().map(this::toOperatorLogItem).toList());
        return summary;
    }

    private UserEntitlementGrantDetailDTO.OperatorLogItem toOperatorLogItem(AdminOperationLog log) {
        UserEntitlementGrantDetailDTO.OperatorLogItem item = new UserEntitlementGrantDetailDTO.OperatorLogItem();
        item.setOperationLogId(log.getOperationLogId());
        item.setAdminUserId(log.getAdminUserId());
        item.setAdminUserName(log.getAdminUserName());
        item.setOperationCode(log.getOperationCode());
        item.setOperationResult(log.getOperationResult());
        item.setBeforeSnapshotJson(log.getBeforeSnapshotJson());
        item.setAfterSnapshotJson(log.getAfterSnapshotJson());
        item.setExtraContextJson(log.getExtraContextJson());
        item.setCreateTime(log.getCreateTime());
        return item;
    }

    private Map<String, Object> snapshot(UserEntitlementGrant grant) {
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

    private Map<String, Object> grantContext(UserEntitlementGrant grant, String remark) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("grant_id", grant.getGrantId());
        context.put("user_id", grant.getUserId());
        context.put("grant_type", grant.getGrantType());
        context.put("grant_code", grant.getGrantCode());
        context.put("effective_time", grant.getEffectiveTime());
        context.put("expire_time", grant.getExpireTime());
        context.put("source_type", grant.getSourceType());
        context.put("source_id", grant.getSourceRefId());
        context.put("remark", remark);
        return context;
    }

    private Set<Long> selectScopedUserIds(UserEntitlementGrantListQueryDTO query) {
        if (!StringUtils.hasText(query.getPhone())) {
            return null;
        }
        String phone = query.getPhone().trim();
        Set<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .like(User::getPhone, phone))
                .stream()
                .map(User::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        userIds.addAll(actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>()
                        .like(ActorProfile::getPhone, phone))
                .stream()
                .map(ActorProfile::getUserId)
                .collect(Collectors.toSet()));
        return userIds;
    }

    private Map<Long, User> selectUserMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, ActorProfile> selectActorProfileMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>()
                        .in(ActorProfile::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(ActorProfile::getUserId, Function.identity(), (left, right) -> left));
    }

    private ActorProfile selectActorProfile(Long userId) {
        if (userId == null) {
            return null;
        }
        return actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, userId)
                .last("limit 1"));
    }

    private String resolveNickname(User user, ActorProfile actorProfile) {
        if (actorProfile != null && StringUtils.hasText(actorProfile.getNickName())) {
            return actorProfile.getNickName();
        }
        return user == null ? null : user.getUserName();
    }

    private String resolvePhone(User user, ActorProfile actorProfile) {
        if (user != null && StringUtils.hasText(user.getPhone())) {
            return user.getPhone();
        }
        return actorProfile == null ? null : actorProfile.getPhone();
    }

    private ReferralPolicy resolveRelatedPolicy(UserEntitlementGrant grant) {
        if (grant == null) {
            return null;
        }
        if (Objects.equals("policy", grant.getSourceType()) && grant.getSourceRefId() != null) {
            return referralPolicyMapper.selectById(grant.getSourceRefId());
        }
        if (!Objects.equals("referral", grant.getSourceType())) {
            return null;
        }
        Long policyId = extractPolicyIdFromRemark(grant.getRemark());
        if (policyId != null) {
            ReferralPolicy policy = referralPolicyMapper.selectById(policyId);
            if (policy != null) {
                return policy;
            }
        }
        return referralPolicyMapper.selectOne(new LambdaQueryWrapper<ReferralPolicy>()
                .eq(ReferralPolicy::getEnabled, 1)
                .orderByDesc(ReferralPolicy::getLastUpdate)
                .orderByDesc(ReferralPolicy::getPolicyId)
                .last("limit 1"));
    }

    private Long extractPolicyIdFromRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        Matcher matcher = POLICY_ID_PATTERN.matcher(remark);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
