package com.kaipai.service.card.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.card.dto.ActorMyShareCardItemDTO;
import com.kaipai.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.model.card.dto.AdminShareCardGovernanceDetailDTO;
import com.kaipai.model.card.dto.AdminShareCardGovernanceItemDTO;
import com.kaipai.model.card.dto.AdminShareCardGovernanceQueryDTO;
import com.kaipai.model.card.dto.CreateShareCardDTO;
import com.kaipai.model.card.entity.ActorCardConfig;
import com.kaipai.model.card.entity.ActorSharePreference;
import com.kaipai.model.card.entity.ShareCardContactRequest;
import com.kaipai.model.card.entity.ShareCardViewHistory;
import com.kaipai.model.card.entity.UserShareCard;
import com.kaipai.model.user.entity.User;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.card.ActorCardConfigMapper;
import com.kaipai.mapper.card.ShareCardContactRequestMapper;
import com.kaipai.mapper.card.ShareCardViewHistoryMapper;
import com.kaipai.mapper.card.UserShareCardMapper;
import com.kaipai.service.card.ActorSharePreferenceService;
import com.kaipai.service.card.CardSceneTemplateService;
import com.kaipai.service.card.UserShareCardService;
import com.kaipai.service.card.support.CurrentPhaseShareArtifactSupport;
import com.kaipai.service.card.support.TemplateSceneCodeValidator;
import com.kaipai.mapper.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserShareCardServiceImpl extends ServiceImpl<UserShareCardMapper, UserShareCard> implements UserShareCardService {

    private static final String PRIMARY_TEMPLATE_SCENE_CODE = "classic";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_ARCHIVED = "archived";

    private final CardSceneTemplateService cardSceneTemplateService;
    private final ActorProfileMapper actorProfileMapper;
    private final ActorCardConfigMapper actorCardConfigMapper;
    private final UserMapper userMapper;
    private final ShareCardContactRequestMapper shareCardContactRequestMapper;
    private final ShareCardViewHistoryMapper shareCardViewHistoryMapper;
    private final ActorSharePreferenceService actorSharePreferenceService;

    @Override
    public UserShareCard findActiveCardById(Long shareCardId) {
        if (shareCardId == null || shareCardId <= 0) {
            return null;
        }
        UserShareCard card = getById(shareCardId);
        return card != null && STATUS_ACTIVE.equals(card.getShareStatus()) ? card : null;
    }

    @Override
    public List<UserShareCard> listOwnedCards(Long userId) {
        return list(new LambdaQueryWrapper<UserShareCard>()
                .eq(UserShareCard::getUserId, userId)
                .eq(UserShareCard::getShareStatus, STATUS_ACTIVE)
                .orderByDesc(UserShareCard::getDefaultCard)
                .orderByAsc(UserShareCard::getCreateTime)
                .orderByAsc(UserShareCard::getShareCardId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActorMyShareCardItemDTO createCard(Long currentUserId, CreateShareCardDTO dto) {
        String templateSceneCode = requireTemplateSceneCode(dto.getTemplateSceneCode());
        ActorSceneTemplateRespDTO template = requireEnabledTemplateByTemplateSceneCode(templateSceneCode);
        User user = userMapper.selectById(currentUserId);
        if (user == null || user.getUserType() == null || user.getUserType() != 1) {
            throw new BizException("当前账号不能创建演员分享卡片");
        }
        int inviteCount = user.getValidInviteCount() == null ? 0 : user.getValidInviteCount();
        int requiredInviteCount = template.getRequiredInviteCount() == null ? 0 : Math.max(0, template.getRequiredInviteCount());
        if (inviteCount < requiredInviteCount) {
            throw new BizException(String.format("再邀请 %d 人解锁%s", requiredInviteCount - inviteCount, template.getName()));
        }

        ActorProfile profile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, currentUserId)
                .last("limit 1"));
        if (profile == null || profile.getActorProfileId() == null) {
            throw new BizException("请先完善演员档案后再创建分享卡片");
        }

        UserShareCard existingCard = findActiveOwnedCardByTemplateId(currentUserId, template.getTemplateId());
        if (existingCard != null) {
            ensureInitialSharePreference(existingCard.getShareCardId());
            return toCardItem(existingCard, loadLatestConfig(existingCard.getShareCardId()));
        }

        UserShareCard card = new UserShareCard();
        card.setUserId(currentUserId);
        card.setActorProfileId(profile.getActorProfileId());
        card.setTemplateId(template.getTemplateId());
        card.setShareStatus(STATUS_ACTIVE);
        card.setDefaultCard(PRIMARY_TEMPLATE_SCENE_CODE.equals(templateSceneCode));
        save(card);

        ActorCardConfig config = createInitialConfig(card.getShareCardId(), template);
        createInitialSharePreference(card.getShareCardId());
        return toCardItem(card, config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveCard(Long currentUserId, Long shareCardId) {
        UserShareCard card = getById(shareCardId);
        if (card == null || !currentUserId.equals(card.getUserId())) {
            throw new BizException("分享卡片不存在");
        }
        if (Boolean.TRUE.equals(card.getDefaultCard())) {
            throw new BizException("基础分享卡不能移除");
        }
        if (STATUS_ARCHIVED.equals(card.getShareStatus())) {
            return;
        }
        card.setShareStatus(STATUS_ARCHIVED);
        updateById(card);
    }

    @Override
    public PageResult<AdminShareCardGovernanceItemDTO> adminShareCardList(AdminShareCardGovernanceQueryDTO queryDTO) {
        Page<UserShareCard> page = new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize());
        LambdaQueryWrapper<UserShareCard> wrapper = new LambdaQueryWrapper<UserShareCard>()
                .orderByDesc(UserShareCard::getDefaultCard)
                .orderByDesc(UserShareCard::getLastUpdate)
                .orderByDesc(UserShareCard::getShareCardId);
        if (queryDTO.getShareCardId() != null) {
            wrapper.eq(UserShareCard::getShareCardId, queryDTO.getShareCardId());
        }
        if (queryDTO.getHolderUserId() != null) {
            wrapper.eq(UserShareCard::getUserId, queryDTO.getHolderUserId());
        }
        if (StringUtils.hasText(queryDTO.getTemplateSceneCode())) {
            List<Long> templateIds = findTemplateIdsByTemplateSceneCode(requireTemplateSceneCode(queryDTO.getTemplateSceneCode()));
            if (templateIds.isEmpty()) {
                return PageResult.empty();
            }
            wrapper.in(UserShareCard::getTemplateId, templateIds);
        }
        if (StringUtils.hasText(queryDTO.getShareStatus())) {
            wrapper.eq(UserShareCard::getShareStatus, queryDTO.getShareStatus().trim());
        }
        if (queryDTO.getDefaultCard() != null) {
            wrapper.eq(UserShareCard::getDefaultCard, queryDTO.getDefaultCard());
        }

        Page<UserShareCard> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Map<Long, UserContext> userContextMap = loadUserContextMap(collectOwnerIds(result.getRecords()));
        List<AdminShareCardGovernanceItemDTO> items = result.getRecords().stream()
                .map(card -> buildAdminItemDto(card, userContextMap))
                .toList();
        return new PageResult<>(result.getTotal(), items);
    }

    @Override
    public AdminShareCardGovernanceDetailDTO adminShareCardDetail(Long shareCardId) {
        UserShareCard card = getById(shareCardId);
        if (card == null) {
            throw new BizException("分享卡片不存在");
        }
        ActorSceneTemplateRespDTO template = requireTemplateById(card.getTemplateId());
        Map<Long, UserContext> userContextMap = loadUserContextMap(Collections.singleton(card.getUserId()));
        BindingSnapshot bindingSnapshot = buildBindingSnapshot(card, template);

        AdminShareCardGovernanceDetailDTO dto = new AdminShareCardGovernanceDetailDTO();
        AdminShareCardGovernanceDetailDTO.CardInfo cardInfo = new AdminShareCardGovernanceDetailDTO.CardInfo();
        cardInfo.setShareCardId(card.getShareCardId());
        cardInfo.setTemplateSceneCode(template.getTemplateSceneCode());
        cardInfo.setTemplateName(template.getName());
        cardInfo.setShareStatus(card.getShareStatus());
        cardInfo.setDefaultCard(card.getDefaultCard());
        cardInfo.setProfileUserId(card.getUserId());
        cardInfo.setTemplateId(card.getTemplateId());
        cardInfo.setConfigId(bindingSnapshot.configId());
        cardInfo.setCreateTime(card.getCreateTime());
        cardInfo.setLastUpdate(card.getLastUpdate());
        dto.setCardInfo(cardInfo);
        dto.setOwnerInfo(buildAdminUserInfo(card.getUserId(), userContextMap));

        AdminShareCardGovernanceDetailDTO.BindingInfo bindingInfo = new AdminShareCardGovernanceDetailDTO.BindingInfo();
        bindingInfo.setConfigId(bindingSnapshot.configId());
        bindingInfo.setConfigTemplateSceneCode(bindingSnapshot.configTemplateSceneCode());
        bindingInfo.setBindingConsistent(bindingSnapshot.bindingConsistent());
        bindingInfo.setIssues(bindingSnapshot.issues());
        dto.setBindingInfo(bindingInfo);

        AdminShareCardGovernanceDetailDTO.StatsInfo statsInfo = new AdminShareCardGovernanceDetailDTO.StatsInfo();
        statsInfo.setHistoryCount(countViewHistories(card));
        statsInfo.setTotalContactRequestCount(countContactRequests(card, null));
        statsInfo.setPendingContactRequestCount(countContactRequests(card, "pending"));
        statsInfo.setApprovedContactRequestCount(countContactRequests(card, "approved"));
        statsInfo.setRejectedContactRequestCount(countContactRequests(card, "rejected"));
        statsInfo.setLatestViewedAt(resolveLatestViewedAt(card));
        statsInfo.setLatestRequestedAt(resolveLatestRequestedAt(card));
        dto.setStatsInfo(statsInfo);
        return dto;
    }

    private ActorSceneTemplateRespDTO requireEnabledTemplateByTemplateSceneCode(String templateSceneCode) {
        return cardSceneTemplateService.actorSceneTemplates().stream()
                .filter(item -> templateSceneCode.equals(requireTemplateSceneCode(item.getTemplateSceneCode())))
                .findFirst()
                .orElseThrow(() -> new BizException("分享风格不存在或未启用"));
    }

    private ActorSceneTemplateRespDTO requireTemplateById(Long templateId) {
        if (templateId == null || templateId <= 0) {
            throw new BizException("分享卡片模板未绑定");
        }
        return cardSceneTemplateService.actorSceneTemplates().stream()
                .filter(item -> templateId.equals(item.getTemplateId()))
                .findFirst()
                .orElseThrow(() -> new BizException("分享卡片模板不存在或未启用"));
    }

    private List<Long> findTemplateIdsByTemplateSceneCode(String templateSceneCode) {
        return cardSceneTemplateService.actorSceneTemplates().stream()
                .filter(item -> templateSceneCode.equals(requireTemplateSceneCode(item.getTemplateSceneCode())))
                .map(ActorSceneTemplateRespDTO::getTemplateId)
                .filter(id -> id != null && id > 0)
                .toList();
    }

    private ActorCardConfig createInitialConfig(Long shareCardId, ActorSceneTemplateRespDTO template) {
        ActorSceneTemplateRespDTO.ThemeColors themeColors = template.getThemeColors();
        if (themeColors == null) {
            throw new BizException("模板主题色缺失");
        }
        ActorCardConfig config = new ActorCardConfig();
        config.setShareCardId(shareCardId);
        config.setLayoutVariant(requireText(template.getLayoutVariant(), "模板 layoutVariant 缺失"));
        config.setPrimaryColor(requireText(themeColors.getPrimary(), "模板 primary 颜色缺失"));
        config.setAccentColor(requireText(themeColors.getAccent(), "模板 accent 颜色缺失"));
        config.setBackgroundColor(requireText(themeColors.getBackground(), "模板 background 颜色缺失"));
        config.setHighlightedExperienceIds("[]");
        config.setHighlightedPhotoUrls("[]");
        config.setTagOrderJson("[]");
        actorCardConfigMapper.insert(config);
        return config;
    }

    private void ensureInitialSharePreference(Long shareCardId) {
        if (shareCardId == null || shareCardId <= 0) {
            throw new BizException("分享卡片主键缺失");
        }
        ActorSharePreference preference = actorSharePreferenceService.getOne(new LambdaQueryWrapper<ActorSharePreference>()
                .eq(ActorSharePreference::getShareCardId, shareCardId)
                .last("limit 1"), false);
        if (preference == null) {
            createInitialSharePreference(shareCardId);
        }
    }

    private void createInitialSharePreference(Long shareCardId) {
        ActorSharePreference preference = new ActorSharePreference();
        preference.setShareCardId(shareCardId);
        preference.setPreferredArtifact(CurrentPhaseShareArtifactSupport.MINI_PROGRAM_CARD);
        actorSharePreferenceService.save(preference);
    }

    private UserShareCard findActiveOwnedCardByTemplateId(Long userId, Long templateId) {
        if (userId == null || templateId == null) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<UserShareCard>()
                .eq(UserShareCard::getUserId, userId)
                .eq(UserShareCard::getTemplateId, templateId)
                .eq(UserShareCard::getShareStatus, STATUS_ACTIVE)
                .orderByDesc(UserShareCard::getDefaultCard)
                .orderByAsc(UserShareCard::getShareCardId)
                .last("limit 1"), false);
    }

    private ActorCardConfig loadLatestConfig(Long shareCardId) {
        if (shareCardId == null) {
            return null;
        }
        return actorCardConfigMapper.selectOne(new LambdaQueryWrapper<ActorCardConfig>()
                .eq(ActorCardConfig::getShareCardId, shareCardId)
                .orderByDesc(ActorCardConfig::getLastUpdate)
                .orderByDesc(ActorCardConfig::getConfigId)
                .last("limit 1"));
    }

    private ActorMyShareCardItemDTO toCardItem(UserShareCard card, ActorCardConfig config) {
        if (config == null) {
            throw new BizException("分享卡片配置未绑定");
        }
        ActorSceneTemplateRespDTO template = requireTemplateById(card.getTemplateId());
        ActorMyShareCardItemDTO dto = new ActorMyShareCardItemDTO();
        dto.setCardId(card.getShareCardId());
        dto.setConfigId(config.getConfigId());
        dto.setProfileUserId(card.getUserId());
        dto.setTemplateId(card.getTemplateId());
        dto.setTemplateSceneCode(template.getTemplateSceneCode());
        dto.setLayoutVariant(config.getLayoutVariant());
        dto.setPrimaryColor(config.getPrimaryColor());
        dto.setAccentColor(config.getAccentColor());
        dto.setBackgroundColor(config.getBackgroundColor());
        dto.setDefaultCard(Boolean.TRUE.equals(card.getDefaultCard()));
        dto.setCreateTime(card.getCreateTime());
        dto.setUpdateTime(card.getLastUpdate());
        return dto;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private String requireTemplateSceneCode(String templateSceneCode) {
        return TemplateSceneCodeValidator.requireAllowed(templateSceneCode);
    }

    private AdminShareCardGovernanceItemDTO buildAdminItemDto(UserShareCard card, Map<Long, UserContext> userContextMap) {
        UserContext ownerContext = userContextMap.get(card.getUserId());
        ActorSceneTemplateRespDTO template = requireTemplateById(card.getTemplateId());
        BindingSnapshot bindingSnapshot = buildBindingSnapshot(card, template);

        AdminShareCardGovernanceItemDTO dto = new AdminShareCardGovernanceItemDTO();
        dto.setShareCardId(card.getShareCardId());
        dto.setHolderUserId(card.getUserId());
        dto.setOwnerName(ownerContext == null ? null : ownerContext.displayName());
        dto.setOwnerPhone(ownerContext == null ? null : ownerContext.phone());
        dto.setTemplateSceneCode(template.getTemplateSceneCode());
        dto.setTemplateName(template.getName());
        dto.setShareStatus(card.getShareStatus());
        dto.setDefaultCard(card.getDefaultCard());
        dto.setProfileUserId(card.getUserId());
        dto.setTemplateId(card.getTemplateId());
        dto.setConfigId(bindingSnapshot.configId());
        dto.setBindingConsistent(bindingSnapshot.bindingConsistent());
        dto.setIssueCount(bindingSnapshot.issues().size());
        dto.setHistoryCount(countViewHistories(card));
        dto.setTotalContactRequestCount(countContactRequests(card, null));
        dto.setPendingContactRequestCount(countContactRequests(card, "pending"));
        dto.setApprovedContactRequestCount(countContactRequests(card, "approved"));
        dto.setCreateTime(card.getCreateTime());
        dto.setLastUpdate(card.getLastUpdate());
        return dto;
    }

    private AdminShareCardGovernanceDetailDTO.UserInfo buildAdminUserInfo(Long userId, Map<Long, UserContext> userContextMap) {
        UserContext context = userContextMap.get(userId);
        AdminShareCardGovernanceDetailDTO.UserInfo dto = new AdminShareCardGovernanceDetailDTO.UserInfo();
        dto.setUserId(userId);
        if (context == null) {
            dto.setDisplayName(null);
            return dto;
        }
        dto.setUserName(context.userName());
        dto.setNickName(context.nickName());
        dto.setDisplayName(context.displayName());
        dto.setPhone(context.phone());
        dto.setAvatarUrl(context.avatarUrl());
        dto.setRealAuthStatus(context.realAuthStatus());
        dto.setValidInviteCount(context.validInviteCount());
        return dto;
    }

    private BindingSnapshot buildBindingSnapshot(UserShareCard card, ActorSceneTemplateRespDTO template) {
        if (card == null) {
            return new BindingSnapshot(false, Collections.singletonList("分享卡片不存在"), null, null);
        }
        List<String> issues = new ArrayList<>();
        ActorCardConfig config = actorCardConfigMapper.selectOne(new LambdaQueryWrapper<ActorCardConfig>()
                .eq(ActorCardConfig::getShareCardId, card.getShareCardId())
                .orderByDesc(ActorCardConfig::getLastUpdate)
                .orderByDesc(ActorCardConfig::getConfigId)
                .last("limit 1"));
        if (config == null) {
            issues.add("分享卡片配置不存在");
        }
        return new BindingSnapshot(
                issues.isEmpty(),
                issues,
                config == null ? null : config.getConfigId(),
                template == null ? null : template.getTemplateSceneCode());
    }

    private long countViewHistories(UserShareCard card) {
        return shareCardViewHistoryMapper.selectCount(new LambdaQueryWrapper<ShareCardViewHistory>()
                .eq(ShareCardViewHistory::getShareCardId, card.getShareCardId()));
    }

    private long countContactRequests(UserShareCard card, String status) {
        LambdaQueryWrapper<ShareCardContactRequest> wrapper = new LambdaQueryWrapper<ShareCardContactRequest>()
                .eq(ShareCardContactRequest::getShareCardId, card.getShareCardId());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ShareCardContactRequest::getStatus, status.trim());
        }
        return shareCardContactRequestMapper.selectCount(wrapper);
    }

    private LocalDateTime resolveLatestViewedAt(UserShareCard card) {
        ShareCardViewHistory history = shareCardViewHistoryMapper.selectOne(new LambdaQueryWrapper<ShareCardViewHistory>()
                .eq(ShareCardViewHistory::getShareCardId, card.getShareCardId())
                .orderByDesc(ShareCardViewHistory::getViewedAt)
                .orderByDesc(ShareCardViewHistory::getHistoryId)
                .last("limit 1"));
        return history == null ? null : history.getViewedAt();
    }

    private LocalDateTime resolveLatestRequestedAt(UserShareCard card) {
        ShareCardContactRequest request = shareCardContactRequestMapper.selectOne(new LambdaQueryWrapper<ShareCardContactRequest>()
                .eq(ShareCardContactRequest::getShareCardId, card.getShareCardId())
                .orderByDesc(ShareCardContactRequest::getRequestedAt)
                .orderByDesc(ShareCardContactRequest::getRequestId)
                .last("limit 1"));
        return request == null ? null : request.getRequestedAt();
    }

    private Map<Long, UserContext> loadUserContextMap(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> userMap = new HashMap<>();
        for (User user : userMapper.selectBatchIds(userIds)) {
            userMap.put(user.getUserId(), user);
        }
        Map<Long, ActorProfile> profileMap = new HashMap<>();
        for (ActorProfile profile : actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>()
                .in(ActorProfile::getUserId, userIds)
                .orderByDesc(ActorProfile::getActorProfileId))) {
            profileMap.putIfAbsent(profile.getUserId(), profile);
        }

        Map<Long, UserContext> result = new HashMap<>();
        for (Long userId : userIds) {
            User user = userMap.get(userId);
            ActorProfile profile = profileMap.get(userId);
            result.put(userId, new UserContext(
                    userId,
                    user == null ? null : trimToNull(user.getUserName()),
                    profile == null ? null : trimToNull(profile.getNickName()),
                    firstNonBlank(profile == null ? null : profile.getNickName(), user == null ? null : user.getUserName(), null),
                    firstNonBlank(profile == null ? null : profile.getPhone(), user == null ? null : user.getPhone(), null),
                    firstNonBlank(profile == null ? null : profile.getAvatarUrl(), user == null ? null : user.getAvatarUrl(), "/static/logo.png"),
                    user == null ? null : user.getRealAuthStatus(),
                    user == null ? null : user.getValidInviteCount()));
        }
        return result;
    }

    private Set<Long> collectOwnerIds(List<UserShareCard> cards) {
        Set<Long> userIds = new HashSet<>();
        for (UserShareCard card : cards) {
            if (card.getUserId() != null) {
                userIds.add(card.getUserId());
            }
        }
        return userIds;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private record UserContext(
            Long userId,
            String userName,
            String nickName,
            String displayName,
            String phone,
            String avatarUrl,
            Integer realAuthStatus,
            Integer validInviteCount) {
    }

    private record BindingSnapshot(
            boolean bindingConsistent,
            List<String> issues,
            Long configId,
            String configTemplateSceneCode) {
    }
}



