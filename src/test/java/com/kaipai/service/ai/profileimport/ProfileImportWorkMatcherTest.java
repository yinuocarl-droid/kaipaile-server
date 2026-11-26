package com.kaipai.service.ai.profileimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.model.ai.dto.ProfileImportExtractionRespDTO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProfileImportWorkMatcherTest {
    private ActorExperienceMapper experienceMapper;
    private ProfileImportWorkMatcher matcher;

    @BeforeEach
    void setUp() {
        experienceMapper = mock(ActorExperienceMapper.class);
        matcher = new ProfileImportWorkMatcher(
                experienceMapper, new ProfileImportWorkMatchSupport(new ObjectMapper()));
    }

    @Test
    void emptyCandidatesDoNotQueryWorks() {
        matcher.match(7L, List.of());

        verifyNoInteractions(experienceMapper);
    }

    @Test
    void marksUnmatchedCandidateForCreationAndQueriesOnlyOwnedActiveWorks() {
        when(experienceMapper.selectList(any())).thenReturn(List.of());
        ProfileImportExtractionRespDTO.WorkCandidate candidate = candidate("新作品", "女主");

        matcher.match(7L, List.of(candidate));

        assertEquals("new", candidate.getMatchStatus());
        assertNull(candidate.getMatchedExperienceId());
        assertEquals("create", candidate.getSelectedAction());
        assertEquals(List.of("create"), candidate.getAllowedActions());
        assertEquals(List.of(), candidate.getConflictFields());

        verify(experienceMapper).selectList(any());
    }

    @Test
    void marksSingleStructurallyEqualWorkAsExactMatchAndParsesCollaboratorsJson() {
        ActorExperience existing = existing(11L, "《作品一》", " 女主 ");
        existing.setPublishStatus("aired");
        existing.setWorkTypeCode("short_drama");
        existing.setRoleLevelCode("lead");
        existing.setShootYear(2025);
        existing.setShootMonth(6);
        existing.setPlatform("红果");
        existing.setSyncSoundStatus("sync");
        existing.setCollaboratorsJson("[\"演员甲\",\"演员乙\"]");
        existing.setAchievementText("热度第一");
        existing.setRoleDesc("角色描述");
        ActorExperience anotherUsersWork = existing(98L, "作品一", "女主");
        anotherUsersWork.setUserId(8L);
        ActorExperience deletedWork = existing(99L, "作品一", "女主");
        deletedWork.setDeleted(1);
        when(experienceMapper.selectList(any())).thenReturn(List.of(existing, anotherUsersWork, deletedWork));
        ProfileImportExtractionRespDTO.WorkCandidate candidate = candidate("作品一", "女主");
        candidate.setPublishStatus("aired");
        candidate.setWorkTypeCode("short_drama");
        candidate.setRoleLevelCode("lead");
        candidate.setShootYear(2025);
        candidate.setShootMonth(6);
        candidate.setPlatform("红果");
        candidate.setSyncSoundStatus("sync");
        candidate.setCollaborators(List.of("演员甲", "演员乙"));
        candidate.setAchievementText("热度第一");
        candidate.setDescription("角色描述");

        matcher.match(7L, List.of(candidate));

        assertEquals("exact_match", candidate.getMatchStatus());
        assertEquals(11L, candidate.getMatchedExperienceId());
        assertEquals("skip", candidate.getSelectedAction());
        assertEquals(List.of("skip"), candidate.getAllowedActions());
        assertEquals(List.of(), candidate.getConflictFields());
    }

    @Test
    void marksSingleIdentityMatchWithAnyStructuredDifferenceAsFieldConflict() {
        ActorExperience existing = existing(12L, "作品一", "女主");
        existing.setCollaboratorsJson("[\"演员甲\"]");
        when(experienceMapper.selectList(any())).thenReturn(List.of(existing));
        ProfileImportExtractionRespDTO.WorkCandidate candidate = candidate("《作品一》", "女主");
        candidate.setCollaborators(List.of("演员乙"));
        ProfileImportExtractionRespDTO.FieldEvidence evidence =
                new ProfileImportExtractionRespDTO.FieldEvidence();
        evidence.setCandidateValue(List.of("演员乙"));
        evidence.setConfidence(0.99d);
        evidence.setSourceText("合作演员：演员乙");
        evidence.setSourceType("explicit");
        candidate.getFields().put("collaborators", evidence);

        matcher.match(7L, List.of(candidate));

        assertEquals("field_conflict", candidate.getMatchStatus());
        assertEquals(12L, candidate.getMatchedExperienceId());
        assertEquals("skip", candidate.getSelectedAction());
        assertEquals(List.of("merge", "skip"), candidate.getAllowedActions());
        assertEquals(List.of("collaborators"), candidate.getConflictFields());
        assertEquals(1, candidate.getConflicts().size());
        assertEquals(List.of("演员甲"), candidate.getConflicts().get(0).getExistingValue());
        assertEquals(List.of("演员乙"), candidate.getConflicts().get(0).getCandidateValue());
        assertEquals("合作演员：演员乙", candidate.getConflicts().get(0).getSourceText());
    }

    @Test
    void reportsStructuredConflictFieldsInStableOrder() {
        ActorExperience existing = existing(15L, "作品一", "女主");
        existing.setPublishStatus("aired");
        existing.setWorkTypeCode("short_drama");
        existing.setRoleLevelCode("lead");
        existing.setShootYear(2024);
        existing.setShootMonth(1);
        existing.setPlatform("平台甲");
        existing.setSyncSoundStatus("sync");
        existing.setCollaboratorsJson("[\"演员甲\"]");
        existing.setAchievementText("成绩甲");
        existing.setRoleDesc("描述甲");
        when(experienceMapper.selectList(any())).thenReturn(List.of(existing));
        ProfileImportExtractionRespDTO.WorkCandidate candidate = candidate("作品一", "女主");
        candidate.setPublishStatus("upcoming");
        candidate.setWorkTypeCode("horizontal");
        candidate.setRoleLevelCode("supporting");
        candidate.setShootYear(2025);
        candidate.setShootMonth(2);
        candidate.setPlatform("平台乙");
        candidate.setSyncSoundStatus("dubbed");
        candidate.setCollaborators(List.of("演员乙"));
        candidate.setAchievementText("成绩乙");
        candidate.setDescription("描述乙");

        matcher.match(7L, List.of(candidate));

        assertEquals(List.of(
                "publishStatus", "workTypeCode", "roleLevelCode", "shootYear", "shootMonth",
                "platform", "syncSoundStatus", "collaborators", "achievementText", "description"),
                candidate.getConflictFields());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void treatsSqlNullAndBlankCollaboratorsAsAnEmptyList(String collaboratorsJson) {
        ActorExperience existing = existing(13L, "作品一", "女主");
        existing.setCollaboratorsJson(collaboratorsJson);
        when(experienceMapper.selectList(any())).thenReturn(List.of(existing));
        ProfileImportExtractionRespDTO.WorkCandidate candidate = candidate("作品一", "女主");

        matcher.match(7L, List.of(candidate));

        assertEquals("exact_match", candidate.getMatchStatus());
        assertEquals(13L, candidate.getMatchedExperienceId());
        assertEquals("skip", candidate.getSelectedAction());
        assertEquals(List.of("skip"), candidate.getAllowedActions());
        assertEquals(List.of(), candidate.getConflictFields());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{malformed", "{}", "null", "[\"演员甲\",1]"})
    void treatsMalformedJsonNullNonArrayAndNonStringCollaboratorsAsFieldConflict(
            String collaboratorsJson) {
        ActorExperience existing = existing(14L, "作品一", "女主");
        existing.setCollaboratorsJson(collaboratorsJson);
        when(experienceMapper.selectList(any())).thenReturn(List.of(existing));
        ProfileImportExtractionRespDTO.WorkCandidate candidate = candidate("作品一", "女主");

        matcher.match(7L, List.of(candidate));

        assertEquals("field_conflict", candidate.getMatchStatus());
        assertEquals(14L, candidate.getMatchedExperienceId());
        assertEquals("skip", candidate.getSelectedAction());
        assertEquals(List.of("merge", "skip"), candidate.getAllowedActions());
        assertEquals(List.of("collaborators"), candidate.getConflictFields());
    }

    @Test
    void ignoresForeignAndDeletedIdentityMatchesDefensively() {
        ActorExperience anotherUsersWork = existing(31L, "作品一", "女主");
        anotherUsersWork.setUserId(8L);
        ActorExperience deletedWork = existing(32L, "作品一", "女主");
        deletedWork.setDeleted(1);
        when(experienceMapper.selectList(any())).thenReturn(List.of(anotherUsersWork, deletedWork));
        ProfileImportExtractionRespDTO.WorkCandidate candidate = candidate("作品一", "女主");

        matcher.match(7L, List.of(candidate));

        assertEquals("new", candidate.getMatchStatus());
        assertNull(candidate.getMatchedExperienceId());
        assertEquals("create", candidate.getSelectedAction());
        assertEquals(List.of("create"), candidate.getAllowedActions());
        assertEquals(List.of(), candidate.getConflictFields());
    }

    @Test
    void marksMultipleIdentityMatchesAsAmbiguousWithoutSelectingATarget() {
        when(experienceMapper.selectList(any())).thenReturn(List.of(
                existing(21L, "作品一", "女主"),
                existing(22L, "《作品一》", " 女主 ")));
        ProfileImportExtractionRespDTO.WorkCandidate candidate = candidate("作品一", "女主");

        matcher.match(7L, List.of(candidate));

        assertEquals("ambiguous", candidate.getMatchStatus());
        assertNull(candidate.getMatchedExperienceId());
        assertEquals("skip", candidate.getSelectedAction());
        assertEquals(List.of("skip"), candidate.getAllowedActions());
        assertEquals(List.of(), candidate.getConflictFields());
    }

    private ProfileImportExtractionRespDTO.WorkCandidate candidate(String projectName, String roleName) {
        ProfileImportExtractionRespDTO.WorkCandidate candidate =
                new ProfileImportExtractionRespDTO.WorkCandidate();
        candidate.setProjectName(projectName);
        candidate.setRoleName(roleName);
        return candidate;
    }

    private ActorExperience existing(Long id, String projectName, String roleName) {
        ActorExperience existing = new ActorExperience();
        existing.setExperienceId(id);
        existing.setUserId(7L);
        existing.setDeleted(0);
        existing.setDramaName(projectName);
        existing.setRoleName(roleName);
        return existing;
    }
}
