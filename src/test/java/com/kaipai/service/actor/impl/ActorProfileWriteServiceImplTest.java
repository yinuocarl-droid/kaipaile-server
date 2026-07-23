package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.dto.ActorProfileCareerUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileCoreUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileMineUpdateDTO;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.service.actor.ActorMediaAssetOwnershipVerifier;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActorProfileWriteServiceImplTest {

    private ActorProfileMapper profileMapper;
    private ActorMediaAssetOwnershipVerifier assetVerifier;
    private ActorProfileWriteServiceImpl service;
    private ActorProfile current;

    @BeforeEach
    void setUp() {
        profileMapper = org.mockito.Mockito.mock(ActorProfileMapper.class);
        assetVerifier = org.mockito.Mockito.mock(ActorMediaAssetOwnershipVerifier.class);
        service = new ActorProfileWriteServiceImpl(profileMapper, assetVerifier, new ObjectMapper());
        current = new ActorProfile();
        current.setActorProfileId(11L);
        current.setUserId(7L);
        current.setVersion(3);
        current.setWorkLibraryVersion(12L);
        when(profileMapper.selectOne(any())).thenReturn(current);
        when(profileMapper.updateById(any())).thenReturn(1);
    }

    @Test
    void saveMineRejectsStaleExpectedVersionBeforeMutation() {
        ActorProfileMineUpdateDTO request = validRequest();
        request.setExpectedProfileVersion(2);

        BizException error = assertThrows(BizException.class, () -> service.saveMine(7L, request));

        assertEquals(46010, error.getCode());
        verify(assetVerifier, never()).requireOwnedReadyPhoto(any(), any());
        verify(profileMapper, never()).updateById(any());
    }

    @Test
    void saveMineWritesOnlyCoreCareerIntroAndAvatar() {
        var response = service.saveMine(7L, validRequest());

        verify(assetVerifier).requireOwnedReadyPhoto(7L, 81L);
        ArgumentCaptor<ActorProfile> profileCaptor = ArgumentCaptor.forClass(ActorProfile.class);
        verify(profileMapper).updateById(profileCaptor.capture());
        ActorProfile saved = profileCaptor.getValue();
        assertEquals("王火火", saved.getNickName());
        assertEquals(2, saved.getGender());
        assertEquals(45, saved.getWeight());
        assertEquals("中国香港", saved.getOriginPlace());
        assertEquals(81L, saved.getAvatarAssetId());
        assertEquals(12L, response.getWorkLibraryVersion());
    }

    @Test
    void mineReturnsVersionedCareerProfile() {
        current.setNickName("王火火");
        current.setLanguageTagsJson("[\"粤语\",\"英语\"]");

        var response = service.mine(7L);

        assertEquals(3, response.getProfileVersion());
        assertEquals(12L, response.getWorkLibraryVersion());
        assertEquals("王火火", response.getPublicName());
        assertEquals(List.of("粤语", "英语"), response.getLanguageTags());
    }

    private ActorProfileMineUpdateDTO validRequest() {
        ActorProfileCoreUpdateDTO core = new ActorProfileCoreUpdateDTO();
        core.setPublicName("王火火");
        core.setGender("female");
        core.setAge(21);
        core.setHeight(170);
        core.setCurrentCity("杭州");

        ActorProfileCareerUpdateDTO career = new ActorProfileCareerUpdateDTO();
        career.setWeight(45);
        career.setOriginPlace("中国香港");
        career.setSchoolName("浙江传媒学院");
        career.setMajorName("表演专业");
        career.setLanguageTags(List.of("粤语", "英语"));

        ActorProfileMineUpdateDTO request = new ActorProfileMineUpdateDTO();
        request.setExpectedProfileVersion(3);
        request.setAvatarAssetId(81L);
        request.setCore(core);
        request.setCareer(career);
        request.setIntro("演员");
        return request;
    }
}
