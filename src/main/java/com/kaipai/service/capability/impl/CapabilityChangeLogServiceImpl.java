package com.kaipai.service.capability.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.capability.dto.CapabilityChangeLogItemDTO;
import com.kaipai.model.capability.dto.CapabilityChangeLogQueryDTO;
import com.kaipai.model.capability.entity.CapabilityChangeLog;
import com.kaipai.model.user.entity.User;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.capability.CapabilityChangeLogMapper;
import com.kaipai.service.capability.CapabilityChangeLogService;
import com.kaipai.mapper.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CapabilityChangeLogServiceImpl extends ServiceImpl<CapabilityChangeLogMapper, CapabilityChangeLog> implements CapabilityChangeLogService {

    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;

    @Override
    public PageResult<CapabilityChangeLogItemDTO> adminLogList(CapabilityChangeLogQueryDTO query) {
        Page<CapabilityChangeLog> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<CapabilityChangeLog> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(CapabilityChangeLog::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getChangeReason())) {
            wrapper.like(CapabilityChangeLog::getChangeReason, query.getChangeReason().trim());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(CapabilityChangeLog::getSourceType, query.getSourceType().trim());
        }
        if (query.getDateFrom() != null) {
            wrapper.ge(CapabilityChangeLog::getCreateTime, query.getDateFrom());
        }
        if (query.getDateTo() != null) {
            wrapper.le(CapabilityChangeLog::getCreateTime, query.getDateTo());
        }
        wrapper.orderByDesc(CapabilityChangeLog::getCreateTime).orderByDesc(CapabilityChangeLog::getChangeLogId);
        Page<CapabilityChangeLog> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Set<Long> userIds = result.getRecords().stream()
                .map(CapabilityChangeLog::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, ActorProfile> actorProfileMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>().in(ActorProfile::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(ActorProfile::getUserId, Function.identity(), (left, right) -> left));

        List<CapabilityChangeLogItemDTO> list = result.getRecords().stream()
                .map(log -> toItem(log, userMap.get(log.getUserId()), actorProfileMap.get(log.getUserId())))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    private CapabilityChangeLogItemDTO toItem(CapabilityChangeLog log, User user, ActorProfile actorProfile) {
        CapabilityChangeLogItemDTO item = new CapabilityChangeLogItemDTO();
        item.setChangeLogId(log.getChangeLogId());
        item.setUserId(log.getUserId());
        item.setNickname(actorProfile != null && StringUtils.hasText(actorProfile.getNickName()) ? actorProfile.getNickName() : user == null ? null : user.getUserName());
        item.setPhone(user == null ? null : user.getPhone());
        item.setBeforeTier(log.getBeforeTier());
        item.setAfterTier(log.getAfterTier());
        item.setChangeReason(log.getChangeReason());
        item.setSourceType(log.getSourceType());
        item.setSourceRefId(log.getSourceRefId());
        item.setEffectiveTime(log.getEffectiveTime());
        item.setExpireTime(log.getExpireTime());
        item.setRemark(log.getRemark());
        item.setCreateTime(log.getCreateTime());
        return item;
    }
}
