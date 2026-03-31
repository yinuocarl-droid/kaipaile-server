package com.kaipai.module.server.membership.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.membership.dto.MembershipChangeLogItemDTO;
import com.kaipai.module.model.membership.dto.MembershipChangeLogQueryDTO;
import com.kaipai.module.model.membership.entity.MembershipChangeLog;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.membership.mapper.MembershipChangeLogMapper;
import com.kaipai.module.server.membership.service.MembershipChangeLogService;
import com.kaipai.module.server.user.mapper.UserMapper;
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
public class MembershipChangeLogServiceImpl extends ServiceImpl<MembershipChangeLogMapper, MembershipChangeLog> implements MembershipChangeLogService {

    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;

    @Override
    public PageResult<MembershipChangeLogItemDTO> adminLogList(MembershipChangeLogQueryDTO query) {
        Page<MembershipChangeLog> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<MembershipChangeLog> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(MembershipChangeLog::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getChangeReason())) {
            wrapper.like(MembershipChangeLog::getChangeReason, query.getChangeReason().trim());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(MembershipChangeLog::getSourceType, query.getSourceType().trim());
        }
        if (query.getDateFrom() != null) {
            wrapper.ge(MembershipChangeLog::getCreateTime, query.getDateFrom());
        }
        if (query.getDateTo() != null) {
            wrapper.le(MembershipChangeLog::getCreateTime, query.getDateTo());
        }
        wrapper.orderByDesc(MembershipChangeLog::getCreateTime).orderByDesc(MembershipChangeLog::getChangeLogId);
        Page<MembershipChangeLog> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Set<Long> userIds = result.getRecords().stream()
                .map(MembershipChangeLog::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, ActorProfile> actorProfileMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>().in(ActorProfile::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(ActorProfile::getUserId, Function.identity(), (left, right) -> left));

        List<MembershipChangeLogItemDTO> list = result.getRecords().stream()
                .map(log -> toItem(log, userMap.get(log.getUserId()), actorProfileMap.get(log.getUserId())))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    private MembershipChangeLogItemDTO toItem(MembershipChangeLog log, User user, ActorProfile actorProfile) {
        MembershipChangeLogItemDTO item = new MembershipChangeLogItemDTO();
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
