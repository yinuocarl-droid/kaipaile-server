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
import java.time.LocalDate;
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
        when(profileMapper.incrementWorkLibraryVersionIfExpected(any(), any())).thenReturn(1);
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
        verify(workWriter, times(2)).createImportedWork(eq(7L), work.capture());
        verify(profileMapper).incrementWorkLibraryVersionIfExpected(9L, 0L);
        assertEquals(List.of("作品一", "作品二"), work.getAllValues().stream()
                .map(ActorWorkSaveDTO::getProjectName)
                .toList());
        assertTrue(summary.contains("2"));
    }

    @Test
    void selectedSkipDoesNotWriteWorkAndReportsSkippedCount() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        when(profileMapper.insert(any())).thenAnswer(call -> {
            ((ActorProfile) call.getArgument(0)).setActorProfileId(9L);
            return 1;
        });
        ProfileImportApplyReqDTO request = worksOnly("作品一", "作品二");
        request.getWorks().get(0).setSelectedAction("skip");
        request.getWorks().get(0).setMatchedExperienceId(101L);
        request.getWorks().get(1).setSelectedAction("create");

        String summary = writer.applyImport(7L, request);

        verify(workWriter, times(1)).createImportedWork(eq(7L), any());
        verify(profileMapper).incrementWorkLibraryVersionIfExpected(9L, 0L);
        assertTrue(summary.contains("worksSkipped"));
        assertTrue(summary.contains("1"));
    }

    @Test
    void selectedMergeUpdatesTheMatchedWorkAndReportsMergedCount() {
        when(profileMapper.selectOne(any())).thenReturn(existingProfile());
        ProfileImportApplyReqDTO request = worksOnly("作品一");
        ProfileImportApplyReqDTO.ConfirmedWork work = request.getWorks().get(0);
        work.setAchievementText("模型候选成绩");
        work.setDescription("模型候选描述");
        work.setSelectedAction("merge");
        work.setMatchedExperienceId(101L);
        ProfileImportApplyReqDTO.WorkFields finalFields = fields(work);
        finalFields.setAchievementText("用户最终成绩");
        finalFields.setDescription("用户最终描述");
        work.setFinalFields(finalFields);

        String summary = writer.applyImport(7L, request);

        var written = org.mockito.ArgumentCaptor.forClass(ActorWorkSaveDTO.class);
        verify(workWriter).updateImportedWork(eq(7L), eq(101L), written.capture());
        verify(profileMapper).incrementWorkLibraryVersionIfExpected(9L, 0L);
        assertEquals("用户最终成绩", written.getValue().getAchievementText());
        assertEquals("用户最终描述", written.getValue().getDescription());
        assertTrue(summary.contains("worksMerged"));
        assertTrue(summary.contains("1"));
    }

    @Test
    void createRejectsUnsignedUserFinalFields() {
        when(profileMapper.selectOne(any())).thenReturn(existingProfile());
        ProfileImportApplyReqDTO request = worksOnly("模型候选作品");
        ProfileImportApplyReqDTO.ConfirmedWork work = request.getWorks().get(0);
        work.setSelectedAction("create");
        ProfileImportApplyReqDTO.WorkFields finalFields = fields(work);
        finalFields.setProjectName("用户最终作品");
        finalFields.setDescription("用户最终描述");
        work.setFinalFields(finalFields);

        BizException error = assertThrows(BizException.class, () -> writer.applyImport(7L, request));

        assertEquals(46008, error.getCode());
        verifyNoInteractions(workWriter);
        verify(profileMapper, never()).incrementWorkLibraryVersionIfExpected(any(), any());
    }

    @Test
    void skipDoesNotConsumeFinalFields() {
        when(profileMapper.selectOne(any())).thenReturn(existingProfile());
        ProfileImportApplyReqDTO request = worksOnly("作品一");
        ProfileImportApplyReqDTO.ConfirmedWork work = request.getWorks().get(0);
        work.setSelectedAction("skip");
        work.setMatchedExperienceId(101L);
        work.setFinalFields(new ProfileImportApplyReqDTO.WorkFields());

        String summary = writer.applyImport(7L, request);

        verifyNoInteractions(workWriter);
        verify(profileMapper, never()).incrementWorkLibraryVersionIfExpected(any(), any());
        assertTrue(summary.contains("worksSkipped"));
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
    void existingFullProfileAppliesOnlyConfirmedCandidateAndPreservesAbsentFields() {
        ActorProfile current = completeProfile();
        when(profileMapper.selectOne(any())).thenReturn(current);
        when(profileMapper.updateById(any())).thenReturn(1);
        ProfileImportApplyReqDTO request = worksOnly();
        request.setScene("full_profile");
        candidate(request, "public_name", "新公开名");

        writer.applyImport(7L, request);

        var written = org.mockito.ArgumentCaptor.forClass(ActorProfile.class);
        verify(profileMapper).updateById(written.capture());
        ActorProfile profile = written.getValue();
        assertEquals("新公开名", profile.getNickName());
        assertEquals(77L, profile.getAvatarAssetId());
        assertEquals(1, profile.getGender());
        assertEquals(38, profile.getAge());
        assertEquals(182, profile.getHeight());
        assertEquals("深圳", profile.getLocationCity());
        assertEquals(75, profile.getWeight());
        assertEquals("广东", profile.getOriginPlace());
        assertEquals("表演学院", profile.getSchoolName());
        assertEquals("表演", profile.getMajorName());
        assertEquals("原简介", profile.getIntro());
        assertEquals(LocalDate.of(1988, 5, 16), profile.getBirthday());
        assertEquals(1988, profile.getBirthYear());
        assertEquals(5, profile.getBirthMonth());
        assertEquals(16, profile.getBirthDayOfMonth());
        assertEquals("day", profile.getBirthPrecision());
        assertEquals("[\"粤语\"]", profile.getLanguageTagsJson());
        assertEquals("[\"武术\"]", profile.getSpecialtyTagsJson());
        assertEquals("[\"硬汉\"]", profile.getRoleTypeTagsJson());
        assertEquals("[\"同期声\"]", profile.getProfessionalAbilityTagsJson());
        verifyNoInteractions(assets);
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

    @Test
    void profileShellInsertFailureStopsBeforeAnyWorkWrite() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        when(profileMapper.insert(any())).thenReturn(0);

        BizException error = assertThrows(
                BizException.class, () -> writer.applyImport(7L, worksOnly("作品一")));

        assertEquals(46008, error.getCode());
        verifyNoInteractions(workWriter);
        verify(profileMapper, never()).incrementWorkLibraryVersionIfExpected(any(), any());
    }

    @Test
    void workLibraryVersionCasFailureFailsClosedAfterWorkWrites() {
        when(profileMapper.selectOne(any())).thenReturn(existingProfile());
        when(profileMapper.incrementWorkLibraryVersionIfExpected(9L, 0L)).thenReturn(0);

        BizException error = assertThrows(
                BizException.class, () -> writer.applyImport(7L, worksOnly("作品一", "作品二")));

        assertEquals(46010, error.getCode());
        verify(workWriter, times(2)).createImportedWork(eq(7L), any());
        verify(profileMapper).incrementWorkLibraryVersionIfExpected(9L, 0L);
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

    private ActorProfile existingProfile() {
        ActorProfile profile = new ActorProfile();
        profile.setActorProfileId(9L);
        profile.setUserId(7L);
        profile.setVersion(0);
        profile.setWorkLibraryVersion(0L);
        return profile;
    }

    private ActorProfile completeProfile() {
        ActorProfile profile = existingProfile();
        profile.setAvatarAssetId(77L);
        profile.setNickName("旧公开名");
        profile.setGender(1);
        profile.setAge(38);
        profile.setHeight(182);
        profile.setLocationCity("深圳");
        profile.setWeight(75);
        profile.setOriginPlace("广东");
        profile.setSchoolName("表演学院");
        profile.setMajorName("表演");
        profile.setIntro("原简介");
        profile.setBirthday(LocalDate.of(1988, 5, 16));
        profile.setBirthYear(1988);
        profile.setBirthMonth(5);
        profile.setBirthDayOfMonth(16);
        profile.setBirthPrecision("day");
        profile.setLanguageTagsJson("[\"粤语\"]");
        profile.setSpecialtyTagsJson("[\"武术\"]");
        profile.setRoleTypeTagsJson("[\"硬汉\"]");
        profile.setProfessionalAbilityTagsJson("[\"同期声\"]");
        return profile;
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

    private ProfileImportApplyReqDTO.WorkFields fields(ProfileImportApplyReqDTO.ConfirmedWork work) {
        ProfileImportApplyReqDTO.WorkFields fields = new ProfileImportApplyReqDTO.WorkFields();
        fields.setProjectName(work.getProjectName());
        fields.setRoleName(work.getRoleName());
        fields.setPublishStatus(work.getPublishStatus());
        fields.setWorkTypeCode(work.getWorkTypeCode());
        fields.setRoleLevelCode(work.getRoleLevelCode());
        fields.setShootYear(work.getShootYear());
        fields.setShootMonth(work.getShootMonth());
        fields.setPlatform(work.getPlatform());
        fields.setSyncSoundStatus(work.getSyncSoundStatus());
        fields.setCollaborators(work.getCollaborators());
        fields.setAchievementText(work.getAchievementText());
        fields.setDescription(work.getDescription());
        return fields;
    }
}
