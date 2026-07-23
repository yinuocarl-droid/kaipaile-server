package com.kaipai.service.ai.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.ai.AiProfileImportRequestAuditMapper;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import com.kaipai.model.ai.entity.AiProfileImportRequestAudit;
import com.kaipai.service.ai.ProfileImportWriter;
import com.kaipai.service.ai.profileimport.ProfileImportCandidateProofService;
import com.kaipai.service.ai.profileimport.ProfileImportPayloadHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfileImportApplyServiceImplTest {
    private AiProfileImportRequestAuditMapper auditMapper;
    private ProfileImportCandidateProofService proofs;
    private ProfileImportPayloadHasher hasher;
    private ProfileImportWriter writer;
    private ProfileImportApplyServiceImpl service;

    @BeforeEach
    void setUp() {
        auditMapper = mock(AiProfileImportRequestAuditMapper.class);
        proofs = new ProfileImportCandidateProofService("secret");
        hasher = new ProfileImportPayloadHasher(new ObjectMapper());
        writer = mock(ProfileImportWriter.class);
        service = new ProfileImportApplyServiceImpl(auditMapper, proofs, hasher, writer);
    }

    @Test
    void applyRejectsUnconfirmedInferredGender() {
        ProfileImportApplyReqDTO request = request(false);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, request));

        assertEquals(46011, error.getCode());
        verifyNoInteractions(writer);
    }

    @Test
    void sameRequestAndPayloadReturnsStoredResultWithoutSecondWriterCall() {
        ProfileImportApplyReqDTO request = request(true);
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
        ProfileImportApplyReqDTO first = request(true);
        AiProfileImportRequestAudit audit = extractedAudit();
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(audit);
        when(writer.applyImport(any(), any())).thenReturn("done");
        service.apply(7L, first);
        ProfileImportApplyReqDTO changed = request(true);
        changed.setScene("works_only");

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, changed));

        assertEquals(46009, error.getCode());
    }

    @Test
    void versionConflictRejectsBeforeWriter() {
        ProfileImportApplyReqDTO request = request(true);
        request.setWorkLibraryVersion(2L);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        assertEquals(46010, assertThrows(BizException.class, () -> service.apply(7L, request)).getCode());
        verifyNoInteractions(writer);
    }

    @Test
    void tamperedWorkCandidateIsRejectedBeforeWriter() {
        ProfileImportApplyReqDTO request = request(true);
        ProfileImportApplyReqDTO.ConfirmedWork work = new ProfileImportApplyReqDTO.ConfirmedWork();
        work.setCandidateId("work-1"); work.setSourceType("direct"); work.setConfirmed(true);
        work.setProjectName("原作品");
        work.setProof(proofs.issue("req-1", "work-1", work.proofValue(), "direct", false));
        work.setProjectName("篡改作品");
        request.getWorks().add(work);
        when(auditMapper.selectForUpdate(7L, "req-1")).thenReturn(extractedAudit());

        BizException error = assertThrows(BizException.class, () -> service.apply(7L, request));

        assertEquals(46008, error.getCode());
        verifyNoInteractions(writer);
    }

    private ProfileImportApplyReqDTO request(boolean confirmed) {
        ProfileImportApplyReqDTO request = new ProfileImportApplyReqDTO();
        request.setRequestId("req-1");
        request.setScene("full_profile");
        request.setProfileVersion(0L);
        request.setWorkLibraryVersion(0L);
        ProfileImportApplyReqDTO.ConfirmedCandidate gender = new ProfileImportApplyReqDTO.ConfirmedCandidate();
        gender.setCandidateId("gender-1");
        gender.setFieldKey("gender");
        gender.setValue("female");
        gender.setSourceType("inferred_from_roles");
        gender.setRequiresExplicitConfirmation(true);
        gender.setConfirmed(confirmed);
        gender.setProof(proofs.issue("req-1", "gender-1", "female", "inferred_from_roles", true));
        request.getProfileCandidates().add(gender);
        return request;
    }

    private AiProfileImportRequestAudit extractedAudit() {
        AiProfileImportRequestAudit audit = new AiProfileImportRequestAudit();
        audit.setAuditId(1L);
        audit.setUserId(7L);
        audit.setRequestId("req-1");
        audit.setStatus("success");
        audit.setProfileVersion(0L);
        audit.setWorkLibraryVersion(0L);
        return audit;
    }
}
