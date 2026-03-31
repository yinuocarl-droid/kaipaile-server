package com.kaipai.module.server.referral.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantExtendRequestDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantGrantRequestDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantItemDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantListQueryDTO;
import com.kaipai.module.model.referral.dto.UserEntitlementGrantRevokeRequestDTO;
import com.kaipai.module.model.referral.entity.UserEntitlementGrant;
import com.kaipai.module.server.referral.mapper.UserEntitlementGrantMapper;
import com.kaipai.module.server.referral.service.UserEntitlementGrantService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEntitlementGrantServiceImpl extends ServiceImpl<UserEntitlementGrantMapper, UserEntitlementGrant> implements UserEntitlementGrantService {

    private final AdminOperationLogger adminOperationLogger;

    @Override
    public PageResult<UserEntitlementGrantItemDTO> adminGrantList(UserEntitlementGrantListQueryDTO query) {
        Page<UserEntitlementGrant> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<UserEntitlementGrant> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(UserEntitlementGrant::getUserId, query.getUserId());
        }
        if (query.getGrantType() != null) {
            wrapper.eq(UserEntitlementGrant::getGrantType, query.getGrantType());
        }
        if (query.getGrantCode() != null) {
            wrapper.eq(UserEntitlementGrant::getGrantCode, query.getGrantCode());
        }
        if (query.getStatus() != null) {
            wrapper.eq(UserEntitlementGrant::getStatus, query.getStatus());
        }
        if (query.getSourceType() != null) {
            wrapper.eq(UserEntitlementGrant::getSourceType, query.getSourceType());
        }
        page(page, wrapper.orderByDesc(UserEntitlementGrant::getCreateTime));
        return new PageResult<>(page.getTotal(),
                page.getRecords().stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @Override
    public UserEntitlementGrant grantManual(UserEntitlementGrantGrantRequestDTO request) {
        LambdaQueryWrapper<UserEntitlementGrant> existsFlag = new LambdaQueryWrapper<>();
        existsFlag.eq(UserEntitlementGrant::getUserId, request.getUserId())
                .eq(UserEntitlementGrant::getGrantCode, request.getGrantCode())
                .eq(UserEntitlementGrant::getStatus, 1);
        if (count(existsFlag) > 0) {
            throw new BizException("active grant with same code already exists");
        }
        UserEntitlementGrant grant = new UserEntitlementGrant();
        grant.setUserId(request.getUserId());
        grant.setGrantType(request.getGrantType());
        grant.setGrantCode(request.getGrantCode());
        grant.setStatus(1);
        grant.setEffectiveTime(request.getEffectiveTime() == null ? LocalDateTime.now() : request.getEffectiveTime());
        grant.setExpireTime(request.getExpireTime());
        grant.setSourceType(request.getSourceType());
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

    private UserEntitlementGrantItemDTO toDTO(UserEntitlementGrant grant) {
        return new UserEntitlementGrantItemDTO(
                grant.getGrantId(),
                grant.getUserId(),
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
}
