package com.kaipai.service.actor.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.actor.ActorProfileRepresentativeWorkMapper;
import com.kaipai.mapper.card.ShareCardContactRequestMapper;
import com.kaipai.mapper.card.UserShareCardMapper;
import com.kaipai.model.actor.dto.ActorCareerHubSummaryRespDTO;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.model.actor.entity.ActorMediaAsset;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.actor.entity.ActorProfileRepresentativeWork;
import com.kaipai.model.card.entity.ShareCardContactRequest;
import com.kaipai.model.card.entity.UserShareCard;
import com.kaipai.service.actor.ActorCareerHubService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ActorCareerHubServiceImpl implements ActorCareerHubService {
    private final ActorProfileMapper profileMapper;
    private final ActorExperienceMapper workMapper;
    private final ActorProfileRepresentativeWorkMapper representativeMapper;
    private final ActorMediaAssetMapper assetMapper;
    private final UserShareCardMapper cardMapper;
    private final ShareCardContactRequestMapper contactMapper;
    private final ObjectMapper objectMapper;

    @Override
    public ActorCareerHubSummaryRespDTO summary(Long userId) {
        ActorProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, userId)
                .last("limit 1"));
        ActorCareerHubSummaryRespDTO response = new ActorCareerHubSummaryRespDTO();
        response.getProfile().setCoreReady(coreReady(profile));
        response.getProfile().setCareerFieldCount(careerFieldCount(profile));
        response.getProfile().setCurrentCity(profile == null ? null : profile.getLocationCity());
        response.getWorks().setTotal(workMapper.selectCount(new LambdaQueryWrapper<ActorExperience>()
                .eq(ActorExperience::getUserId, userId)));
        response.getWorks().setRepresentativeCount(profile == null ? 0L
                : representativeMapper.selectCount(new LambdaQueryWrapper<ActorProfileRepresentativeWork>()
                        .eq(ActorProfileRepresentativeWork::getActorProfileId, profile.getActorProfileId())));
        response.getAssets().setPhotoCount(assetCount(userId, "photo"));
        response.getAssets().setVideoCount(assetCount(userId, "video"));
        response.getAssets().setHasCurrentResume(profile != null && profile.getCurrentResumeAssetId() != null);
        response.setPendingContactRequests(pendingContactRequests(userId));
        return response;
    }

    private long assetCount(Long userId, String mediaType) {
        return assetMapper.selectCount(new LambdaQueryWrapper<ActorMediaAsset>()
                .eq(ActorMediaAsset::getUserId, userId)
                .eq(ActorMediaAsset::getMediaType, mediaType));
    }

    private long pendingContactRequests(Long userId) {
        List<Long> cardIds = cardMapper.selectList(new LambdaQueryWrapper<UserShareCard>()
                        .eq(UserShareCard::getUserId, userId)
                        .eq(UserShareCard::getShareStatus, "active"))
                .stream()
                .map(UserShareCard::getShareCardId)
                .filter(id -> id != null && id > 0)
                .toList();
        if (cardIds.isEmpty()) {
            return 0L;
        }
        return contactMapper.selectCount(new LambdaQueryWrapper<ShareCardContactRequest>()
                .in(ShareCardContactRequest::getShareCardId, cardIds)
                .eq(ShareCardContactRequest::getStatus, "pending"));
    }

    private boolean coreReady(ActorProfile profile) {
        return profile != null
                && profile.getAvatarAssetId() != null
                && StringUtils.hasText(profile.getNickName())
                && (Integer.valueOf(1).equals(profile.getGender()) || Integer.valueOf(2).equals(profile.getGender()))
                && profile.getAge() != null
                && profile.getHeight() != null
                && StringUtils.hasText(profile.getLocationCity());
    }

    private int careerFieldCount(ActorProfile profile) {
        if (profile == null) {
            return 0;
        }
        int count = 0;
        count += profile.getWeight() == null ? 0 : 1;
        count += StringUtils.hasText(profile.getOriginPlace()) ? 1 : 0;
        count += StringUtils.hasText(profile.getSchoolName()) ? 1 : 0;
        count += StringUtils.hasText(profile.getMajorName()) ? 1 : 0;
        count += hasTags(profile.getLanguageTagsJson()) ? 1 : 0;
        count += hasTags(profile.getSpecialtyTagsJson()) ? 1 : 0;
        count += hasTags(profile.getRoleTypeTagsJson()) ? 1 : 0;
        count += hasTags(profile.getProfessionalAbilityTagsJson()) ? 1 : 0;
        return count;
    }

    private boolean hasTags(String json) {
        if (!StringUtils.hasText(json)) {
            return false;
        }
        try {
            List<String> tags = objectMapper.readValue(json, new TypeReference<List<String>>() { });
            return tags.stream().anyMatch(StringUtils::hasText);
        } catch (Exception ignored) {
            return false;
        }
    }
}
