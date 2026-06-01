package com.kaipai.service.card.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.card.dto.AdminContactRequestDetailDTO;
import com.kaipai.model.card.dto.AdminContactRequestItemDTO;
import com.kaipai.model.card.dto.AdminContactRequestQueryDTO;
import com.kaipai.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.model.card.dto.ContactRequestApplyDTO;
import com.kaipai.model.card.dto.ContactRequestDecisionDTO;
import com.kaipai.model.card.dto.ContactRequestItemDTO;
import com.kaipai.model.card.dto.ContactRequestStatusRespDTO;
import com.kaipai.model.card.entity.ShareCardContactRequest;
import com.kaipai.model.card.entity.UserShareCard;
import com.kaipai.model.user.entity.User;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.card.ShareCardContactRequestMapper;
import com.kaipai.service.card.CardSceneTemplateService;
import com.kaipai.service.card.ShareCardContactRequestService;
import com.kaipai.service.card.UserShareCardService;
import com.kaipai.service.card.support.TemplateSceneCodeValidator;
import com.kaipai.mapper.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShareCardContactRequestServiceImpl extends ServiceImpl<ShareCardContactRequestMapper, ShareCardContactRequest> implements ShareCardContactRequestService {

    private static final String STATUS_NONE = "base";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";

    private final ActorProfileMapper actorProfileMapper;
    private final UserMapper userMapper;
    private final CardSceneTemplateService cardSceneTemplateService;
    private final UserShareCardService userShareCardService;

    @Override
    public PageResult<AdminContactRequestItemDTO> adminContactRequestList(AdminContactRequestQueryDTO query) {
        Page<ShareCardContactRequest> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<ShareCardContactRequest> wrapper = new LambdaQueryWrapper<ShareCardContactRequest>()
                .isNotNull(ShareCardContactRequest::getShareCardId)
                .orderByDesc(ShareCardContactRequest::getRequestedAt)
                .orderByDesc(ShareCardContactRequest::getRequestId);

        if (query.getRequestId() != null) {
            wrapper.eq(ShareCardContactRequest::getRequestId, query.getRequestId());
        }
        if (query.getShareCardId() != null) {
            wrapper.eq(ShareCardContactRequest::getShareCardId, query.getShareCardId());
        }
        if (query.getHolderUserId() != null) {
            List<Long> shareCardIds = findActiveShareCardIdsByOwner(query.getHolderUserId());
            if (shareCardIds.isEmpty()) {
                return PageResult.empty();
            }
            wrapper.in(ShareCardContactRequest::getShareCardId, shareCardIds);
        }
        if (query.getViewerUserId() != null) {
            wrapper.eq(ShareCardContactRequest::getViewerUserId, query.getViewerUserId());
        }
        if (StringUtils.hasText(query.getTemplateSceneCode())) {
            List<Long> shareCardIds = findActiveShareCardIdsByTemplateSceneCode(query.getTemplateSceneCode());
            if (shareCardIds.isEmpty()) {
                return PageResult.empty();
            }
            wrapper.in(ShareCardContactRequest::getShareCardId, shareCardIds);
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ShareCardContactRequest::getStatus, query.getStatus().trim());
        }
        if (query.getRequestedAtFrom() != null) {
            wrapper.ge(ShareCardContactRequest::getRequestedAt, query.getRequestedAtFrom());
        }
        if (query.getRequestedAtTo() != null) {
            wrapper.le(ShareCardContactRequest::getRequestedAt, query.getRequestedAtTo());
        }
        if (query.getDecidedAtFrom() != null) {
            wrapper.ge(ShareCardContactRequest::getDecidedAt, query.getDecidedAtFrom());
        }
        if (query.getDecidedAtTo() != null) {
            wrapper.le(ShareCardContactRequest::getDecidedAt, query.getDecidedAtTo());
        }

        Page<ShareCardContactRequest> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Map<Long, UserShareCard> requestCardMap = new HashMap<>();
        for (ShareCardContactRequest request : result.getRecords()) {
            requestCardMap.put(request.getRequestId(), resolveRequestCard(request));
        }
        Map<Long, UserContext> userContextMap = loadUserContextMap(collectUserIds(result.getRecords()));
        List<AdminContactRequestItemDTO> list = result.getRecords().stream()
                .map(request -> buildAdminItemDto(request, requestCardMap.get(request.getRequestId()), userContextMap))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public AdminContactRequestDetailDTO adminContactRequestDetail(Long requestId) {
        ShareCardContactRequest request = getById(requestId);
        if (request == null) {
            throw new BizException("联系方式申请不存在");
        }

        UserShareCard shareCard = resolveRequestCard(request);
        if (shareCard == null) {
            throw new BizException("分享卡片不存在");
        }
        Map<Long, UserContext> userContextMap = loadUserContextMap(collectUserIds(Collections.singletonList(request)));
        Long resolvedHolderUserId = shareCard.getUserId();
        ActorSceneTemplateRespDTO resolvedTemplate = requireTemplate(shareCard);
        String resolvedTemplateSceneCode = resolvedTemplate.getTemplateSceneCode();

        AdminContactRequestDetailDTO dto = new AdminContactRequestDetailDTO();
        AdminContactRequestDetailDTO.RequestInfo requestInfo = new AdminContactRequestDetailDTO.RequestInfo();
        requestInfo.setRequestId(request.getRequestId());
        requestInfo.setStatus(request.getStatus());
        requestInfo.setTemplateName(resolvedTemplate.getName());
        requestInfo.setApplicantNote(request.getApplicantNote());
        requestInfo.setDecisionNote(request.getDecisionNote());
        requestInfo.setRequestedAt(request.getRequestedAt());
        requestInfo.setDecidedAt(request.getDecidedAt());
        dto.setRequestInfo(requestInfo);

        AdminContactRequestDetailDTO.CardInfo cardInfo = new AdminContactRequestDetailDTO.CardInfo();
        cardInfo.setShareCardId(shareCard.getShareCardId());
        cardInfo.setProfileUserId(shareCard.getUserId());
        cardInfo.setTemplateSceneCode(resolvedTemplateSceneCode);
        cardInfo.setShareStatus(shareCard.getShareStatus());
        cardInfo.setDefaultCard(shareCard.getDefaultCard());
        dto.setCardInfo(cardInfo);
        dto.setOwnerInfo(buildAdminUserInfo(resolvedHolderUserId, userContextMap));
        dto.setViewerInfo(buildAdminUserInfo(request.getViewerUserId(), userContextMap));
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContactRequestStatusRespDTO adminApprove(Long requestId, ContactRequestDecisionDTO dto) {
        return decideRequest(requirePendingRequest(requestId), STATUS_APPROVED, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContactRequestStatusRespDTO adminReject(Long requestId, ContactRequestDecisionDTO dto) {
        return decideRequest(requirePendingRequest(requestId), STATUS_REJECTED, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContactRequestStatusRespDTO apply(Long viewerUserId, ContactRequestApplyDTO dto) {
        UserShareCard card = resolveApplyCard(dto);
        if (viewerUserId.equals(card.getUserId())) {
            throw new BizException("不能向自己的卡片申请联系方式");
        }
        if (!hasContactPhone(card.getUserId())) {
            throw new BizException("该演员暂未补充联系电话");
        }

        ShareCardContactRequest latest = latestRequest(viewerUserId, card);
        if (latest != null && (STATUS_PENDING.equals(latest.getStatus()) || STATUS_APPROVED.equals(latest.getStatus()))) {
            return buildStatusResp(latest, viewerUserId);
        }

        ShareCardContactRequest request = new ShareCardContactRequest();
        request.setViewerUserId(viewerUserId);
        request.setShareCardId(card.getShareCardId());
        request.setStatus(STATUS_PENDING);
        request.setApplicantNote(trimToNull(dto.getApplicantNote()));
        request.setRequestedAt(LocalDateTime.now());
        save(request);
        return buildStatusResp(request, viewerUserId);
    }

    @Override
    public ContactRequestStatusRespDTO status(Long viewerUserId, Long shareCardId) {
        UserShareCard card = resolveStatusCard(shareCardId);
        ShareCardContactRequest latest = latestRequest(viewerUserId, card);
        if (latest == null) {
            ContactRequestStatusRespDTO dto = new ContactRequestStatusRespDTO();
            dto.setHolderUserId(card.getUserId());
            dto.setViewerUserId(viewerUserId);
            dto.setShareCardId(card.getShareCardId());
            ActorSceneTemplateRespDTO template = requireTemplate(card);
            dto.setTemplateSceneCode(template.getTemplateSceneCode());
            dto.setTemplateName(template.getName());
            dto.setStatus(STATUS_NONE);
            dto.setHasContactPhone(hasContactPhone(card.getUserId()));
            dto.setCanViewPhone(false);
            if (card != null) {
                dto.setRequestId(null);
            }
            return dto;
        }
        return buildStatusResp(latest, viewerUserId);
    }

    @Override
    public List<ContactRequestItemDTO> approvedContacts(Long viewerUserId) {
        List<ShareCardContactRequest> requests = list(new LambdaQueryWrapper<ShareCardContactRequest>()
                .eq(ShareCardContactRequest::getViewerUserId, viewerUserId)
                .eq(ShareCardContactRequest::getStatus, STATUS_APPROVED)
                .isNotNull(ShareCardContactRequest::getShareCardId)
                .orderByDesc(ShareCardContactRequest::getDecidedAt)
                .orderByDesc(ShareCardContactRequest::getRequestId));
        Map<String, ContactRequestItemDTO> latestByCard = new LinkedHashMap<>();
        for (ShareCardContactRequest request : requests) {
            UserShareCard card = resolveRequestCard(request);
            if (card == null || card.getShareCardId() == null) {
                continue;
            }
            latestByCard.putIfAbsent(buildCardKey(card), buildItemDto(request, card, true));
        }
        return new ArrayList<>(latestByCard.values());
    }

    @Override
    public List<ContactRequestItemDTO> ownedRequests(Long holderUserId, String status) {
        List<Long> shareCardIds = findActiveShareCardIdsByOwner(holderUserId);
        if (shareCardIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ShareCardContactRequest> wrapper = new LambdaQueryWrapper<ShareCardContactRequest>()
                .in(ShareCardContactRequest::getShareCardId, shareCardIds)
                .isNotNull(ShareCardContactRequest::getShareCardId)
                .orderByDesc(ShareCardContactRequest::getRequestedAt)
                .orderByDesc(ShareCardContactRequest::getRequestId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(ShareCardContactRequest::getStatus, status.trim());
        }
        return list(wrapper).stream()
                .map(request -> buildItemDto(request, resolveRequestCard(request), false))
                .filter(item -> item.getShareCardId() != null)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContactRequestStatusRespDTO approve(Long holderUserId, Long requestId, ContactRequestDecisionDTO dto) {
        return decideRequest(requireOwnedPendingRequest(holderUserId, requestId), STATUS_APPROVED, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContactRequestStatusRespDTO reject(Long holderUserId, Long requestId, ContactRequestDecisionDTO dto) {
        return decideRequest(requireOwnedPendingRequest(holderUserId, requestId), STATUS_REJECTED, dto);
    }

    private ContactRequestStatusRespDTO decideRequest(ShareCardContactRequest request, String status, ContactRequestDecisionDTO dto) {
        request.setStatus(status);
        request.setDecisionNote(trimToNull(dto == null ? null : dto.getDecisionNote()));
        request.setDecidedAt(LocalDateTime.now());
        updateById(request);
        return buildStatusResp(request, request.getViewerUserId());
    }

    private ShareCardContactRequest requirePendingRequest(Long requestId) {
        ShareCardContactRequest request = getById(requestId);
        if (request == null) {
            throw new BizException("联系申请不存在");
        }
        if (!STATUS_PENDING.equals(request.getStatus())) {
            throw new BizException("当前申请状态不可处理");
        }
        return request;
    }

    private ShareCardContactRequest requireOwnedPendingRequest(Long holderUserId, Long requestId) {
        ShareCardContactRequest request = getById(requestId);
        UserShareCard card = resolveRequestCard(request);
        if (request == null || card == null || !holderUserId.equals(card.getUserId())) {
            throw new BizException("联系申请不存在");
        }
        if (!STATUS_PENDING.equals(request.getStatus())) {
            throw new BizException("当前申请状态不可处理");
        }
        return request;
    }

    private AdminContactRequestItemDTO buildAdminItemDto(ShareCardContactRequest request, UserShareCard card, Map<Long, UserContext> userContextMap) {
        if (card == null) {
            throw new BizException("分享卡片不存在");
        }
        Long resolvedHolderUserId = card.getUserId();
        Long resolvedShareCardId = card.getShareCardId();
        ActorSceneTemplateRespDTO template = requireTemplate(card);
        String resolvedTemplateSceneCode = template.getTemplateSceneCode();
        UserContext ownerContext = userContextMap.get(resolvedHolderUserId);
        UserContext viewerContext = userContextMap.get(request.getViewerUserId());

        AdminContactRequestItemDTO dto = new AdminContactRequestItemDTO();
        dto.setRequestId(request.getRequestId());
        dto.setShareCardId(resolvedShareCardId);
        dto.setTemplateSceneCode(resolvedTemplateSceneCode);
        dto.setTemplateName(template.getName());
        dto.setStatus(request.getStatus());
        dto.setHolderUserId(resolvedHolderUserId);
        dto.setOwnerName(ownerContext == null ? null : ownerContext.displayName());
        dto.setOwnerPhone(ownerContext == null ? null : ownerContext.phone());
        dto.setViewerUserId(request.getViewerUserId());
        dto.setViewerName(viewerContext == null ? "访客" : viewerContext.displayName());
        dto.setViewerPhone(viewerContext == null ? null : viewerContext.phone());
        dto.setRequestedAt(request.getRequestedAt());
        dto.setDecidedAt(request.getDecidedAt());
        return dto;
    }

    private AdminContactRequestDetailDTO.UserInfo buildAdminUserInfo(Long userId, Map<Long, UserContext> userContextMap) {
        UserContext context = userContextMap.get(userId);
        AdminContactRequestDetailDTO.UserInfo dto = new AdminContactRequestDetailDTO.UserInfo();
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

    private Set<Long> collectUserIds(Collection<ShareCardContactRequest> requests) {
        Set<Long> userIds = new HashSet<>();
        for (ShareCardContactRequest request : requests) {
            UserShareCard card = resolveRequestCard(request);
            if (card != null && card.getUserId() != null) {
                userIds.add(card.getUserId());
            }
            if (request.getViewerUserId() != null) {
                userIds.add(request.getViewerUserId());
            }
        }
        return userIds;
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

    private ShareCardContactRequest latestRequest(Long viewerUserId, UserShareCard card) {
        if (viewerUserId == null || card == null) {
            return null;
        }
        LambdaQueryWrapper<ShareCardContactRequest> wrapper = new LambdaQueryWrapper<ShareCardContactRequest>()
                .eq(ShareCardContactRequest::getViewerUserId, viewerUserId)
                .eq(ShareCardContactRequest::getShareCardId, card.getShareCardId())
                .orderByDesc(ShareCardContactRequest::getRequestedAt)
                .orderByDesc(ShareCardContactRequest::getRequestId)
                .last("limit 1");
        return getOne(wrapper, false);
    }

    private UserShareCard resolveStatusCard(Long shareCardId) {
        return requireActiveShareCard(shareCardId);
    }

    private UserShareCard resolveApplyCard(ContactRequestApplyDTO dto) {
        return requireActiveShareCard(dto.getShareCardId());
    }

    private UserShareCard resolveRequestCard(ShareCardContactRequest request) {
        if (request == null) {
            return null;
        }
        return userShareCardService.findActiveCardById(request.getShareCardId());
    }

    private UserShareCard requireActiveShareCard(Long shareCardId) {
        UserShareCard card = userShareCardService.findActiveCardById(shareCardId);
        if (card == null) {
            throw new BizException("分享卡片不存在");
        }
        return card;
    }

    private String buildCardKey(UserShareCard card) {
        return "card|" + card.getShareCardId();
    }

    private ContactRequestStatusRespDTO buildStatusResp(ShareCardContactRequest request, Long viewerUserId) {
        UserShareCard card = resolveRequestCard(request);
        if (card == null) {
            throw new BizException("分享卡片不存在");
        }
        Long resolvedHolderUserId = card.getUserId();
        ActorSceneTemplateRespDTO template = requireTemplate(card);
        String resolvedTemplateSceneCode = template.getTemplateSceneCode();
        ContactRequestStatusRespDTO dto = new ContactRequestStatusRespDTO();
        dto.setRequestId(request.getRequestId());
        dto.setHolderUserId(resolvedHolderUserId);
        dto.setViewerUserId(request.getViewerUserId());
        dto.setShareCardId(card.getShareCardId());
        dto.setTemplateSceneCode(resolvedTemplateSceneCode);
        dto.setTemplateName(template.getName());
        dto.setStatus(request.getStatus());
        dto.setHasContactPhone(hasContactPhone(resolvedHolderUserId));
        boolean canViewPhone = STATUS_APPROVED.equals(request.getStatus()) && viewerUserId != null && viewerUserId.equals(request.getViewerUserId());
        dto.setCanViewPhone(canViewPhone);
        dto.setContactPhone(canViewPhone ? resolveContactPhone(resolvedHolderUserId) : null);
        dto.setRequestedAt(request.getRequestedAt());
        dto.setDecidedAt(request.getDecidedAt());
        return dto;
    }

    private ContactRequestItemDTO buildItemDto(ShareCardContactRequest request, UserShareCard card, boolean includeContactPhone) {
        if (card == null) {
            throw new BizException("分享卡片不存在");
        }
        Long resolvedHolderUserId = card.getUserId();
        ActorSceneTemplateRespDTO template = requireTemplate(card);
        String normalizedTemplateSceneCode = template.getTemplateSceneCode();
        ContactRequestItemDTO dto = new ContactRequestItemDTO();
        dto.setRequestId(request.getRequestId());
        dto.setHolderUserId(resolvedHolderUserId);
        dto.setViewerUserId(request.getViewerUserId());
        dto.setShareCardId(card.getShareCardId());
        dto.setTemplateSceneCode(normalizedTemplateSceneCode);
        dto.setTemplateName(template.getName());
        dto.setStatus(request.getStatus());
        dto.setRequestedAt(request.getRequestedAt());
        dto.setDecidedAt(request.getDecidedAt());

        User owner = userMapper.selectById(resolvedHolderUserId);
        ActorProfile ownerProfile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, resolvedHolderUserId)
                .last("limit 1"));
        dto.setOwnerName(firstNonBlank(ownerProfile == null ? null : ownerProfile.getNickName(), owner == null ? null : owner.getUserName(), null));
        dto.setOwnerAvatar(firstNonBlank(ownerProfile == null ? null : ownerProfile.getAvatarUrl(), owner == null ? null : owner.getAvatarUrl(), "/static/logo.png"));

        User viewer = userMapper.selectById(request.getViewerUserId());
        ActorProfile viewerProfile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, request.getViewerUserId())
                .last("limit 1"));
        dto.setViewerName(firstNonBlank(viewerProfile == null ? null : viewerProfile.getNickName(), viewer == null ? null : viewer.getUserName(), "访客"));
        dto.setViewerAvatar(firstNonBlank(viewerProfile == null ? null : viewerProfile.getAvatarUrl(), viewer == null ? null : viewer.getAvatarUrl(), "/static/logo.png"));
        dto.setContactPhone(includeContactPhone && STATUS_APPROVED.equals(request.getStatus()) ? resolveContactPhone(resolvedHolderUserId) : null);
        return dto;
    }

    private boolean hasContactPhone(Long holderUserId) {
        return StringUtils.hasText(resolveContactPhone(holderUserId));
    }

    private String resolveContactPhone(Long holderUserId) {
        ActorProfile profile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, holderUserId)
                .last("limit 1"));
        if (profile != null && StringUtils.hasText(profile.getPhone())) {
            return profile.getPhone().trim();
        }
        User user = userMapper.selectById(holderUserId);
        return user == null || !StringUtils.hasText(user.getPhone()) ? null : user.getPhone().trim();
    }

    private List<Long> findActiveShareCardIdsByTemplateSceneCode(String templateSceneCode) {
        List<Long> templateIds = findTemplateIdsByTemplateSceneCode(requireTemplateSceneCode(templateSceneCode));
        if (templateIds.isEmpty()) {
            return List.of();
        }
        return userShareCardService.list(new LambdaQueryWrapper<UserShareCard>()
                        .in(UserShareCard::getTemplateId, templateIds)
                        .eq(UserShareCard::getShareStatus, "active"))
                .stream()
                .map(UserShareCard::getShareCardId)
                .filter(id -> id != null && id > 0)
                .toList();
    }

    private List<Long> findActiveShareCardIdsByOwner(Long holderUserId) {
        if (holderUserId == null || holderUserId <= 0) {
            return List.of();
        }
        return userShareCardService.list(new LambdaQueryWrapper<UserShareCard>()
                        .eq(UserShareCard::getUserId, holderUserId)
                        .eq(UserShareCard::getShareStatus, "active"))
                .stream()
                .map(UserShareCard::getShareCardId)
                .filter(id -> id != null && id > 0)
                .toList();
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

    private ActorSceneTemplateRespDTO requireTemplate(UserShareCard card) {
        if (card == null || card.getTemplateId() == null || card.getTemplateId() <= 0) {
            throw new BizException("分享卡片模板未绑定");
        }
        return cardSceneTemplateService.actorSceneTemplates().stream()
                .filter(item -> card.getTemplateId().equals(item.getTemplateId()))
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

    private String requireTemplateSceneCode(String templateSceneCode) {
        return TemplateSceneCodeValidator.requireAllowed(templateSceneCode);
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
}



