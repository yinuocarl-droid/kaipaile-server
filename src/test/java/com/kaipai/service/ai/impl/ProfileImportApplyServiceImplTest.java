package com.kaipai.service.ai.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.ai.AiProfileImportRequestAuditMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import com.kaipai.model.ai.entity.AiProfileImportRequestAudit;
import com.kaipai.model.user.entity.User;
import com.kaipai.service.ai.ProfileImportWriter;
import com.kaipai.service.ai.profileimport.ProfileImportCandidateProofService;
import com.kaipai.service.ai.profileimport.ProfileImportPayloadHasher;
import com.kaipai.service.ai.profileimport.ProfileImportSchemaValidator;
import com.kaipai.service.ai.profileimport.ProfileImportWorkApplyGuard;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProfileImportApplyServiceImplTest {
    private AiProfileImportRequestAuditMapper auditMapper;
    private UserMapper userMapper;
    private ActorProfileMapper profileMapper;
    private ProfileImportCandidateProofService proofs;
    private ProfileImportPayloadHasher hasher;
    private ProfileImportWriter writer;
    private ProfileImportWorkApplyGuard workGuard;
    private ProfileImportApplyServiceImpl service;

    @BeforeEach
    void setUp() {
        auditMapper = mock(AiProfileImportRequestAuditMapper.class);
        userMapper = mock(UserMapper.class);
        profileMapper = mock(ActorProfileMapper.class);
        proofs = new ProfileImportCandidateProofService("secret");
        hasher = new ProfileImportPayloadHasher(new ObjectMapper());
        writer = mock(ProfileImportWriter.class);
        workGuard = mock(ProfileImportWorkApplyGuard.class);
        when(userMapper.selectActiveByIdForUpdate(any())).thenAnswer(invocation -> {
            User user = new User();
            user.setUserId(invocation.getArgument(0));
            user.setDeleted(0);
            return user;
        });
        when(auditMapper.updateById(any())).thenReturn(1);
        service = new ProfileImportApplyServiceImpl(
                auditMapper, userMapper, profileMapper, proofs, hasher,
                new ProfileImportSchemaValidator(), writer, workGuard);
    }

    @Test
    void applyRejectsUnconfirmedInferredGender() {
        ProfileImportApplyReqDTO request = profileRequest(false);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, request));

        assertEquals(46011, error.getCode());
        verifyNoInteractions(writer);
    }

    @Test
    void applyRejectsSceneChangedAfterExtraction() {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.setScene("works_only");
        AiProfileImportRequestAudit audit = extractedAudit();
        audit.setScene("full_profile");
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(audit);

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, request));

        assertEquals(46008, error.getCode());
        verifyNoInteractions(writer);
        verify(auditMapper, never()).updateById(any());
    }

    @Test
    void worksOnlySceneRejectsProfileCandidatesBeforeAnyBusinessWrite() {
        ProfileImportApplyReqDTO request = profileRequest(true);
        request.setScene("works_only");
        AiProfileImportRequestAudit audit = extractedAudit();
        audit.setScene("works_only");
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(audit);

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, request));

        assertEquals(46008, error.getCode());
        assertTrue(error.getMessage().contains("场景"));
        verifyNoInteractions(userMapper, profileMapper, writer, workGuard);
        verify(auditMapper, never()).updateById(any());
    }

    @Test
    void supportedSceneIsCanonicalizedBeforeHashingAndWriting() {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.setScene(" full_profile ");
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        when(writer.applyImport(7L, request)).thenReturn("done");

        assertEquals("done", service.apply(7L, request).getSummary());

        assertEquals("full_profile", request.getScene());
        verify(writer).applyImport(7L, request);
    }

    @Test
    void legacyUnknownSceneFailsClosedBeforeReturningAStoredApplyResult() {
        ProfileImportApplyReqDTO request = emptyRequest();
        AiProfileImportRequestAudit audit = extractedAudit();
        audit.setScene("legacy_unknown");
        audit.setApplyStatus("success");
        audit.setApplyPayloadSha256(hasher.hash(request));
        audit.setApplyResultSummaryJson("stored");
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(audit);

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, request));

        assertEquals(46008, error.getCode());
        assertTrue(error.getMessage().contains("场景"));
        verifyNoInteractions(writer, workGuard);
        verify(auditMapper, never()).updateById(any());
    }

    @Test
    void applyRejectsAnyUnconfirmedProfileCandidate() {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.getProfileCandidates().add(profileCandidate(
                "name-1", "public_name", "模型原值", "用户最终值", "direct", false, false));
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, request));

        assertEquals(46011, error.getCode());
        verifyNoInteractions(writer);
        verify(auditMapper, never()).updateById(any());
    }

    @Test
    void profileProofUsesModelCandidateValueWhileWriterReceivesFinalValue() {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.getProfileCandidates().add(profileCandidate(
                "name-1", "public_name", "模型原值", "用户最终值", "direct", false, true));
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        when(writer.applyImport(7L, request)).thenReturn("done");

        assertEquals("done", service.apply(7L, request).getSummary());

        verify(writer).applyImport(7L, request);
    }

    @ParameterizedTest(name = "rejects invalid final profile value for {0}: {2}")
    @MethodSource("invalidFinalProfileValues")
    void invalidFinalProfileValueIsRejectedBeforeWriter(
            String fieldKey, String candidateValue, String finalValue) {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.getProfileCandidates().add(profileCandidate(
                "profile-1", fieldKey, candidateValue, finalValue, "direct", false, true));
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @ParameterizedTest(name = "rejects invalid final tag structure: {0}")
    @MethodSource("invalidFinalTagValues")
    void invalidFinalTagStructureIsRejectedBeforeWriter(String finalValue) {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.getProfileCandidates().add(profileCandidate(
                "tags-1", "language_tags", "[\"粤语\"]", finalValue,
                "direct", false, true));
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void dayPrecisionWithoutACompleteFinalBirthdayIsRejectedBeforeWriter() {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.getProfileCandidates().add(profileCandidate(
                "birth-precision", "birth_precision", "day", "day",
                "direct", false, true));
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void birthdayFieldWithoutAFinalPrecisionReturnsStableConflict() {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.getProfileCandidates().add(profileCandidate(
                "birth-year", "birth_year", "2004", "2004",
                "direct", false, true));
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
        verifyNoInteractions(workGuard);
    }

    @Test
    void finalBirthdayIsValidatedAgainstTheLockedProfile() {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.getProfileCandidates().add(profileCandidate(
                "birth-year", "birth_year", "2023", "2023",
                "direct", false, true));
        ActorProfile locked = profile(0, 0L);
        locked.setBirthYear(2004);
        locked.setBirthMonth(2);
        locked.setBirthDayOfMonth(29);
        locked.setBirthPrecision("day");
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        when(profileMapper.selectByUserIdForUpdate(7L)).thenReturn(locked);

        assertConflictBeforeWriter(request);
        verifyNoInteractions(workGuard);
    }

    @Test
    void nullProfileCandidateValueIsRejectedWithoutUnsafeFallback() {
        ProfileImportApplyReqDTO request = profileRequest(true);
        request.getProfileCandidates().get(0).setCandidateValue(null);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void profileFieldKeyCannotBeRebound() {
        ProfileImportApplyReqDTO request = profileRequest(true);
        request.getProfileCandidates().get(0).setFieldKey("public_name");
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void duplicateCandidateIdAcrossProfileAndWorkIsRejected() {
        ProfileImportApplyReqDTO request = profileRequest(true);
        request.getWorks().add(work("gender-1", "new", "create", null, List.of("create")));
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void duplicateProfileFieldKeyIsRejected() {
        ProfileImportApplyReqDTO request = profileRequest(true);
        request.getProfileCandidates().add(profileCandidate(
                "gender-2", "gender", "female", "female", "direct", false, true));
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @ParameterizedTest
    @MethodSource("allowedWorkActions")
    void strictWorkActionMatrixAllowsOnlySignedServerChoice(
            String matchStatus, String selectedAction, Long matchedExperienceId, List<String> allowedActions) {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.getWorks().add(work(
                "work-1", matchStatus, selectedAction, matchedExperienceId, allowedActions));
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        when(writer.applyImport(7L, request)).thenReturn("done");

        assertEquals("done", service.apply(7L, request).getSummary());

        verify(writer).applyImport(7L, request);
    }

    @Test
    void mergeAllowsUserFinalFieldsWhenEverySignedConflictIsConfirmed() {
        ProfileImportApplyReqDTO request = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork work = work(
                "work-1", "field_conflict", "merge", 91L, List.of("merge", "skip"));
        String originalProof = work.getProof();
        work.getFinalFields().setAchievementText("用户最终成绩");
        work.getFinalFields().setDescription("用户最终描述");
        request.getWorks().add(work);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        when(writer.applyImport(7L, request)).thenReturn("done");

        assertEquals("done", service.apply(7L, request).getSummary());
        assertEquals(originalProof, work.getProof());
        verify(writer).applyImport(7L, request);
    }

    @Test
    void createRejectsUnsignedUserFinalFields() {
        ProfileImportApplyReqDTO request = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork work =
                work("work-1", "new", "create", null, List.of("create"));
        work.setFinalFields(finalFields(work));
        work.getFinalFields().setDescription("用户编辑后的新作品描述");
        request.getWorks().add(work);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        assertConflictBeforeWriter(request);
    }

    @Test
    void mergeRequiresFinalFields() {
        ProfileImportApplyReqDTO request = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork work = work(
                "work-1", "field_conflict", "merge", 91L, List.of("merge", "skip"));
        work.setFinalFields(null);
        request.getWorks().add(work);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void mergeRejectsDuplicateOrIncompleteConflictConfirmations() {
        ProfileImportApplyReqDTO duplicateRequest = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork duplicate = work(
                "work-1", "field_conflict", "merge", 91L, List.of("merge", "skip"));
        duplicate.setConfirmedConflictFields(List.of("achievementText", "achievementText", "description"));
        duplicateRequest.getWorks().add(duplicate);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        assertConflictBeforeWriter(duplicateRequest);

        reset(writer);
        ProfileImportApplyReqDTO incompleteRequest = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork incomplete = work(
                "work-2", "field_conflict", "merge", 91L, List.of("merge", "skip"));
        incomplete.setConfirmedConflictFields(List.of("achievementText"));
        incompleteRequest.getWorks().add(incomplete);
        assertConflictBeforeWriter(incompleteRequest);
    }

    @Test
    void skipRejectsFinalFieldsOrConflictConfirmations() {
        ProfileImportApplyReqDTO finalRequest = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork withFinal = work(
                "work-1", "field_conflict", "skip", 91L, List.of("merge", "skip"));
        withFinal.setFinalFields(finalFields(withFinal));
        finalRequest.getWorks().add(withFinal);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        assertConflictBeforeWriter(finalRequest);

        reset(writer);
        ProfileImportApplyReqDTO confirmationRequest = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork withConfirmation = work(
                "work-2", "field_conflict", "skip", 91L, List.of("merge", "skip"));
        withConfirmation.setConfirmedConflictFields(List.of("achievementText", "description"));
        confirmationRequest.getWorks().add(withConfirmation);
        assertConflictBeforeWriter(confirmationRequest);
    }

    @Test
    void createRejectsConflictConfirmations() {
        ProfileImportApplyReqDTO request = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork work =
                work("work-1", "new", "create", null, List.of("create"));
        work.setConfirmedConflictFields(List.of("description"));
        request.getWorks().add(work);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @ParameterizedTest
    @MethodSource("illegalWorkActions")
    void illegalWorkActionMatrixIsRejectedBeforeWriter(
            String matchStatus, String selectedAction, Long matchedExperienceId, List<String> allowedActions) {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.getWorks().add(work(
                "work-1", matchStatus, selectedAction, matchedExperienceId, allowedActions));
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void createProofCannotBeChangedToMerge() {
        ProfileImportApplyReqDTO request = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork work =
                work("work-1", "new", "create", null, List.of("create"));
        work.setMatchStatus("field_conflict");
        work.setSelectedAction("merge");
        work.setMatchedExperienceId(91L);
        work.setAllowedActions(List.of("merge", "skip"));
        work.setConflictFields(List.of("achievementText", "description"));
        work.setFinalFields(finalFields(work));
        work.setConfirmedConflictFields(List.of("achievementText", "description"));
        request.getWorks().add(work);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void workMatchTargetCannotBeRebound() {
        ProfileImportApplyReqDTO request = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork work = work(
                "work-1", "field_conflict", "merge", 91L, List.of("merge", "skip"));
        work.setMatchedExperienceId(92L);
        request.getWorks().add(work);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void signedAllowedActionsCannotBeRebound() {
        ProfileImportApplyReqDTO request = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork work = work(
                "work-1", "field_conflict", "skip", 91L, List.of("merge", "skip"));
        work.setAllowedActions(List.of("skip"));
        request.getWorks().add(work);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void signedConflictFieldsCannotBeRebound() {
        ProfileImportApplyReqDTO request = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork work = work(
                "work-1", "field_conflict", "merge", 91L, List.of("merge", "skip"));
        work.setConflictFields(List.of("description"));
        work.setConfirmedConflictFields(List.of("description"));
        request.getWorks().add(work);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void tamperedWorkCandidateIsRejectedBeforeWriter() {
        ProfileImportApplyReqDTO request = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork work =
                work("work-1", "new", "create", null, List.of("create"));
        work.setProjectName("篡改作品");
        request.getWorks().add(work);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void nullCollaboratorElementReturnsStableConflictInsteadOfServerError() {
        ProfileImportApplyReqDTO request = emptyRequest();
        ProfileImportApplyReqDTO.ConfirmedWork work =
                work("work-1", "new", "create", null, List.of("create"));
        work.setCollaborators(java.util.Collections.singletonList(null));
        request.getWorks().add(work);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertConflictBeforeWriter(request);
    }

    @Test
    void proofIssuedForAnotherUserCannotBeReusedWithTheSameRequestId() {
        ProfileImportApplyReqDTO request = profileRequest(true);
        when(auditMapper.selectForUpdate(8L, "req-1")).thenReturn(extractedAudit());
        when(writer.applyImport(8L, request)).thenReturn("unsafe");

        BizException error = assertThrows(BizException.class, () -> service.apply(8L, request));

        assertEquals(46008, error.getCode());
        verifyNoInteractions(writer);
    }

    @Test
    void sameRequestAndPayloadReturnsStoredResultWithoutSecondWriterCall() {
        ProfileImportApplyReqDTO request = profileRequest(true);
        AiProfileImportRequestAudit audit = extractedAudit();
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(audit);
        when(writer.applyImport(7L, request)).thenReturn("done");

        var first = service.apply(7L, request);
        var second = service.apply(7L, request);

        assertEquals("done", first.getSummary());
        assertEquals(first.getSummary(), second.getSummary());
        verify(writer, times(1)).applyImport(7L, request);
    }

    @Test
    void sameRequestWithDifferentPayloadReturns46009() {
        ProfileImportApplyReqDTO first = profileRequest(true);
        AiProfileImportRequestAudit audit = extractedAudit();
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(audit);
        when(writer.applyImport(any(), any())).thenReturn("done");
        service.apply(7L, first);
        ProfileImportApplyReqDTO changed = profileRequest(true);
        changed.setProfileVersion(1L);

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, changed));

        assertEquals(46009, error.getCode());
    }

    @Test
    void versionConflictRejectsBeforeWriter() {
        ProfileImportApplyReqDTO request = profileRequest(true);
        request.setWorkLibraryVersion(2L);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertEquals(46010, assertThrows(BizException.class, () -> service.apply(7L, request)).getCode());
        verifyNoInteractions(writer);
    }

    @Test
    void validApplyUsesFixedLockAndWriteOrder() {
        ProfileImportApplyReqDTO request = emptyRequest();
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        when(profileMapper.selectByUserIdForUpdate(7L)).thenReturn(profile(0, 0L));
        when(writer.applyImport(7L, request)).thenReturn("done");

        service.apply(7L, request);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                auditMapper, userMapper, profileMapper, workGuard, writer);
        order.verify(auditMapper).selectForUpdate(7L, "req-1");
        order.verify(userMapper).selectActiveByIdForUpdate(7L);
        order.verify(profileMapper).selectByUserIdForUpdate(7L);
        order.verify(workGuard).validateAndLock(7L, request.getWorks());
        order.verify(writer).applyImport(7L, request);
        order.verify(auditMapper).updateById(any());
    }

    @Test
    void lockedDatabaseContextConflictRejectsBeforeGuardAndWriter() {
        ProfileImportApplyReqDTO request = emptyRequest();
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        when(profileMapper.selectByUserIdForUpdate(7L)).thenReturn(profile(1, 0L));

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, request));

        assertEquals(46010, error.getCode());
        verifyNoInteractions(workGuard, writer);
        verify(auditMapper, never()).updateById(any());
    }

    @Test
    void missingLockedUserFailsClosedBeforeBusinessValidation() {
        ProfileImportApplyReqDTO request = emptyRequest();
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        when(userMapper.selectActiveByIdForUpdate(7L)).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, request));

        assertEquals(46008, error.getCode());
        verify(profileMapper, never()).selectByUserIdForUpdate(any());
        verifyNoInteractions(workGuard, writer);
    }

    @Test
    void failedAuditResultUpdateFailsClosed() {
        ProfileImportApplyReqDTO request = emptyRequest();
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());
        when(profileMapper.selectByUserIdForUpdate(7L)).thenReturn(profile(0, 0L));
        when(writer.applyImport(7L, request)).thenReturn("done");
        when(auditMapper.updateById(any())).thenReturn(0);

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, request));

        assertEquals(46008, error.getCode());
        verify(writer).applyImport(7L, request);
    }

    private void assertConflictBeforeWriter(ProfileImportApplyReqDTO request) {
        BizException error = assertThrows(BizException.class, () -> service.apply(7L, request));
        assertEquals(46008, error.getCode());
        verifyNoInteractions(writer);
        verify(auditMapper, never()).updateById(any());
    }

    private ProfileImportApplyReqDTO profileRequest(boolean confirmed) {
        ProfileImportApplyReqDTO request = emptyRequest();
        request.getProfileCandidates().add(profileCandidate(
                "gender-1", "gender", "female", "female", "inferred_from_roles", true, confirmed));
        return request;
    }

    private ProfileImportApplyReqDTO emptyRequest() {
        ProfileImportApplyReqDTO request = new ProfileImportApplyReqDTO();
        request.setRequestId("req-1");
        request.setScene("full_profile");
        request.setProfileVersion(0L);
        request.setWorkLibraryVersion(0L);
        return request;
    }

    private ProfileImportApplyReqDTO.ConfirmedCandidate profileCandidate(
            String candidateId, String fieldKey, String candidateValue, String value,
            String sourceType, boolean requiresExplicitConfirmation, boolean confirmed) {
        ProfileImportApplyReqDTO.ConfirmedCandidate candidate =
                new ProfileImportApplyReqDTO.ConfirmedCandidate();
        candidate.setCandidateId(candidateId);
        candidate.setFieldKey(fieldKey);
        candidate.setCandidateValue(candidateValue);
        candidate.setValue(value);
        candidate.setSourceType(sourceType);
        candidate.setRequiresExplicitConfirmation(requiresExplicitConfirmation);
        candidate.setConfirmed(confirmed);
        candidate.setProof(proofs.issueProfile(
                7L, "req-1", candidateId, fieldKey, candidateValue, sourceType,
                requiresExplicitConfirmation));
        return candidate;
    }

    private ProfileImportApplyReqDTO.ConfirmedWork work(
            String candidateId, String matchStatus, String selectedAction,
            Long matchedExperienceId, List<String> allowedActions) {
        ProfileImportApplyReqDTO.ConfirmedWork work = new ProfileImportApplyReqDTO.ConfirmedWork();
        work.setCandidateId(candidateId);
        work.setSourceType("direct");
        work.setConfirmed(true);
        work.setMatchStatus(matchStatus);
        work.setSelectedAction(selectedAction);
        work.setMatchedExperienceId(matchedExperienceId);
        work.setAllowedActions(allowedActions);
        List<String> conflictFields = "field_conflict".equals(matchStatus)
                ? List.of("achievementText", "description") : List.of();
        work.setConflictFields(conflictFields);
        work.setConfirmedConflictFields(List.of());
        work.setProjectName("作品一");
        work.setRoleName("角色一");
        work.setCollaborators(List.of("甲", "乙"));
        if ("merge".equals(selectedAction)) {
            work.setFinalFields(finalFields(work));
            work.setConfirmedConflictFields(conflictFields);
        }
        work.setProof(proofs.issueWork(
                7L, "req-1", candidateId, work.proofValue(), work.getSourceType(), matchStatus,
                matchedExperienceId, allowedActions, conflictFields));
        return work;
    }

    private ProfileImportApplyReqDTO.WorkFields finalFields(ProfileImportApplyReqDTO.ConfirmedWork work) {
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

    private AiProfileImportRequestAudit extractedAudit() {
        AiProfileImportRequestAudit audit = new AiProfileImportRequestAudit();
        audit.setAuditId(1L);
        audit.setUserId(7L);
        audit.setRequestId("req-1");
        audit.setScene("full_profile");
        audit.setStatus("success");
        audit.setProfileVersion(0L);
        audit.setWorkLibraryVersion(0L);
        return audit;
    }

    private ActorProfile profile(int profileVersion, long workLibraryVersion) {
        ActorProfile profile = new ActorProfile();
        profile.setActorProfileId(9L);
        profile.setUserId(7L);
        profile.setVersion(profileVersion);
        profile.setWorkLibraryVersion(workLibraryVersion);
        profile.setDeleted(0);
        return profile;
    }

    private static Stream<Arguments> allowedWorkActions() {
        return Stream.of(
                Arguments.of("new", "create", null, List.of("create")),
                Arguments.of("exact_match", "skip", 91L, List.of("skip")),
                Arguments.of("field_conflict", "skip", 91L, List.of("merge", "skip")),
                Arguments.of("field_conflict", "merge", 91L, List.of("merge", "skip")),
                Arguments.of("ambiguous", "skip", null, List.of("skip")));
    }

    private static Stream<Arguments> illegalWorkActions() {
        return Stream.of(
                Arguments.of("new", "", null, List.of("create")),
                Arguments.of(null, "create", null, List.of("create")),
                Arguments.of("new", "merge", 91L, List.of("create")),
                Arguments.of("exact_match", "skip", null, List.of("skip")),
                Arguments.of("field_conflict", "merge", null, List.of("merge", "skip")),
                Arguments.of("field_conflict", "create", 91L, List.of("merge", "skip")),
                Arguments.of("ambiguous", "skip", 91L, List.of("skip")));
    }

    private static Stream<Arguments> invalidFinalProfileValues() {
        return Stream.of(
                Arguments.of("unknown_field", "模型值", "最终值"),
                Arguments.of("gender", "female", "nonbinary"),
                Arguments.of("height", "170", "999"),
                Arguments.of("height", "170", "-1"),
                Arguments.of("age", "21", "0"),
                Arguments.of("age", "21", "121"),
                Arguments.of("weight", "45", "19"),
                Arguments.of("weight", "45", "301"),
                Arguments.of("birth_year", "2004", "1899"),
                Arguments.of("birth_year", "2004", Integer.toString(java.time.Year.now().getValue() + 1)),
                Arguments.of("birth_month", "9", "13"),
                Arguments.of("birth_day", "16", "32"),
                Arguments.of("birth_precision", "month", "quarter"),
                Arguments.of("public_name", "王火火", "x".repeat(65)),
                Arguments.of("current_city", "杭州", "x".repeat(65)),
                Arguments.of("origin_place", "广东", "x".repeat(129)),
                Arguments.of("intro", "简介", "x".repeat(2001)));
    }

    private static Stream<Arguments> invalidFinalTagValues() {
        String tooMany = IntStream.range(0, 51)
                .mapToObj(index -> "\"标签" + index + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        return Stream.of(
                Arguments.of("[\"粤语\",1]"),
                Arguments.of(tooMany),
                Arguments.of("[\"" + "x".repeat(129) + "\"]"));
    }
}
