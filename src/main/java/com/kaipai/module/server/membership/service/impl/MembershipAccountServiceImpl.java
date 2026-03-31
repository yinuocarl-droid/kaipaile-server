package com.kaipai.module.server.membership.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.ResultCode;
import com.kaipai.module.model.membership.dto.MembershipAccountCloseDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountExtendDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountOpenDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountQueryDTO;
import com.kaipai.module.model.membership.entity.MembershipAccount;
import com.kaipai.module.model.membership.entity.MembershipChangeLog;
import com.kaipai.module.server.membership.mapper.MembershipAccountMapper;
import com.kaipai.module.server.membership.service.MembershipAccountService;
import com.kaipai.module.server.membership.service.MembershipChangeLogService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembershipAccountServiceImpl extends ServiceImpl<MembershipAccountMapper, MembershipAccount> implements MembershipAccountService {

    private final MembershipChangeLogService membershipChangeLogService;
    private final AdminOperationLogger adminOperationLogger;

    @Override
    public PageResult<MembershipAccount> adminAccountList(MembershipAccountQueryDTO query) {
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
        wrapper.orderByDesc(MembershipAccount::getCreateTime);
        Page<MembershipAccount> result = page(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
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
}
