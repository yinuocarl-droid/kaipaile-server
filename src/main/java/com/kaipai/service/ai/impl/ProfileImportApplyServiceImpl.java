package com.kaipai.service.ai.impl;

import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.ai.AiProfileImportRequestAuditMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import com.kaipai.model.ai.dto.ProfileImportApplyRespDTO;
import com.kaipai.model.ai.entity.AiProfileImportRequestAudit;
import com.kaipai.model.user.entity.User;
import com.kaipai.service.ai.ProfileImportApplyService;
import com.kaipai.service.ai.ProfileImportWriter;
import com.kaipai.service.ai.profileimport.ProfileImportBirthdayGuard;
import com.kaipai.service.ai.profileimport.ProfileImportCandidateProofService;
import com.kaipai.service.ai.profileimport.ProfileImportPayloadHasher;
import com.kaipai.service.ai.profileimport.ProfileImportSchemaValidator;
import com.kaipai.service.ai.profileimport.ProfileImportSceneGuard;
import com.kaipai.service.ai.profileimport.ProfileImportWorkApplyGuard;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProfileImportApplyServiceImpl implements ProfileImportApplyService {
    private final AiProfileImportRequestAuditMapper auditMapper;
    private final UserMapper userMapper;
    private final ActorProfileMapper profileMapper;
    private final ProfileImportCandidateProofService proofs;
    private final ProfileImportPayloadHasher payloadHasher;
    private final ProfileImportSchemaValidator schemaValidator;
    private final ProfileImportWriter writer;
    private final ProfileImportWorkApplyGuard workGuard;

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
        String scene = ProfileImportSceneGuard.requireSupported(request.getScene());
        ProfileImportSceneGuard.requireMatches(audit.getScene(), scene);
        request.setScene(scene);
        if ("works_only".equals(scene)
                && request.getProfileCandidates() != null
                && !request.getProfileCandidates().isEmpty()) {
            throw new BizException(
                    ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.code(),
                    "作品导入场景不允许提交档案候选");
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
        User user = userMapper.selectActiveByIdForUpdate(userId);
        if (user == null || !Objects.equals(userId, user.getUserId())
                || !Objects.equals(0, user.getDeleted())) {
            throw applyConflict();
        }
        ActorProfile profile = profileMapper.selectByUserIdForUpdate(userId);
        validateCurrentContext(userId, profile, audit);
        validateCandidates(userId, request, profile);
        workGuard.validateAndLock(userId, request.getWorks());
        String summary = writer.applyImport(userId, request);
        audit.setApplyPayloadSha256(payloadHash);
        audit.setApplyStatus("success");
        audit.setApplyResultSummaryJson(summary);
        audit.setAppliedAt(LocalDateTime.now());
        if (auditMapper.updateById(audit) != 1) {
            throw applyConflict();
        }
        return response(request.getRequestId(), summary);
    }

    private void validateCurrentContext(
            Long userId, ActorProfile profile, AiProfileImportRequestAudit audit) {
        if (profile != null && (!Objects.equals(userId, profile.getUserId())
                || !Objects.equals(0, profile.getDeleted()))) {
            throw applyConflict();
        }
        long currentProfileVersion =
                profile == null || profile.getVersion() == null ? 0L : profile.getVersion();
        long currentWorkLibraryVersion = profile == null || profile.getWorkLibraryVersion() == null
                ? 0L : profile.getWorkLibraryVersion();
        if (!Objects.equals(audit.getProfileVersion(), currentProfileVersion)
                || !Objects.equals(audit.getWorkLibraryVersion(), currentWorkLibraryVersion)) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
        }
    }

    private void validateCandidates(
            Long userId, ProfileImportApplyReqDTO request, ActorProfile currentProfile) {
        List<ProfileImportApplyReqDTO.ConfirmedCandidate> profileCandidates = request.getProfileCandidates();
        List<ProfileImportApplyReqDTO.ConfirmedWork> works = request.getWorks();
        if (profileCandidates == null || works == null) {
            throw applyConflict();
        }

        Set<String> candidateIds = new HashSet<>();
        Set<String> profileFieldKeys = new HashSet<>();
        for (ProfileImportApplyReqDTO.ConfirmedCandidate candidate : profileCandidates) {
            if (candidate == null
                    || !StringUtils.hasText(candidate.getCandidateId())
                    || !candidateIds.add(candidate.getCandidateId())
                    || !StringUtils.hasText(candidate.getFieldKey())
                    || !profileFieldKeys.add(candidate.getFieldKey())
                    || candidate.getCandidateValue() == null) {
                throw applyConflict();
            }
        }
        for (ProfileImportApplyReqDTO.ConfirmedWork work : works) {
            if (work == null
                    || !StringUtils.hasText(work.getCandidateId())
                    || !candidateIds.add(work.getCandidateId())) {
                throw applyConflict();
            }
        }

        for (ProfileImportApplyReqDTO.ConfirmedCandidate candidate : profileCandidates) {
            if (!proofs.verifyProfile(
                    candidate.getProof(), userId, request.getRequestId(), candidate.getCandidateId(),
                    candidate.getFieldKey(), candidate.getCandidateValue(), candidate.getSourceType(),
                    candidate.isRequiresExplicitConfirmation())) {
                throw applyConflict();
            }
            try {
                schemaValidator.validateProfileFinalValue(candidate.getFieldKey(), candidate.getValue());
            } catch (IllegalArgumentException error) {
                throw applyConflict();
            }
            if (!candidate.isConfirmed()) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_CONFIRMATION_REQUIRED.toException();
            }
        }
        Map<String, String> finalProfileValues = new LinkedHashMap<>();
        for (ProfileImportApplyReqDTO.ConfirmedCandidate candidate : profileCandidates) {
            finalProfileValues.put(candidate.getFieldKey(), candidate.getValue());
        }
        ProfileImportBirthdayGuard.normalize(currentProfile, finalProfileValues);
        for (ProfileImportApplyReqDTO.ConfirmedWork work : works) {
            validateWorkAction(work);
            if (!proofs.verifyWork(
                    work.getProof(), userId, request.getRequestId(), work.getCandidateId(), work.proofValue(),
                    work.getSourceType(), work.getMatchStatus(), work.getMatchedExperienceId(),
                    work.getAllowedActions(), work.getConflictFields())) {
                throw applyConflict();
            }
            if (!work.isConfirmed()) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_CONFIRMATION_REQUIRED.toException();
            }
        }
    }

    private void validateWorkAction(ProfileImportApplyReqDTO.ConfirmedWork work) {
        String action = work.getSelectedAction();
        List<String> allowedActions = work.getAllowedActions();
        List<String> conflictFields = work.getConflictFields();
        List<String> confirmedConflictFields = work.getConfirmedConflictFields();
        if (!StringUtils.hasText(action)
                || !StringUtils.hasText(work.getMatchStatus())
                || allowedActions == null
                || !allowedActions.contains(action)
                || conflictFields == null
                || hasDuplicates(conflictFields)
                || confirmedConflictFields == null) {
            throw applyConflict();
        }

        boolean valid = switch (work.getMatchStatus()) {
            case "new" -> "create".equals(action)
                    && work.getMatchedExperienceId() == null
                    && List.of("create").equals(allowedActions)
                    && conflictFields.isEmpty();
            case "exact_match" -> "skip".equals(action)
                    && work.getMatchedExperienceId() != null
                    && List.of("skip").equals(allowedActions)
                    && conflictFields.isEmpty();
            case "field_conflict" -> Set.of("merge", "skip").contains(action)
                    && work.getMatchedExperienceId() != null
                    && List.of("merge", "skip").equals(allowedActions)
                    && !conflictFields.isEmpty();
            case "ambiguous" -> "skip".equals(action)
                    && work.getMatchedExperienceId() == null
                    && List.of("skip").equals(allowedActions)
                    && conflictFields.isEmpty();
            default -> false;
        };
        if (!valid || !validFinalFields(work, action, conflictFields, confirmedConflictFields)) {
            throw applyConflict();
        }
    }

    private boolean validFinalFields(ProfileImportApplyReqDTO.ConfirmedWork work, String action,
            List<String> conflictFields, List<String> confirmedConflictFields) {
        if ("skip".equals(action)) {
            return work.getFinalFields() == null && confirmedConflictFields.isEmpty();
        }
        if ("create".equals(action)) {
            return work.getFinalFields() == null && confirmedConflictFields.isEmpty();
        }
        return work.getFinalFields() != null
                && !hasDuplicates(confirmedConflictFields)
                && new HashSet<>(conflictFields).equals(new HashSet<>(confirmedConflictFields));
    }

    private boolean hasDuplicates(List<String> values) {
        return new HashSet<>(values).size() != values.size();
    }

    private BizException applyConflict() {
        return ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.toException();
    }

    private ProfileImportApplyRespDTO response(String requestId, String summary) {
        ProfileImportApplyRespDTO response = new ProfileImportApplyRespDTO();
        response.setRequestId(requestId);
        response.setSummary(summary);
        return response;
    }
}
