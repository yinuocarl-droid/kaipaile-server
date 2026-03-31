package com.kaipai.module.server.verify.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.verify.dto.IdentityVerificationAuditReqDTO;
import com.kaipai.module.model.verify.dto.IdentityVerificationDetailRespDTO;
import com.kaipai.module.model.verify.dto.IdentityVerificationListItemDTO;
import com.kaipai.module.model.verify.dto.IdentityVerificationListReqDTO;
import com.kaipai.module.model.verify.entity.IdentityVerification;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.verify.mapper.IdentityVerificationMapper;
import com.kaipai.module.server.verify.service.IdentityVerificationService;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IdentityVerificationServiceImpl extends ServiceImpl<IdentityVerificationMapper, IdentityVerification> implements IdentityVerificationService {

    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;

    private static final int STATUS_PENDING = 1;
    private static final int STATUS_APPROVED = 2;
    private static final int STATUS_REJECTED = 3;

    @Override
    public PageResult<IdentityVerificationListItemDTO> adminList(IdentityVerificationListReqDTO req) {
        QueryWrapper<IdentityVerification> wrapper = new QueryWrapper<>();
        if (req.getStatus() != null) {
            wrapper.eq("status", req.getStatus());
        }
        if (req.getUserId() != null) {
            wrapper.eq("user_id", req.getUserId());
        }
        wrapper.orderByDesc("create_time");
        long total = baseMapper.selectCount(wrapper);
        if (total == 0) {
            return PageResult.empty();
        }
        int pageNo = Math.max(req.getPageNo(), 1);
        int pageSize = Math.max(req.getPageSize(), 10);
        int offset = (pageNo - 1) * pageSize;
        wrapper.last("LIMIT " + offset + "," + pageSize);
        List<IdentityVerification> records = baseMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return PageResult.empty();
        }
        Set<Long> userIds = records.stream().map(IdentityVerification::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getUserId, u -> u));
        List<IdentityVerificationListItemDTO> list = records.stream().map(record -> {
            IdentityVerificationListItemDTO item = new IdentityVerificationListItemDTO();
            item.setVerificationId(record.getVerificationId());
            item.setUserId(record.getUserId());
            item.setRealName(record.getRealName());
            item.setStatus(record.getStatus());
            item.setSubmitTime(record.getCreateTime());
            User user = userMap.get(record.getUserId());
            if (user != null) {
                item.setPhone(user.getPhone());
                item.setUserName(user.getUserName());
            }
            return item;
        }).collect(Collectors.toList());
        return new PageResult<>(total, list);
    }

    @Override
    public IdentityVerificationDetailRespDTO adminDetail(Long id) {
        IdentityVerification record = getById(id);
        if (record == null) {
            throw new BizException("实名认证记录不存在");
        }
        IdentityVerificationDetailRespDTO dto = new IdentityVerificationDetailRespDTO();
        dto.setVerificationId(record.getVerificationId());
        dto.setUserId(record.getUserId());
        dto.setRealName(record.getRealName());
        dto.setIdCardNoCipher(record.getIdCardNoCipher());
        dto.setStatus(record.getStatus());
        dto.setRejectReason(record.getRejectReason());
        dto.setSubmitTime(record.getCreateTime());
        dto.setReviewedAt(record.getReviewedAt());
        User user = userMapper.selectById(record.getUserId());
        if (user != null) {
            dto.setPhone(user.getPhone());
            dto.setUserName(user.getUserName());
        }
        ActorProfile profile = actorProfileMapper.selectOne(new QueryWrapper<ActorProfile>().lambda()
                .eq(ActorProfile::getUserId, record.getUserId())
        );
        if (profile != null) {
            dto.setActorCertified(Boolean.TRUE.equals(profile.getIsCertified()));
        }
        return dto;
    }

    @Override
    public void approve(Long id, IdentityVerificationAuditReqDTO req) {
        review(id, req, STATUS_APPROVED);
    }

    @Override
    public void reject(Long id, IdentityVerificationAuditReqDTO req) {
        review(id, req, STATUS_REJECTED);
    }

    private void review(Long id, IdentityVerificationAuditReqDTO req, int newStatus) {
        IdentityVerification record = getById(id);
        if (record == null) {
            throw new BizException("实名认证记录不存在");
        }
        Integer currentStatus = record.getStatus();
        if (currentStatus == null || currentStatus != STATUS_PENDING) {
            throw new BizException("只有待审核记录可以操作");
        }
        record.setStatus(newStatus);
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewerId(0L);
        if (newStatus == STATUS_REJECTED) {
            record.setRejectReason(req.getRemark());
        } else {
            record.setRejectReason(null);
        }
        updateById(record);
        User user = userMapper.selectById(record.getUserId());
        if (user != null) {
            user.setRealAuthStatus(newStatus == STATUS_APPROVED ? 2 : 3);
            userMapper.updateById(user);
        }
        ActorProfile profile = actorProfileMapper.selectOne(new QueryWrapper<ActorProfile>().lambda()
                .eq(ActorProfile::getUserId, record.getUserId())
        );
        if (profile != null) {
            profile.setIsCertified(newStatus == STATUS_APPROVED);
            actorProfileMapper.updateById(profile);
        }
    }
}
