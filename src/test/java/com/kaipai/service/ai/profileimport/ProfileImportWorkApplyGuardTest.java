package com.kaipai.service.ai.profileimport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ProfileImportWorkApplyGuardTest {
    private ActorExperienceMapper experienceMapper;
    private ActorProfileMapper profileMapper;
    private ProfileImportWorkApplyGuard guard;

    @BeforeEach
    void setUp() {
        experienceMapper = mock(ActorExperienceMapper.class);
        profileMapper = mock(ActorProfileMapper.class);
        guard = new ProfileImportWorkApplyGuard(
                experienceMapper,
                profileMapper,
                new ProfileImportWorkMatchSupport(new ObjectMapper()));
        when(profileMapper.selectOne(any())).thenReturn(profile(9L, 7L));
    }

    @Test
    void acceptsValidExactSkipAfterLockingTheProofBoundTarget() {
        ActorExperience target = existing(91L, 9L, "作品一", "女主");
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 91L)).thenReturn(target);

        assertDoesNotThrow(() -> guard.validateAndLock(7L, List.of(exactSkip(target))));

        verify(experienceMapper).selectOwnedActiveByIdForUpdate(7L, 91L);
    }

    @Test
    void acceptsConflictMergeMixingOnlyCandidateAndCurrentValues() {
        ActorExperience target = existing(91L, 9L, "作品一", "女主");
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 91L)).thenReturn(target);
        ProfileImportApplyReqDTO.ConfirmedWork work = conflictMerge(target);
        work.getFinalFields().setAchievementText(work.getAchievementText());
        work.getFinalFields().setDescription(target.getRoleDesc());

        assertDoesNotThrow(() -> guard.validateAndLock(7L, List.of(work)));
    }

    @Test
    void locksTargetsInAscendingIdOrderRegardlessOfRequestOrder() {
        ActorExperience first = existing(91L, 9L, "作品一", "女主");
        ActorExperience second = existing(92L, 9L, "作品二", "女二");
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 91L)).thenReturn(first);
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 92L)).thenReturn(second);

        guard.validateAndLock(7L, List.of(exactSkip(second), exactSkip(first)));

        InOrder order = inOrder(experienceMapper);
        order.verify(experienceMapper).selectOwnedActiveByIdForUpdate(7L, 91L);
        order.verify(experienceMapper).selectOwnedActiveByIdForUpdate(7L, 92L);
    }

    @Test
    void rejectsMissingTarget() {
        assertConflict(() -> guard.validateAndLock(7L, List.of(exactSkip(
                existing(91L, 9L, "作品一", "女主")))));
    }

    @Test
    void rejectsForeignTargetEvenIfMapperReturnsItDefensively() {
        ActorExperience target = existing(91L, 9L, "作品一", "女主");
        target.setUserId(8L);
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 91L)).thenReturn(target);

        assertConflict(() -> guard.validateAndLock(7L, List.of(exactSkip(target))));
    }

    @Test
    void rejectsDeletedTargetEvenIfMapperReturnsItDefensively() {
        ActorExperience target = existing(91L, 9L, "作品一", "女主");
        target.setDeleted(1);
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 91L)).thenReturn(target);

        assertConflict(() -> guard.validateAndLock(7L, List.of(exactSkip(target))));
    }

    @Test
    void rejectsTargetFromAnotherActorProfile() {
        ActorExperience target = existing(91L, 10L, "作品一", "女主");
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 91L)).thenReturn(target);

        assertConflict(() -> guard.validateAndLock(7L, List.of(exactSkip(target))));
    }

    @Test
    void rejectsIdentityRebindingAfterExtraction() {
        ActorExperience proofBound = existing(91L, 9L, "作品一", "女主");
        ProfileImportApplyReqDTO.ConfirmedWork work = exactSkip(proofBound);
        ActorExperience rebound = existing(91L, 9L, "另一作品", "女主");
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 91L)).thenReturn(rebound);

        assertConflict(() -> guard.validateAndLock(7L, List.of(work)));
    }

    @Test
    void rejectsStaleConflictSetAfterTargetChanges() {
        ActorExperience original = existing(91L, 9L, "作品一", "女主");
        ProfileImportApplyReqDTO.ConfirmedWork work = conflictMerge(original);
        ActorExperience changed = existing(91L, 9L, "作品一", "女主");
        changed.setPlatform("新平台");
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 91L)).thenReturn(changed);

        assertConflict(() -> guard.validateAndLock(7L, List.of(work)));
    }

    @Test
    void rejectsThirdValueForAConfirmedConflict() {
        ActorExperience target = existing(91L, 9L, "作品一", "女主");
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 91L)).thenReturn(target);
        ProfileImportApplyReqDTO.ConfirmedWork work = conflictMerge(target);
        work.getFinalFields().setAchievementText("既非候选也非当前值");

        assertConflict(() -> guard.validateAndLock(7L, List.of(work)));
    }

    @Test
    void acceptsSignedCandidateFormattingWhenNormalizedIdentityIsUnchanged() {
        ActorExperience target = existing(91L, 9L, "作品一", "女主");
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 91L)).thenReturn(target);
        ProfileImportApplyReqDTO.ConfirmedWork work = conflictMerge(target);
        work.setProjectName("《作品一》");
        work.getFinalFields().setProjectName("《作品一》");

        assertDoesNotThrow(() -> guard.validateAndLock(7L, List.of(work)));
    }

    @Test
    void rejectsUnsignedThirdIdentityValue() {
        ActorExperience target = existing(91L, 9L, "作品一", "女主");
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 91L)).thenReturn(target);
        ProfileImportApplyReqDTO.ConfirmedWork work = conflictMerge(target);
        work.getFinalFields().setProjectName("第三部作品");

        assertConflict(() -> guard.validateAndLock(7L, List.of(work)));
    }

    private void assertConflict(Runnable action) {
        BizException error = assertThrows(BizException.class, action::run);
        assertEquals(46008, error.getCode());
    }

    private ProfileImportApplyReqDTO.ConfirmedWork exactSkip(ActorExperience target) {
        ProfileImportApplyReqDTO.ConfirmedWork work = candidate(target);
        work.setMatchStatus("exact_match");
        work.setSelectedAction("skip");
        work.setMatchedExperienceId(target.getExperienceId());
        work.setAllowedActions(List.of("skip"));
        work.setConflictFields(List.of());
        work.setConfirmedConflictFields(List.of());
        return work;
    }

    private ProfileImportApplyReqDTO.ConfirmedWork conflictMerge(ActorExperience target) {
        ProfileImportApplyReqDTO.ConfirmedWork work = candidate(target);
        work.setAchievementText("候选成绩");
        work.setDescription("候选描述");
        work.setMatchStatus("field_conflict");
        work.setSelectedAction("merge");
        work.setMatchedExperienceId(target.getExperienceId());
        work.setAllowedActions(List.of("merge", "skip"));
        work.setConflictFields(List.of("achievementText", "description"));
        work.setConfirmedConflictFields(List.of("achievementText", "description"));
        work.setFinalFields(finalFields(target));
        return work;
    }

    private ProfileImportApplyReqDTO.ConfirmedWork candidate(ActorExperience target) {
        ProfileImportApplyReqDTO.ConfirmedWork work = new ProfileImportApplyReqDTO.ConfirmedWork();
        work.setCandidateId("work-" + target.getExperienceId());
        work.setProjectName(target.getDramaName());
        work.setRoleName(target.getRoleName());
        work.setPublishStatus(target.getPublishStatus());
        work.setWorkTypeCode(target.getWorkTypeCode());
        work.setRoleLevelCode(target.getRoleLevelCode());
        work.setShootYear(target.getShootYear());
        work.setShootMonth(target.getShootMonth());
        work.setPlatform(target.getPlatform());
        work.setSyncSoundStatus(target.getSyncSoundStatus());
        work.setCollaborators(List.of("演员甲"));
        work.setAchievementText(target.getAchievementText());
        work.setDescription(target.getRoleDesc());
        return work;
    }

    private ProfileImportApplyReqDTO.WorkFields finalFields(ActorExperience target) {
        ProfileImportApplyReqDTO.WorkFields fields = new ProfileImportApplyReqDTO.WorkFields();
        fields.setProjectName(target.getDramaName());
        fields.setRoleName(target.getRoleName());
        fields.setPublishStatus(target.getPublishStatus());
        fields.setWorkTypeCode(target.getWorkTypeCode());
        fields.setRoleLevelCode(target.getRoleLevelCode());
        fields.setShootYear(target.getShootYear());
        fields.setShootMonth(target.getShootMonth());
        fields.setPlatform(target.getPlatform());
        fields.setSyncSoundStatus(target.getSyncSoundStatus());
        fields.setCollaborators(List.of("演员甲"));
        fields.setAchievementText(target.getAchievementText());
        fields.setDescription(target.getRoleDesc());
        return fields;
    }

    private ActorExperience existing(Long id, Long profileId, String projectName, String roleName) {
        ActorExperience target = new ActorExperience();
        target.setExperienceId(id);
        target.setUserId(7L);
        target.setActorProfileId(profileId);
        target.setDeleted(0);
        target.setDramaName(projectName);
        target.setRoleName(roleName);
        target.setPublishStatus("aired");
        target.setWorkTypeCode("short_drama");
        target.setRoleLevelCode("lead");
        target.setShootYear(2025);
        target.setShootMonth(6);
        target.setPlatform("红果");
        target.setSyncSoundStatus("sync");
        target.setCollaboratorsJson("[\"演员甲\"]");
        target.setAchievementText("当前成绩");
        target.setRoleDesc("当前描述");
        return target;
    }

    private ActorProfile profile(Long profileId, Long userId) {
        ActorProfile profile = new ActorProfile();
        profile.setActorProfileId(profileId);
        profile.setUserId(userId);
        profile.setDeleted(0);
        return profile;
    }
}
