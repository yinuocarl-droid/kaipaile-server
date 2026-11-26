package com.kaipai.service.card.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.card.ShareCardFavoriteMapper;
import com.kaipai.mapper.card.UserShareCardMapper;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.model.card.dto.ShareCardFavoriteItemDTO;
import com.kaipai.model.card.dto.ShareCardFavoriteStateDTO;
import com.kaipai.model.card.entity.ShareCardFavorite;
import com.kaipai.model.card.entity.UserShareCard;
import com.kaipai.service.card.CardSceneTemplateService;
import com.kaipai.service.card.ShareCardFavoriteService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ShareCardFavoriteServiceImpl implements ShareCardFavoriteService {
    private final ShareCardFavoriteMapper favoriteMapper;
    private final UserShareCardMapper cardMapper;
    private final ActorProfileMapper profileMapper;
    private final CardSceneTemplateService templateService;

    private ShareCardFavorite find(Long userId, Long cardId) {
        return favoriteMapper.selectOne(new LambdaQueryWrapper<ShareCardFavorite>()
                .eq(ShareCardFavorite::getUserId, userId)
                .eq(ShareCardFavorite::getShareCardId, cardId)
                .last("limit 1"));
    }

    public PageResult<ShareCardFavoriteItemDTO> list(Long userId, int page, int size) {
        List<ShareCardFavorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<ShareCardFavorite>()
                .eq(ShareCardFavorite::getUserId, userId)
                .orderByDesc(ShareCardFavorite::getFavoriteId));
        if (favorites.isEmpty()) return PageResult.empty();
        List<Long> ids = favorites.stream().map(ShareCardFavorite::getShareCardId).toList();
        List<UserShareCard> cards = cardMapper.selectList(new LambdaQueryWrapper<UserShareCard>()
                .in(UserShareCard::getShareCardId, ids)
                .eq(UserShareCard::getShareStatus, "active"));
        Map<Long, UserShareCard> cardsById = new HashMap<>();
        for (UserShareCard card : cards) cardsById.put(card.getShareCardId(), card);

        List<Long> ownerIds = cards.stream().map(UserShareCard::getUserId).distinct().toList();
        Map<Long, ActorProfile> profilesByUser = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            for (ActorProfile profile : profileMapper.selectList(new LambdaQueryWrapper<ActorProfile>()
                    .in(ActorProfile::getUserId, ownerIds)
                    .orderByDesc(ActorProfile::getActorProfileId))) {
                profilesByUser.putIfAbsent(profile.getUserId(), profile);
            }
        }
        Map<Long, ActorSceneTemplateRespDTO> templatesById = new HashMap<>();
        for (ActorSceneTemplateRespDTO template : templateService.actorSceneTemplates()) {
            templatesById.put(template.getTemplateId(), template);
        }

        List<ShareCardFavoriteItemDTO> active = new ArrayList<>();
        for (ShareCardFavorite favorite : favorites) {
            UserShareCard card = cardsById.get(favorite.getShareCardId());
            if (card != null) active.add(toItem(card, profilesByUser.get(card.getUserId()), templatesById.get(card.getTemplateId())));
        }
        int pageNo = Math.max(1, page);
        int pageSize = size <= 0 ? 10 : Math.min(size, 50);
        int from = Math.min(active.size(), (pageNo - 1) * pageSize);
        int to = Math.min(active.size(), from + pageSize);
        return new PageResult<>(active.size(), active.subList(from, to));
    }

    private ShareCardFavoriteItemDTO toItem(UserShareCard card, ActorProfile profile, ActorSceneTemplateRespDTO template) {
        ShareCardFavoriteItemDTO item = new ShareCardFavoriteItemDTO();
        item.setShareCardId(card.getShareCardId());
        item.setOwnerUserId(card.getUserId());
        item.setProfileUserId(card.getUserId());
        item.setActorName(profile == null || !StringUtils.hasText(profile.getNickName()) ? "演员" : profile.getNickName().trim());
        item.setActorAvatar(profile == null || !StringUtils.hasText(profile.getAvatarUrl()) ? "/static/logo.png" : profile.getAvatarUrl().trim());
        item.setTemplateSceneCode(template == null ? "classic" : template.getTemplateSceneCode());
        item.setTemplateName(template == null ? "演员名片" : template.getName());
        item.setIntro(profile == null || !StringUtils.hasText(profile.getIntro()) ? "查看演员公开分享资料" : profile.getIntro().trim());
        item.setContactLabel("联系需先申请授权");
        return item;
    }

    public ShareCardFavoriteStateDTO state(Long userId, Long cardId) {
        UserShareCard card = cardMapper.selectOne(new LambdaQueryWrapper<UserShareCard>()
                .eq(UserShareCard::getShareCardId, cardId)
                .eq(UserShareCard::getShareStatus, "active")
                .last("limit 1"));
        return new ShareCardFavoriteStateDTO(card != null && find(userId, cardId) != null);
    }

    @Transactional
    public ShareCardFavoriteStateDTO add(Long userId, Long cardId) {
        UserShareCard card = cardMapper.selectOne(new LambdaQueryWrapper<UserShareCard>()
                .eq(UserShareCard::getShareCardId, cardId)
                .eq(UserShareCard::getShareStatus, "active")
                .last("limit 1"));
        if (card == null) throw new BizException("分享卡不存在或已失效");
        if (userId.equals(card.getUserId())) throw new BizException("不能收藏自己的分享卡");
        if (find(userId, cardId) == null) {
            ShareCardFavorite favorite = new ShareCardFavorite();
            favorite.setUserId(userId);
            favorite.setShareCardId(cardId);
            try { favoriteMapper.insert(favorite); } catch (DuplicateKeyException ignored) { /* concurrent idempotent add */ }
        }
        return new ShareCardFavoriteStateDTO(true);
    }

    @Transactional
    public ShareCardFavoriteStateDTO remove(Long userId, Long cardId) {
        ShareCardFavorite favorite = find(userId, cardId);
        if (favorite != null) favoriteMapper.deleteById(favorite.getFavoriteId());
        return new ShareCardFavoriteStateDTO(false);
    }
}
