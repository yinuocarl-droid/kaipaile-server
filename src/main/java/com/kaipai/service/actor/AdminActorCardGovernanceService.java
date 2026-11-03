package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.model.actor.card.dto.ActorCardListItemDTO;
import com.kaipai.model.actor.card.dto.AdminActorCardGovernanceItemDTO;
import com.kaipai.model.actor.card.dto.AdminActorCardGovernancePageDTO;
import com.kaipai.model.actor.card.dto.AdminActorCardGovernanceQueryDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 00-265: 后台「分享内容」页的 v2 演员卡治理列表（actor_card）。
 * 与 1.0 分享卡（user_share_card）是两套数据，不做合并。
 */
@Service
@RequiredArgsConstructor
public class AdminActorCardGovernanceService {

    static final String STATUS_PUBLISHED = "published";
    static final String STATUS_DRAFT = "draft";

    private final ActorCardMapper actorCardMapper;
    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final ActorCardPublishService publishService;

    public AdminActorCardGovernancePageDTO list(AdminActorCardGovernanceQueryDTO query) {
        AdminActorCardGovernancePageDTO result = new AdminActorCardGovernancePageDTO();
        result.setStatusCounts(countByStatus(query));

        String status = trimToNull(query.getStatus());
        if (status != null && !STATUS_PUBLISHED.equals(status) && !STATUS_DRAFT.equals(status)) {
            return result;
        }

        LambdaQueryWrapper<ActorCard> wrapper = baseQuery(query);
        if (status != null) {
            wrapper.eq(ActorCard::getStatus, status);
        }
        wrapper.orderByDesc(ActorCard::getLastUpdate).orderByDesc(ActorCard::getId);
        Page<ActorCard> page = actorCardMapper.selectPage(
                new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        result.setTotal(page.getTotal());
        List<ActorCard> cards = page.getRecords();
        if (cards == null || cards.isEmpty()) {
            return result;
        }

        Map<Long, Owner> owners = loadOwners(cards);
        result.setList(cards.stream().map(card -> toItem(card, owners.get(card.getUserId()))).toList());
        return result;
    }

    private AdminActorCardGovernancePageDTO.StatusCounts countByStatus(AdminActorCardGovernanceQueryDTO query) {
        AdminActorCardGovernancePageDTO.StatusCounts counts = new AdminActorCardGovernancePageDTO.StatusCounts();
        counts.setTotal(nullToZero(actorCardMapper.selectCount(baseQuery(query))));
        counts.setPublished(nullToZero(actorCardMapper.selectCount(
                baseQuery(query).eq(ActorCard::getStatus, STATUS_PUBLISHED))));
        counts.setDraft(nullToZero(actorCardMapper.selectCount(
                baseQuery(query).eq(ActorCard::getStatus, STATUS_DRAFT))));
        return counts;
    }

    /** 不含 status 的公共条件，计数与列表共用。 */
    private LambdaQueryWrapper<ActorCard> baseQuery(AdminActorCardGovernanceQueryDTO query) {
        LambdaQueryWrapper<ActorCard> wrapper = new LambdaQueryWrapper<>();
        if (query.getCardId() != null) {
            wrapper.eq(ActorCard::getId, query.getCardId());
        }
        if (query.getUserId() != null) {
            wrapper.eq(ActorCard::getUserId, query.getUserId());
        }
        String style = trimToNull(query.getStyle());
        if (style != null) {
            wrapper.eq(ActorCard::getStyle, style);
        }
        return wrapper;
    }

    private AdminActorCardGovernanceItemDTO toItem(ActorCard card, Owner owner) {
        AdminActorCardGovernanceItemDTO dto = new AdminActorCardGovernanceItemDTO();
        dto.setCardId(card.getId());
        dto.setUserId(card.getUserId());
        dto.setStatus(card.getStatus());
        dto.setTitle(card.getTitle());
        dto.setStyle(card.getStyle());
        dto.setCurrentStep(card.getCurrentStep());
        dto.setPublishedVersion(card.getPublishedVersion());
        dto.setPublishedAt(card.getPublishedAt());
        dto.setCreateTime(card.getCreateTime());
        dto.setLastUpdate(card.getLastUpdate());
        if (owner != null) {
            dto.setOwnerName(owner.name());
            dto.setOwnerPhone(owner.phone());
        }
        try {
            ActorCardListItemDTO described = publishService.describe(card);
            dto.setCompletionPercentage(described.getCompletionPercentage());
            dto.setCoverImageUrl(described.getCoverImageUrl());
        } catch (RuntimeException ignored) {
            // 完成度 / 封面派生失败只影响这两个字段，治理列表本身必须可读
        }
        return dto;
    }

    private Map<Long, Owner> loadOwners(List<ActorCard> cards) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (ActorCard card : cards) {
            if (card.getUserId() != null) {
                userIds.add(card.getUserId());
            }
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> users = new HashMap<>();
        for (User user : userMapper.selectBatchIds(userIds)) {
            users.put(user.getUserId(), user);
        }
        Map<Long, ActorProfile> profiles = new HashMap<>();
        for (ActorProfile profile : actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>()
                .in(ActorProfile::getUserId, userIds)
                .orderByDesc(ActorProfile::getActorProfileId))) {
            profiles.putIfAbsent(profile.getUserId(), profile);
        }
        Map<Long, Owner> owners = new HashMap<>();
        for (Long userId : userIds) {
            User user = users.get(userId);
            ActorProfile profile = profiles.get(userId);
            owners.put(userId, new Owner(
                    firstNonBlank(profile == null ? null : profile.getNickName(), user == null ? null : user.getUserName()),
                    firstNonBlank(profile == null ? null : profile.getPhone(), user == null ? null : user.getPhone())));
        }
        return owners;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record Owner(String name, String phone) {
    }
}
