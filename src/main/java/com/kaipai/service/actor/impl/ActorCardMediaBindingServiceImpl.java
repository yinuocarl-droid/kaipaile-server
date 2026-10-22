package com.kaipai.service.actor.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardMediaItemMapper;
import com.kaipai.mapper.actor.ActorCardMediaSetMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingItemReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingItemRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardMediaItem;
import com.kaipai.model.actor.card.entity.ActorCardMediaSet;
import com.kaipai.model.actor.card.entity.ActorCardWork;
import com.kaipai.model.actor.entity.ActorMediaAsset;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.service.actor.ActorCardMediaBindingService;
import com.kaipai.service.actor.ActorMediaAssetService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ActorCardMediaBindingServiceImpl implements ActorCardMediaBindingService {

    public static final String SOURCE_IMAGE = "source_image";
    public static final String WORK_STILL = "work_still";
    public static final String LIFE_PHOTO = "life_photo";
    public static final String VIDEO_RESUME = "video_resume";

    private static final Set<String> USAGES = Set.of(SOURCE_IMAGE, WORK_STILL, LIFE_PHOTO, VIDEO_RESUME);

    private final ActorCardMapper cardMapper;
    private final ActorCardWorkMapper workMapper;
    private final ActorCardMediaSetMapper setMapper;
    private final ActorCardMediaItemMapper itemMapper;
    private final ActorMediaAssetMapper assetMapper;
    private final ActorMediaAssetService assetService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public ActorCardMediaBindingRespDTO list(Long userId, Long cardId, String usage, Long workId) {
        ActorCard card = requireOwnedCard(userId, cardId);
        String normalizedUsage = normalizeUsage(usage);
        validateScope(cardId, normalizedUsage, workId);

        ActorCardMediaSet set = setMapper.selectActive(cardId, normalizedUsage, workId);
        if (set == null) {
            return legacyResponse(card, cardId, normalizedUsage, workId);
        }
        return toResponse(set, itemMapper.selectActiveBySetId(set.getSetId()), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActorCardMediaBindingRespDTO replace(
            Long userId,
            Long cardId,
            ActorCardMediaBindingReqDTO request) {
        if (request == null) {
            throw new BizException("素材绑定请求不能为空");
        }
        ActorCard card = cardMapper.selectOwnedForUpdate(userId, cardId);
        if (card == null) {
            throw new BizException("演员卡不存在");
        }
        if (!"draft".equals(card.getStatus())) {
            throw new BizException("只有草稿状态的演员卡可以编辑素材");
        }

        String usage = normalizeUsage(request.getUsage());
        validateScope(cardId, usage, request.getWorkId());
        List<ActorCardMediaBindingItemReqDTO> requested = request.getItems() == null
                ? Collections.emptyList()
                : request.getItems();
        validateItems(usage, requested);
        Map<Long, ActorMediaAsset> assets = lockAndValidateAssets(userId, usage, requested);

        ActorCardMediaSet set = setMapper.selectActiveForUpdate(cardId, usage, request.getWorkId());
        boolean createsSourceOverride = SOURCE_IMAGE.equals(usage) && set == null;
        List<Long> existingAssetIds = set == null
                ? Collections.emptyList()
                : itemMapper.selectActiveBySetId(set.getSetId()).stream()
                        .map(ActorCardMediaItem::getAssetId)
                        .filter(Objects::nonNull)
                        .toList();
        List<Long> requestedAssetIds = requested.stream()
                .map(ActorCardMediaBindingItemReqDTO::getAssetId)
                .toList();
        if (set == null) {
            set = new ActorCardMediaSet();
            set.setCardId(cardId);
            set.setCardWorkId(request.getWorkId());
            set.setUsageCode(usage);
            if (setMapper.insert(set) != 1) {
                throw new IllegalStateException("素材绑定集合创建失败");
            }
        }

        itemMapper.deleteActiveBySetId(set.getSetId());
        for (ActorCardMediaBindingItemReqDTO item : requested) {
            ActorCardMediaItem entity = new ActorCardMediaItem();
            entity.setSetId(set.getSetId());
            entity.setAssetId(item.getAssetId());
            entity.setLegacyUrl(null);
            entity.setSortOrder(item.getSortOrder());
            if (itemMapper.insert(entity) != 1) {
                throw new IllegalStateException("素材绑定条目保存失败");
            }
        }
        if (SOURCE_IMAGE.equals(usage)
                && (createsSourceOverride || !existingAssetIds.equals(requestedAssetIds))) {
            card.setExpandedImageUrl(null);
            card.setExpandedSourceFingerprint(null);
            if (cardMapper.update(null, new UpdateWrapper<ActorCard>()
                    .eq("id", cardId)
                    .eq("user_id", userId)
                    .set("expanded_image_url", null)
                    .set("expanded_source_fingerprint", null)) != 1) {
                throw new IllegalStateException("主视觉扩图状态清理失败");
            }
        }
        return toResponse(set, itemMapper.selectActiveBySetId(set.getSetId()), userId, assets);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearWorkBindings(Long userId, Long cardId, List<Long> workIds) {
        if (workIds == null || workIds.isEmpty()) {
            return;
        }
        if (cardMapper.selectOwnedForUpdate(userId, cardId) == null) {
            throw new BizException("演员卡不存在");
        }
        List<ActorCardMediaSet> sets = setMapper.selectActiveWorkSetsForUpdate(cardId, workIds);
        for (ActorCardMediaSet set : sets) {
            itemMapper.deleteActiveBySetId(set.getSetId());
            setMapper.deleteActive(set.getSetId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyBindings(
            Long userId,
            Long sourceCardId,
            Long targetCardId,
            Map<Long, Long> workIdMapping) {
        ActorCard source = requireOwnedCard(userId, sourceCardId);
        ActorCard target = cardMapper.selectOwnedForUpdate(userId, targetCardId);
        if (source == null || target == null) {
            throw new BizException("演员卡不存在");
        }
        if (!"draft".equals(target.getStatus())) {
            throw new BizException("复制目标必须是草稿");
        }
        Map<Long, Long> safeWorkMapping = workIdMapping == null
                ? Collections.emptyMap() : workIdMapping;
        for (ActorCardMediaSet sourceSet : setMapper.selectActiveByCardForUpdate(sourceCardId)) {
            Long targetWorkId = null;
            if (WORK_STILL.equals(sourceSet.getUsageCode())) {
                targetWorkId = safeWorkMapping.get(sourceSet.getCardWorkId());
                if (targetWorkId == null) {
                    throw new IllegalStateException("复制演员卡时缺少作品剧照映射");
                }
            }
            ActorCardMediaSet targetSet = new ActorCardMediaSet();
            targetSet.setCardId(targetCardId);
            targetSet.setCardWorkId(targetWorkId);
            targetSet.setUsageCode(sourceSet.getUsageCode());
            if (setMapper.insert(targetSet) != 1) {
                throw new IllegalStateException("复制素材绑定集合失败");
            }
            for (ActorCardMediaItem sourceItem : itemMapper.selectActiveBySetId(sourceSet.getSetId())) {
                ActorCardMediaItem targetItem = new ActorCardMediaItem();
                targetItem.setSetId(targetSet.getSetId());
                targetItem.setAssetId(sourceItem.getAssetId());
                targetItem.setLegacyUrl(sourceItem.getLegacyUrl());
                targetItem.setSortOrder(sourceItem.getSortOrder());
                if (itemMapper.insert(targetItem) != 1) {
                    throw new IllegalStateException("复制素材绑定条目失败");
                }
            }
        }
    }

    private ActorCard requireOwnedCard(Long userId, Long cardId) {
        ActorCard card = cardMapper.selectById(cardId);
        if (card == null || !userId.equals(card.getUserId())) {
            throw new BizException("演员卡不存在");
        }
        return card;
    }

    private String normalizeUsage(String usage) {
        String normalized = usage == null ? "" : usage.trim().toLowerCase();
        if (!USAGES.contains(normalized)) {
            throw new BizException("不支持的演员卡素材用途");
        }
        return normalized;
    }

    private void validateScope(Long cardId, String usage, Long workId) {
        if (WORK_STILL.equals(usage) != (workId != null)) {
            throw new BizException(WORK_STILL.equals(usage)
                    ? "作品剧照必须指定作品"
                    : "该素材用途不支持作品范围");
        }
        if (workId != null && workMapper.selectActiveByIdAndCard(cardId, workId) == null) {
            throw new BizException("作品不存在");
        }
    }

    private void validateItems(String usage, List<ActorCardMediaBindingItemReqDTO> items) {
        int limit = switch (usage) {
            case SOURCE_IMAGE, VIDEO_RESUME -> 1;
            case WORK_STILL -> 3;
            case LIFE_PHOTO -> 12;
            default -> throw new BizException("不支持的演员卡素材用途");
        };
        if (items.size() > limit) {
            throw new BizException("素材数量超过该用途上限");
        }
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            ActorCardMediaBindingItemReqDTO item = items.get(i);
            if (item == null || item.getAssetId() == null || item.getSortOrder() == null) {
                throw new BizException("素材绑定条目不完整");
            }
            if (item.getSortOrder() != i) {
                throw new BizException("素材排序必须从 0 连续编号");
            }
            if (!ids.add(item.getAssetId())) {
                throw new BizException("同一素材不能重复绑定");
            }
        }
    }

    private Map<Long, ActorMediaAsset> lockAndValidateAssets(
            Long userId,
            String usage,
            List<ActorCardMediaBindingItemReqDTO> items) {
        if (items.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = items.stream().map(ActorCardMediaBindingItemReqDTO::getAssetId).toList();
        List<ActorMediaAsset> records = assetMapper.selectOwnedActiveByIdsForUpdate(userId, ids);
        if (records.size() != ids.size()) {
            throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_FOUND.toException();
        }
        String expectedMediaType = VIDEO_RESUME.equals(usage) ? "video" : "photo";
        for (ActorMediaAsset asset : records) {
            if (!expectedMediaType.equals(asset.getMediaType())
                    || !"ready".equals(asset.getProcessStatus())) {
                throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_READY.toException();
            }
        }
        return records.stream().collect(Collectors.toMap(ActorMediaAsset::getAssetId, asset -> asset));
    }

    private ActorCardMediaBindingRespDTO legacyResponse(
            ActorCard card,
            Long cardId,
            String usage,
            Long workId) {
        List<String> urls = switch (usage) {
            case SOURCE_IMAGE -> StringUtils.hasText(card.getSourceImageUrl())
                    ? List.of(card.getSourceImageUrl()) : Collections.emptyList();
            case LIFE_PHOTO -> readUrlList(card.getPhotosJson());
            case VIDEO_RESUME -> StringUtils.hasText(card.getVideoUrl())
                    ? List.of(card.getVideoUrl()) : Collections.emptyList();
            case WORK_STILL -> {
                ActorCardWork work = workMapper.selectActiveByIdAndCard(cardId, workId);
                yield work == null ? Collections.emptyList() : readUrlList(work.getStillsJson());
            }
            default -> Collections.emptyList();
        };
        ActorCardMediaBindingRespDTO response = emptyResponse(cardId, workId, usage);
        for (int i = 0; i < urls.size(); i++) {
            ActorCardMediaBindingItemRespDTO item = new ActorCardMediaBindingItemRespDTO();
            item.setLegacyUrl(urls.get(i));
            item.setPreviewUrl(urls.get(i));
            item.setSortOrder(i);
            response.getItems().add(item);
        }
        return response;
    }

    private List<String> readUrlList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return values == null ? Collections.emptyList() : values.stream()
                    .filter(StringUtils::hasText)
                    .toList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private ActorCardMediaBindingRespDTO toResponse(
            ActorCardMediaSet set,
            List<ActorCardMediaItem> items,
            Long userId) {
        List<Long> assetIds = items.stream().map(ActorCardMediaItem::getAssetId)
                .filter(java.util.Objects::nonNull).toList();
        Map<Long, ActorMediaAsset> assets = assetIds.isEmpty()
                ? Collections.emptyMap()
                : assetMapper.selectList(new LambdaQueryWrapper<ActorMediaAsset>()
                        .eq(ActorMediaAsset::getUserId, userId)
                        .eq(ActorMediaAsset::getDeleted, 0)
                        .in(ActorMediaAsset::getAssetId, assetIds))
                .stream().collect(Collectors.toMap(ActorMediaAsset::getAssetId, asset -> asset));
        return toResponse(set, items, userId, assets);
    }

    private ActorCardMediaBindingRespDTO toResponse(
            ActorCardMediaSet set,
            List<ActorCardMediaItem> items,
            Long userId,
            Map<Long, ActorMediaAsset> assets) {
        ActorCardMediaBindingRespDTO response = emptyResponse(set.getCardId(), set.getCardWorkId(), set.getUsageCode());
        response.setSetId(set.getSetId());
        for (ActorCardMediaItem entity : items) {
            ActorCardMediaBindingItemRespDTO item = new ActorCardMediaBindingItemRespDTO();
            item.setItemId(entity.getItemId());
            item.setAssetId(entity.getAssetId());
            item.setLegacyUrl(entity.getLegacyUrl());
            item.setSortOrder(entity.getSortOrder());
            ActorMediaAsset asset = entity.getAssetId() == null ? null : assets.get(entity.getAssetId());
            if (asset != null) {
                item.setOriginalName(asset.getOriginalName());
                item.setMediaType(asset.getMediaType());
                item.setCategoryCode(asset.getCategoryCode());
                item.setProcessStatus(asset.getProcessStatus());
                var access = assetService.issueOwnerAccessUrl(userId, asset.getAssetId());
                if (access != null) {
                    item.setPreviewUrl(access.getAccessUrl());
                    item.setExpiresAt(access.getExpiresAt());
                }
            }
            response.getItems().add(item);
        }
        return response;
    }

    private ActorCardMediaBindingRespDTO emptyResponse(Long cardId, Long workId, String usage) {
        ActorCardMediaBindingRespDTO response = new ActorCardMediaBindingRespDTO();
        response.setCardId(cardId);
        response.setWorkId(workId);
        response.setUsage(usage);
        response.setItems(new ArrayList<>());
        return response;
    }
}
