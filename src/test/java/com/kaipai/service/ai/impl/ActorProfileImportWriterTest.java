package com.kaipai.service.ai.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.dto.ActorWorkSaveDTO;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import com.kaipai.service.actor.ActorMediaAssetOwnershipVerifier;
import com.kaipai.service.actor.ActorWorkInternalWriter;
import com.kaipai.service.actor.ActorWorkSourceType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActorProfileImportWriterTest {
    private ActorProfileMapper profileMapper;
    private ActorWorkInternalWriter workWriter;
    private ActorMediaAssetOwnershipVerifier assets;
    private ActorProfileImportWriter writer;

    @BeforeEach
    void setUp() {
        profileMapper = mock(ActorProfileMapper.class);
        workWriter = mock(ActorWorkInternalWriter.class);
        assets = mock(ActorMediaAssetOwnershipVerifier.class);
        writer = new ActorProfileImportWriter(profileMapper, workWriter, assets, new ObjectMapper());
    }

    @Test
    void worksOnlyCreatesIncompleteShellAndWritesEveryWork() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        when(profileMapper.insert(any())).thenAnswer(call -> {
            ((ActorProfile) call.getArgument(0)).setActorProfileId(9L);
            return 1;
        });
        ProfileImportApplyReqDTO request = worksOnly("作品一", "作品二");

        String summary = writer.applyImport(7L, request);

        var profile = org.mockito.ArgumentCaptor.forClass(ActorProfile.class);
        verify(profileMapper).insert(profile.capture());
        assertEquals(3, profile.getValue().getProfileStatus());
        assertEquals(0L, profile.getValue().getWorkLibraryVersion());
        var work = org.mockito.ArgumentCaptor.forClass(ActorWorkSaveDTO.class);
        verify(workWriter, times(2)).createWork(
                eq(7L), work.capture(), eq(ActorWorkSourceType.IMPORT));
        assertEquals(List.of("作品一", "作品二"), work.getAllValues().stream()
                .map(ActorWorkSaveDTO::getProjectName)
                .toList());
        assertTrue(summary.contains("2"));
    }

    @Test
    void fullProfileWritesConfirmedCoreAndChecksAvatarBeforeMutation() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        when(profileMapper.insert(any())).thenAnswer(call -> {
            ((ActorProfile) call.getArgument(0)).setActorProfileId(9L);
            return 1;
        });
        when(profileMapper.updateById(any())).thenReturn(1);
        ProfileImportApplyReqDTO request = fullProfile();

        writer.applyImport(7L, request);

        verify(assets).requireOwnedReadyPhoto(7L, 81L);
        var profile = org.mockito.ArgumentCaptor.forClass(ActorProfile.class);
        verify(profileMapper).updateById(profile.capture());
        assertEquals("王火火", profile.getValue().getNickName());
        assertEquals(2, profile.getValue().getGender());
        assertEquals(170, profile.getValue().getHeight());
        assertEquals(1, profile.getValue().getProfileStatus());
    }

    @Test
    void fullProfileMissingRequiredCoreRejectsBeforeAnyWrite() {
        ProfileImportApplyReqDTO request = new ProfileImportApplyReqDTO();
        request.setScene("full_profile");
        request.setProfileVersion(0L);
        request.setWorkLibraryVersion(0L);

        assertThrows(BizException.class, () -> writer.applyImport(7L, request));

        verify(profileMapper, never()).insert(any());
        verify(profileMapper, never()).updateById(any());
        verifyNoInteractions(workWriter);
    }

    @Test
    void currentVersionConflictRejectsBeforeAnyWrite() {
        ActorProfile current = new ActorProfile();
        current.setUserId(7L); current.setVersion(3); current.setWorkLibraryVersion(4L);
        when(profileMapper.selectOne(any())).thenReturn(current);
        ProfileImportApplyReqDTO request = worksOnly("作品一");
        request.setProfileVersion(2L);

        BizException error = assertThrows(BizException.class, () -> writer.applyImport(7L, request));

        assertEquals(46010, error.getCode());
        verifyNoInteractions(workWriter);
    }

    private ProfileImportApplyReqDTO worksOnly(String... projects) {
        ProfileImportApplyReqDTO request = new ProfileImportApplyReqDTO();
        request.setScene("works_only");
        request.setProfileVersion(0L);
        request.setWorkLibraryVersion(0L);
        for (String project : projects) {
            ProfileImportApplyReqDTO.ConfirmedWork work = new ProfileImportApplyReqDTO.ConfirmedWork();
            work.setProjectName(project);
            work.setRoleName("女主");
            request.getWorks().add(work);
        }
        return request;
    }

    private ProfileImportApplyReqDTO fullProfile() {
        ProfileImportApplyReqDTO request = worksOnly();
        request.setScene("full_profile");
        request.setAvatarAssetId(81L);
        candidate(request, "public_name", "王火火");
        candidate(request, "gender", "female");
        candidate(request, "age", "21");
        candidate(request, "height", "170");
        candidate(request, "current_city", "杭州");
        return request;
    }

    private void candidate(ProfileImportApplyReqDTO request, String field, String value) {
        ProfileImportApplyReqDTO.ConfirmedCandidate candidate = new ProfileImportApplyReqDTO.ConfirmedCandidate();
        candidate.setFieldKey(field); candidate.setValue(value); candidate.setConfirmed(true);
        request.getProfileCandidates().add(candidate);
    }
}
