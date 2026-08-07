package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.actor.ActorProfileRepresentativeWorkMapper;
import com.kaipai.mapper.card.ShareCardContactRequestMapper;
import com.kaipai.mapper.card.UserShareCardMapper;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.card.entity.UserShareCard;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActorCareerHubServiceImplTest {
    @Test
    void returnsRealProfileWorkAssetAndPendingContactSummary() {
        ActorProfileMapper profileMapper = mock(ActorProfileMapper.class);
        ActorExperienceMapper workMapper = mock(ActorExperienceMapper.class);
        ActorProfileRepresentativeWorkMapper representativeMapper = mock(ActorProfileRepresentativeWorkMapper.class);
        ActorMediaAssetMapper assetMapper = mock(ActorMediaAssetMapper.class);
        UserShareCardMapper cardMapper = mock(UserShareCardMapper.class);
        ShareCardContactRequestMapper contactMapper = mock(ShareCardContactRequestMapper.class);
        ActorProfile profile = completeProfile();
        when(profileMapper.selectOne(any())).thenReturn(profile);
        when(workMapper.selectCount(any())).thenReturn(29L);
        when(representativeMapper.selectCount(any())).thenReturn(6L);
        when(assetMapper.selectCount(any())).thenReturn(14L, 3L);
        UserShareCard card = new UserShareCard();
        card.setShareCardId(91L);
        when(cardMapper.selectList(any())).thenReturn(List.of(card));
        when(contactMapper.selectCount(any())).thenReturn(2L);
        ActorCareerHubServiceImpl service = new ActorCareerHubServiceImpl(
                profileMapper, workMapper, representativeMapper, assetMapper, cardMapper, contactMapper,
                new ObjectMapper());

        var summary = service.summary(7L);

        assertTrue(summary.getProfile().isCoreReady());
        assertEquals(8, summary.getProfile().getCareerFieldCount());
        assertEquals("杭州", summary.getProfile().getCurrentCity());
        assertEquals(29L, summary.getWorks().getTotal());
        assertEquals(6L, summary.getWorks().getRepresentativeCount());
        assertEquals(14L, summary.getAssets().getPhotoCount());
        assertEquals(3L, summary.getAssets().getVideoCount());
        assertTrue(summary.getAssets().isHasCurrentResume());
        assertEquals(2L, summary.getPendingContactRequests());
    }

    private ActorProfile completeProfile() {
        ActorProfile profile = new ActorProfile();
        profile.setActorProfileId(11L);
        profile.setUserId(7L);
        profile.setAvatarAssetId(81L);
        profile.setCurrentResumeAssetId(82L);
        profile.setNickName("王火火");
        profile.setGender(2);
        profile.setAge(21);
        profile.setHeight(170);
        profile.setLocationCity("杭州");
        profile.setWeight(45);
        profile.setOriginPlace("中国香港");
        profile.setSchoolName("浙江传媒学院");
        profile.setMajorName("表演专业");
        profile.setLanguageTagsJson("[\"粤语\"]");
        profile.setSpecialtyTagsJson("[\"表演\"]");
        profile.setRoleTypeTagsJson("[\"女主\"]");
        profile.setProfessionalAbilityTagsJson("[\"同期声\"]");
        return profile;
    }
}
