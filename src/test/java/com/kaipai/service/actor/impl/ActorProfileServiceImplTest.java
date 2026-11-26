package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.model.actor.dto.ActorProfileSaveDTO;
import com.kaipai.model.actor.dto.ActorWorkExperienceDTO;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.user.entity.User;
import com.kaipai.service.ai.AiResumeApplyRecorder;
import com.kaipai.service.capability.CapabilityAccountService;
import com.kaipai.service.referral.ReferralRecordService;
import com.kaipai.service.actor.support.LegacyProfileWriteGuard;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActorProfileServiceImplTest {

    private static final long USER_ID = 7L;

    private ActorExperienceMapper experienceMapper;
    private ReferralRecordService referralRecordService;
    private ActorProfileServiceImpl service;
    private ActorProfile profile;

    @BeforeEach
    void setUp() {
        experienceMapper = mock(ActorExperienceMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        referralRecordService = mock(ReferralRecordService.class);
        service = spy(new ActorProfileServiceImpl(
                experienceMapper,
                userMapper,
                mock(CapabilityAccountService.class),
                new ObjectMapper(),
                mock(AiResumeApplyRecorder.class),
                referralRecordService,
                new LegacyProfileWriteGuard()));

        User user = new User();
        user.setUserId(USER_ID);
        user.setPhone("13800000000");
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        profile = new ActorProfile();
        profile.setActorProfileId(11L);
        profile.setUserId(USER_ID);
        profile.setPhotoUrls("legacy-photo-json");
        profile.setVideoUrl("legacy-video");
        profile.setExperienceDesc("legacy-work-summary");
        profile.setExtendedField("legacy-profile-extras");
        doReturn(profile).when(service).getOne(any(), any(Boolean.class));
        doReturn(true).when(service).updateById(any(ActorProfile.class));
    }

    @Test
    void emptyLegacyCollectionsAreNoOpForWorksAndMedia() {
        ActorExperience existingWork = new ActorExperience();
        existingWork.setExperienceId(31L);
        existingWork.setUserId(USER_ID);
        when(experienceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existingWork));

        service.saveProfile(USER_ID, scalarOnlyRequest());

        verify(experienceMapper, never()).delete(any(Wrapper.class));
        verify(experienceMapper, never()).deleteBatchIds(any());
        verify(experienceMapper, never()).insert(any());
        verify(experienceMapper, never()).updateById(any());
        assertEquals("legacy-photo-json", profile.getPhotoUrls());
        assertEquals("legacy-video", profile.getVideoUrl());
        assertEquals("legacy-work-summary", profile.getExperienceDesc());
    }

    @Test
    void nonEmptyLegacyWorksAreRejectedBeforeProfileMutation() {
        ActorProfileSaveDTO request = scalarOnlyRequest();
        ActorWorkExperienceDTO work = new ActorWorkExperienceDTO();
        work.setProjectName("绝不回头，白爷宠她成瘾");
        request.setWorkExperiences(List.of(work));

        BizException error = assertThrows(BizException.class, () -> service.saveProfile(USER_ID, request));

        assertEquals(46017, error.getCode());
        verify(service, never()).updateById(any(ActorProfile.class));
        verify(experienceMapper, never()).insert(any());
        verify(referralRecordService, never()).reconcileInviteeReferral(any());
    }

    @Test
    void nonEmptyLegacyMediaAreRejectedBeforeProfileMutation() {
        ActorProfileSaveDTO request = scalarOnlyRequest();
        request.setPhotos(List.of("legacy-photo"));

        BizException error = assertThrows(BizException.class, () -> service.saveProfile(USER_ID, request));

        assertEquals(46017, error.getCode());
        verify(service, never()).updateById(any(ActorProfile.class));
        verify(referralRecordService, never()).reconcileInviteeReferral(any());
    }

    private ActorProfileSaveDTO scalarOnlyRequest() {
        ActorProfileSaveDTO request = new ActorProfileSaveDTO();
        request.setName("王火火");
        request.setGender("female");
        request.setAge(21);
        request.setHeight(170);
        request.setWeight(45);
        request.setCity("杭州");
        request.setIntro("演员");
        return request;
    }
}
