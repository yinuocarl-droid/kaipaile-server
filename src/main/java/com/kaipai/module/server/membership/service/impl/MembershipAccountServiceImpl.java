package com.kaipai.module.server.membership.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembershipAccountServiceImpl extends ServiceImpl<MembershipAccountMapper, MembershipAccount> implements MembershipAccountService {

    private final MembershipChangeLogService membershipChangeLogService;

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
    }

    @Override
    @Transactional
    public void extendAccount(Long userId, MembershipAccountExtendDTO dto) {
        MembershipAccount account = lambdaQuery().eq(MembershipAccount::getUserId, userId).one();
        if (account == null) {
            throw new BizException(ResultCode.PARAM_ERROR.getMessage());
        }
        Integer beforeTier = account.getTier();
        account.setExpireTime(dto.getExpireTime());
        account.setStatus(1);
        updateById(account);
        logChange(userId, beforeTier, account.getTier(), "手动延期", account.getSourceType(), account.getSourceRefId(), dto.getRemark(), account);
    }

    @Override
    @Transactional
    public void closeAccount(Long userId, MembershipAccountCloseDTO dto) {
        MembershipAccount account = lambdaQuery().eq(MembershipAccount::getUserId, userId).one();
        if (account == null) {
            throw new BizException(ResultCode.PARAM_ERROR.getMessage());
        }
        Integer beforeTier = account.getTier();
        account.setStatus(3);
        account.setExpireTime(LocalDateTime.now());
        updateById(account);
        logChange(userId, beforeTier, account.getTier(), "手动关闭", account.getSourceType(), account.getSourceRefId(), dto.getRemark(), account);
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
}
