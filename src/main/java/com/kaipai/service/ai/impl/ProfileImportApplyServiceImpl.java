package com.kaipai.service.ai.impl;

import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.ai.AiProfileImportRequestAuditMapper;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import com.kaipai.model.ai.dto.ProfileImportApplyRespDTO;
import com.kaipai.model.ai.entity.AiProfileImportRequestAudit;
import com.kaipai.service.ai.ProfileImportApplyService;
import com.kaipai.service.ai.ProfileImportWriter;
import com.kaipai.service.ai.profileimport.ProfileImportCandidateProofService;
import com.kaipai.service.ai.profileimport.ProfileImportPayloadHasher;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProfileImportApplyServiceImpl implements ProfileImportApplyService {
    private final AiProfileImportRequestAuditMapper auditMapper;
    private final ProfileImportCandidateProofService proofs;
    private final ProfileImportPayloadHasher payloadHasher;
    private final ProfileImportWriter writer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfileImportApplyRespDTO apply(Long userId, ProfileImportApplyReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getRequestId())) {
            throw new BizException("requestId 不能为空");
        }
        AiProfileImportRequestAudit audit = auditMapper.selectForUpdate(userId, request.getRequestId());
        if (audit == null || !"success".equals(audit.getStatus())) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.toException();
        }
        String payloadHash = payloadHasher.hash(request);
        if ("success".equals(audit.getApplyStatus())) {
            if (!payloadHash.equals(audit.getApplyPayloadSha256())) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_REQUEST_REUSED.toException();
            }
            return response(request.getRequestId(), audit.getApplyResultSummaryJson());
        }
        if (!Objects.equals(audit.getProfileVersion(), request.getProfileVersion())
                || !Objects.equals(audit.getWorkLibraryVersion(), request.getWorkLibraryVersion())) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
        }
        validateCandidates(request);
        String summary = writer.applyImport(userId, request);
        audit.setApplyPayloadSha256(payloadHash);
        audit.setApplyStatus("success");
        audit.setApplyResultSummaryJson(summary);
        audit.setAppliedAt(LocalDateTime.now());
        auditMapper.updateById(audit);
        return response(request.getRequestId(), summary);
    }

    private void validateCandidates(ProfileImportApplyReqDTO request) {
        for (ProfileImportApplyReqDTO.ConfirmedCandidate candidate : request.getProfileCandidates()) {
            if (candidate.isRequiresExplicitConfirmation() && !candidate.isConfirmed()) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_CONFIRMATION_REQUIRED.toException();
            }
            if (!proofs.verify(candidate.getProof(), request.getRequestId(), candidate.getCandidateId(),
                    candidate.getValue(), candidate.getSourceType(), candidate.isRequiresExplicitConfirmation())) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.toException();
            }
        }
        for (ProfileImportApplyReqDTO.ConfirmedWork work : request.getWorks()) {
            if (!work.isConfirmed()) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_CONFIRMATION_REQUIRED.toException();
            }
            if (!proofs.verify(work.getProof(), request.getRequestId(), work.getCandidateId(),
                    work.proofValue(), work.getSourceType(), false)) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.toException();
            }
        }
    }

    private ProfileImportApplyRespDTO response(String requestId, String summary) {
        ProfileImportApplyRespDTO response = new ProfileImportApplyRespDTO();
        response.setRequestId(requestId);
        response.setSummary(summary);
        return response;
    }
}
