package com.kaipai.module.server.referral.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.referral.dto.AdminReferralPolicyDetailDTO;
import com.kaipai.module.model.referral.dto.AdminReferralPolicyQueryDTO;
import com.kaipai.module.model.referral.dto.AdminReferralPolicySaveDTO;
import com.kaipai.module.model.referral.entity.ReferralPolicy;
import com.kaipai.module.server.referral.mapper.ReferralPolicyMapper;
import com.kaipai.module.server.referral.service.ReferralPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReferralPolicyServiceImpl extends ServiceImpl<ReferralPolicyMapper, ReferralPolicy> implements ReferralPolicyService {

    private final AdminOperationLogger adminOperationLogger;

    @Override
    public PageResult<AdminReferralPolicyDetailDTO> adminPolicyList(AdminReferralPolicyQueryDTO query) {
        Page<ReferralPolicy> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<ReferralPolicy> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getPolicyName())) {
            wrapper.like(ReferralPolicy::getPolicyName, query.getPolicyName().trim());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(ReferralPolicy::getEnabled, query.getEnabled());
        }
        wrapper.orderByDesc(ReferralPolicy::getLastUpdate).orderByDesc(ReferralPolicy::getPolicyId);
        Page<ReferralPolicy> result = page(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toDetail).toList());
    }

    @Override
    public AdminReferralPolicyDetailDTO adminPolicyDetail(Long policyId) {
        return toDetail(requirePolicy(policyId));
    }

    @Override
    public AdminReferralPolicyDetailDTO createPolicy(AdminReferralPolicySaveDTO dto) {
        ReferralPolicy policy = new ReferralPolicy();
        applySave(policy, dto);
        if (policy.getEnabled() == null) {
            policy.setEnabled(1);
        }
        save(policy);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("referral")
                .operationCode("policy_create")
                .targetType("referral_policy")
                .targetId(policy.getPolicyId())
                .afterSnapshot(snapshot(policy))
                .extraContext(snapshot(policy))
                .operationResult(1)
                .build());
        return toDetail(policy);
    }

    @Override
    public AdminReferralPolicyDetailDTO updatePolicy(Long policyId, AdminReferralPolicySaveDTO dto) {
        ReferralPolicy policy = requirePolicy(policyId);
        Map<String, Object> beforeSnapshot = snapshot(policy);
        applySave(policy, dto);
        updateById(policy);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("referral")
                .operationCode("policy_edit")
                .targetType("referral_policy")
                .targetId(policy.getPolicyId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(policy))
                .extraContext(snapshot(policy))
                .operationResult(1)
                .build());
        return toDetail(policy);
    }

    @Override
    public AdminReferralPolicyDetailDTO changePolicyEnabled(Long policyId, boolean enabled, String reason) {
        ReferralPolicy policy = requirePolicy(policyId);
        Map<String, Object> beforeSnapshot = snapshot(policy);
        policy.setEnabled(enabled ? 1 : 0);
        updateById(policy);
        Map<String, Object> extraContext = snapshot(policy);
        extraContext.put("reason", reason);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("referral")
                .operationCode(enabled ? "policy_enable" : "policy_disable")
                .targetType("referral_policy")
                .targetId(policy.getPolicyId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(policy))
                .extraContext(extraContext)
                .operationResult(1)
                .build());
        return toDetail(policy);
    }

    private ReferralPolicy requirePolicy(Long policyId) {
        ReferralPolicy policy = getById(policyId);
        if (policy == null) {
            throw new BizException("邀请规则不存在");
        }
        return policy;
    }

    private void applySave(ReferralPolicy policy, AdminReferralPolicySaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getPolicyName())) {
            throw new BizException("邀请规则名称不能为空");
        }
        policy.setPolicyName(dto.getPolicyName().trim());
        policy.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        policy.setRequireRealAuth(dto.getRequireRealAuth() == null ? 0 : dto.getRequireRealAuth());
        policy.setRequireProfileCompletion(dto.getRequireProfileCompletion() == null ? 0 : dto.getRequireProfileCompletion());
        policy.setProfileCompletionThreshold(dto.getProfileCompletionThreshold() == null ? 0 : dto.getProfileCompletionThreshold());
        policy.setSameDeviceLimit(dto.getSameDeviceLimit() == null ? 0 : dto.getSameDeviceLimit());
        policy.setHourlyInviteLimit(dto.getHourlyInviteLimit() == null ? 0 : dto.getHourlyInviteLimit());
        policy.setAutoGrantEnabled(dto.getAutoGrantEnabled() == null ? 0 : dto.getAutoGrantEnabled());
        policy.setGrantRuleJson(StringUtils.hasText(dto.getGrantRuleJson()) ? dto.getGrantRuleJson().trim() : "{}");
    }

    private AdminReferralPolicyDetailDTO toDetail(ReferralPolicy policy) {
        AdminReferralPolicyDetailDTO dto = new AdminReferralPolicyDetailDTO();
        dto.setPolicyId(policy.getPolicyId());
        dto.setPolicyName(policy.getPolicyName());
        dto.setEnabled(policy.getEnabled());
        dto.setRequireRealAuth(policy.getRequireRealAuth());
        dto.setRequireProfileCompletion(policy.getRequireProfileCompletion());
        dto.setProfileCompletionThreshold(policy.getProfileCompletionThreshold());
        dto.setSameDeviceLimit(policy.getSameDeviceLimit());
        dto.setHourlyInviteLimit(policy.getHourlyInviteLimit());
        dto.setAutoGrantEnabled(policy.getAutoGrantEnabled());
        dto.setGrantRuleJson(policy.getGrantRuleJson());
        dto.setUpdateUserName(policy.getUpdateUserName());
        dto.setLastUpdate(policy.getLastUpdate());
        dto.setVersionRemark(null);
        return dto;
    }

    private Map<String, Object> snapshot(ReferralPolicy policy) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyId", policy.getPolicyId());
        snapshot.put("policyName", policy.getPolicyName());
        snapshot.put("enabled", policy.getEnabled());
        snapshot.put("requireRealAuth", policy.getRequireRealAuth());
        snapshot.put("requireProfileCompletion", policy.getRequireProfileCompletion());
        snapshot.put("profileCompletionThreshold", policy.getProfileCompletionThreshold());
        snapshot.put("sameDeviceLimit", policy.getSameDeviceLimit());
        snapshot.put("hourlyInviteLimit", policy.getHourlyInviteLimit());
        snapshot.put("autoGrantEnabled", policy.getAutoGrantEnabled());
        snapshot.put("grantRuleJson", policy.getGrantRuleJson());
        return snapshot;
    }
}
