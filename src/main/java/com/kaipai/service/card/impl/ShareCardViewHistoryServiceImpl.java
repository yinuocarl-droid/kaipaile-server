package com.kaipai.service.card.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.card.dto.ShareCardHistoryItemDTO;
import com.kaipai.model.card.dto.ShareCardHistoryRecordDTO;
import com.kaipai.model.card.entity.ShareCardContactRequest;
import com.kaipai.model.card.entity.ShareCardViewHistory;
import com.kaipai.model.card.entity.UserShareCard;
import com.kaipai.model.user.entity.User;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.card.ShareCardContactRequestMapper;
import com.kaipai.mapper.card.ShareCardViewHistoryMapper;
import com.kaipai.service.card.CardSceneTemplateService;
import com.kaipai.service.card.ShareCardViewHistoryService;
import com.kaipai.service.card.UserShareCardService;
import com.kaipai.mapper.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShareCardViewHistoryServiceImpl extends ServiceImpl<ShareCardViewHistoryMapper, ShareCardViewHistory> implements ShareCardViewHistoryService {

    private final ActorProfileMapper actorProfileMapper;
    private final UserMapper userMapper;
    private final CardSceneTemplateService cardSceneTemplateService;
    private final ShareCardContactRequestMapper contactRequestMapper;
    private final UserShareCardService userShareCardService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void record(Long viewerUserId, ShareCardHistoryRecordDTO dto) {
        if (viewerUserId == null || dto == null) {
            return;
        }
        UserShareCard shareCard = resolveHistoryCard(dto.getShareCardId());
        Long holderUserId = shareCard == null ? null : shareCard.getUserId();
        if (holderUserId == null) {
            return;
        }
        if (viewerUserId.equals(holderUserId)) {
            return;
        }
        ShareCardViewHistory history = new ShareCardViewHistory();
        history.setViewerUserId(viewerUserId);
        history.setShareCardId(shareCard.getShareCardId());
        history.setViewedAt(LocalDateTime.now());
        save(history);
    }

    @Override
    public List<ShareCardHistoryItemDTO> myHistory(Long viewerUserId) {
        List<ShareCardViewHistory> histories = list(new LambdaQueryWrapper<ShareCardViewHistory>()
                .eq(ShareCardViewHistory::getViewerUserId, viewerUserId)
                .isNotNull(ShareCardViewHistory::getShareCardId)
                .orderByDesc(ShareCardViewHistory::getViewedAt)
                .orderByDesc(ShareCardViewHistory::getHistoryId));
        Map<String, ShareCardHistoryItemDTO> latestByCard = new LinkedHashMap<>();
        for (ShareCardViewHistory history : histories) {
            UserShareCard shareCard = resolveHistoryCard(history);
            if (shareCard == null || shareCard.getShareCardId() == null) {
                continue;
            }
            latestByCard.putIfAbsent(buildHistoryKey(history, shareCard), buildItem(viewerUserId, history, shareCard));
        }
        return new ArrayList<>(latestByCard.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clear(Long viewerUserId) {
        remove(new LambdaQueryWrapper<ShareCardViewHistory>()
                .eq(ShareCardViewHistory::getViewerUserId, viewerUserId));
    }

    private ShareCardHistoryItemDTO buildItem(Long viewerUserId, ShareCardViewHistory history, UserShareCard shareCard) {
        Long resolvedHolderUserId = shareCard.getUserId();
        ActorSceneTemplateRespDTO template = requireTemplate(shareCard);
        String normalizedTemplateSceneCode = template.getTemplateSceneCode();
        ActorProfile profile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, resolvedHolderUserId)
                .last("limit 1"));
        User user = userMapper.selectById(resolvedHolderUserId);
        ShareCardHistoryItemDTO dto = new ShareCardHistoryItemDTO();
        dto.setProfileUserId(resolvedHolderUserId);
        dto.setShareCardId(shareCard.getShareCardId());
        dto.setTemplateSceneCode(normalizedTemplateSceneCode);
        dto.setActorName(firstNonBlank(profile == null ? null : profile.getNickName(), user == null ? null : user.getUserName(), null));
        dto.setActorAvatar(firstNonBlank(profile == null ? null : profile.getAvatarUrl(), user == null ? null : user.getAvatarUrl(), "/static/logo.png"));
        dto.setTemplateName(template.getName());
        dto.setIntro(firstNonBlank(profile == null ? null : profile.getIntro(), "查看过的分享卡片会保留在历史中，方便再次打开。"));
        dto.setContactLabel(resolveContactLabel(viewerUserId, history, shareCard));
        dto.setViewedAt(history.getViewedAt());
        return dto;
    }

    private String resolveContactLabel(Long viewerUserId, ShareCardViewHistory history, UserShareCard shareCard) {
        Long resolvedHolderUserId = shareCard.getUserId();
        LambdaQueryWrapper<ShareCardContactRequest> wrapper = new LambdaQueryWrapper<ShareCardContactRequest>()
                .eq(ShareCardContactRequest::getViewerUserId, viewerUserId)
                .eq(ShareCardContactRequest::getShareCardId, shareCard.getShareCardId())
                .orderByDesc(ShareCardContactRequest::getRequestedAt)
                .orderByDesc(ShareCardContactRequest::getRequestId)
                .last("limit 1");
        ShareCardContactRequest latestRequest = contactRequestMapper.selectOne(wrapper);
        if (latestRequest != null) {
            return switch (latestRequest.getStatus()) {
                case "approved" -> "已获授权，可查看电话";
                case "pending" -> "联系申请待处理";
                case "rejected" -> "申请被拒绝";
                default -> "联系需先申请授权";
            };
        }

        ActorProfile profile = actorProfileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, resolvedHolderUserId)
                .last("limit 1"));
        User user = userMapper.selectById(resolvedHolderUserId);
        String phone = firstNonBlank(profile == null ? null : profile.getPhone(), user == null ? null : user.getPhone(), null);
        return StringUtils.hasText(phone) ? "联系需先申请授权" : "暂未开放联系电话";
    }

    private UserShareCard resolveHistoryCard(Long shareCardId) {
        return userShareCardService.findActiveCardById(shareCardId);
    }

    private String buildHistoryKey(ShareCardViewHistory history, UserShareCard shareCard) {
        return "card|" + shareCard.getShareCardId();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private UserShareCard resolveHistoryCard(ShareCardViewHistory history) {
        if (history == null) {
            return null;
        }
        return resolveHistoryCard(history.getShareCardId());
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
}



