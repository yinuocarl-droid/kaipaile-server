package com.kaipai.service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.integration.ai.profileimport.DeepSeekProfileTextExtractor;
import com.kaipai.mapper.ai.AiProfileImportRequestAuditMapper;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.ai.dto.ProfileImportExtractReqDTO;
import com.kaipai.model.ai.dto.ProfileImportExtractionRespDTO;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import com.kaipai.model.ai.entity.AiProfileImportRequestAudit;
import com.kaipai.service.ai.ProfileImportConfigService;
import com.kaipai.service.ai.ProfileImportRateLimiter;
import com.kaipai.service.ai.ProfileImportRuntimeConfig;
import com.kaipai.service.ai.ProfileImportService;
import com.kaipai.service.ai.profileimport.ProfileImportCandidateProofService;
import com.kaipai.service.ai.profileimport.ProfileImportSchemaValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProfileImportServiceImpl implements ProfileImportService {
    private final ProfileImportConfigService configService;
    private final DeepSeekProfileTextExtractor extractor;
    private final AiProfileImportRequestAuditMapper auditMapper;
    private final ProfileImportRateLimiter rateLimiter;
    private final ProfileImportSchemaValidator validator;
    private final ProfileImportCandidateProofService proofs;
    private final ObjectMapper mapper;

    @Override
    public ProfileImportExtractionRespDTO extract(Long userId, ProfileImportExtractReqDTO request) {
        if (!configService.capability().isAvailable()) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_DISABLED.toException();
        }
        if (request == null || !StringUtils.hasText(request.getRawText())) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_INPUT_EMPTY.toException();
        }
        ProfileImportRuntimeConfig runtime = configService.runtimeConfig();
        if (request.getRawText().length() > runtime.maxInputChars()) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_INPUT_TOO_LONG.toException();
        }
        if (!rateLimiter.allow(userId, runtime.dailyLimit())) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_RATE_LIMITED.toException();
        }
        AiProfileImportConfig config = new AiProfileImportConfig();
        config.setConfigId(runtime.configId());
        config.setEndpoint(runtime.endpoint());
        config.setModelName(runtime.modelName());
        JsonNode root = extractor.extract(config, runtime.apiKey(), request.getRawText(), request.getRequestId());
        ProfileImportSchemaValidator.ValidatedExtraction extraction;
        try {
            extraction = validator.validate(root.toString());
        } catch (IllegalArgumentException error) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_RESPONSE_INVALID.toException();
        }
        ProfileImportExtractionRespDTO response = response(request.getRequestId(), extraction, root);
        saveAudit(userId, request, runtime, response);
        return response;
    }

    private ProfileImportExtractionRespDTO response(String requestId,
            ProfileImportSchemaValidator.ValidatedExtraction extraction, JsonNode root) {
        ProfileImportExtractionRespDTO response = new ProfileImportExtractionRespDTO();
        response.setRequestId(requestId);
        for (ProfileImportSchemaValidator.Candidate candidate : extraction.profileCandidates()) {
            ProfileImportExtractionRespDTO.ProfileCandidate item = new ProfileImportExtractionRespDTO.ProfileCandidate();
            item.setCandidateId(candidate.candidateId()); item.setFieldKey(candidate.fieldKey());
            item.setCandidateValue(candidate.value()); item.setSourceType(candidate.sourceType());
            item.setSelected(candidate.selected()); item.setConfirmed(candidate.confirmed());
            item.setRequiresExplicitConfirmation(candidate.requiresExplicitConfirmation());
            item.setCandidateProof(proofs.issue(requestId, candidate.candidateId(), candidate.value(),
                    candidate.sourceType(), candidate.requiresExplicitConfirmation()));
            response.getProfileCandidates().add(item);
        }
        for (ProfileImportSchemaValidator.Work work : extraction.workCandidates()) {
            ProfileImportExtractionRespDTO.WorkCandidate item = new ProfileImportExtractionRespDTO.WorkCandidate();
            item.setCandidateId(work.candidateId()); item.setProjectName(work.projectName());
            item.setRoleName(work.roleName()); item.setPublishStatus(work.publishStatus());
            item.setWorkTypeCode(work.workTypeCode()); item.setRoleLevelCode(work.roleLevelCode());
            item.setShootYear(work.shootYear()); item.setShootMonth(work.shootMonth()); item.setPlatform(work.platform());
            item.setSyncSoundStatus(work.syncSoundStatus()); item.setCollaborators(work.collaborators());
            item.setAchievementText(work.achievementText()); item.setDescription(work.description());
            item.setSourceType(work.sourceType()); item.setSelected(true);
            item.setCandidateProof(proofs.issue(requestId, work.candidateId(), work.proofValue(), work.sourceType(), false));
            response.getWorkCandidates().add(item);
        }
        response.setProfileCandidateCount(response.getProfileCandidates().size());
        response.setWorkCandidateCount(response.getWorkCandidates().size());
        response.setConflictCount(root.path("conflicts").size());
        response.setIgnoredMediaPlaceholderCount(extraction.ignoredMediaPlaceholderCount());
        return response;
    }

    private void saveAudit(Long userId, ProfileImportExtractReqDTO request, ProfileImportRuntimeConfig runtime,
            ProfileImportExtractionRespDTO response) {
        AiProfileImportRequestAudit audit = new AiProfileImportRequestAudit();
        audit.setRequestId(request.getRequestId()); audit.setUserId(userId); audit.setConfigId(runtime.configId());
        audit.setModelName(runtime.modelName()); audit.setInputLength(request.getRawText().length());
        audit.setStatus("success"); audit.setProfileVersion(request.getProfileVersion());
        audit.setWorkLibraryVersion(request.getWorkLibraryVersion());
        audit.setCandidateCount(response.getProfileCandidateCount()); audit.setWorkCount(response.getWorkCandidateCount());
        audit.setConflictCount(response.getConflictCount());
        auditMapper.insert(audit);
    }
}
