package com.kaipai.module.server.referral.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserEntitlementGrantServiceImpl extends ServiceImpl<UserEntitlementGrantMapper, UserEntitlementGrant> implements UserEntitlementGrantService {

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
        grant.setStatus(3);
        grant.setRemark(request.getRemark());
        grant.setExpireTime(LocalDateTime.now());
        updateById(grant);
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
        grant.setExpireTime(request.getExpireTime());
        grant.setRemark(request.getRemark());
        updateById(grant);
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
}
