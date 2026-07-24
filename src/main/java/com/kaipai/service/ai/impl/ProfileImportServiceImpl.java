package com.kaipai.service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.profileimport.DeepSeekProfileTextExtractor;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.ai.AiProfileImportRequestAuditMapper;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.ai.dto.ProfileImportCapabilityRespDTO;
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
import com.kaipai.service.ai.profileimport.ProfileImportSceneGuard;
import com.kaipai.service.ai.profileimport.ProfileImportWorkMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProfileImportServiceImpl implements ProfileImportService {
    private static final Object UNREADABLE_PROFILE_VALUE = new Object();
    private final ProfileImportConfigService configService;
    private final DeepSeekProfileTextExtractor extractor;
    private final AiProfileImportRequestAuditMapper auditMapper;
    private final ProfileImportRateLimiter rateLimiter;
    private final ProfileImportSchemaValidator validator;
    private final ProfileImportWorkMatcher workMatcher;
    private final ActorProfileMapper profileMapper;
    private final ProfileImportCandidateProofService proofs;
    private final ObjectMapper mapper;

    @Override
    public ProfileImportExtractionRespDTO extract(Long userId, ProfileImportExtractReqDTO request) {
        validateRequestEnvelope(request);
        String requestId = request.getRequestId().trim();
        String scene = ProfileImportSceneGuard.requireSupported(request.getScene());
        ProfileImportCapabilityRespDTO capability;
        try {
            capability = configService.capability();
        } catch (RuntimeException error) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
        if (capability == null) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
        if (!capability.isEnabled()) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_DISABLED.toException();
        }
        if (!capability.isAvailable()) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
        if (!StringUtils.hasText(request.getRawText())) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_INPUT_EMPTY.toException();
        }
        ProfileImportRuntimeConfig runtime;
        try {
            runtime = configService.runtimeConfig();
        } catch (RuntimeException error) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
        if (runtime == null) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
        if (request.getRawText().length() > runtime.maxInputChars()) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_INPUT_TOO_LONG.toException();
        }
        long startedAt = System.nanoTime();
        if (!rateLimiter.allow(userId, runtime.dailyLimit())) {
            BizException error = ProfileDomainErrorCode.PROFILE_IMPORT_RATE_LIMITED.toException();
            saveFailureAuditBestEffort(
                    userId, requestId, scene, request.getRawText().length(), runtime,
                    new ProfileContext(0L, 0L, null), elapsedMillis(startedAt),
                    ProfileDomainErrorCode.PROFILE_IMPORT_RATE_LIMITED.errorCode());
            throw error;
        }
        ProfileContext context = new ProfileContext(0L, 0L, null);
        AiProfileImportConfig config = new AiProfileImportConfig();
        config.setConfigId(runtime.configId());
        config.setEndpoint(runtime.endpoint());
        config.setModelName(runtime.modelName());
        config.setConnectTimeoutMs(runtime.connectTimeoutMs());
        config.setReadTimeoutMs(runtime.readTimeoutMs());
        config.setMaxOutputTokens(runtime.maxOutputTokens());
        try {
            context = profileContext(userId);
            JsonNode root = extractor.extract(config, runtime.apiKey(), request.getRawText(), requestId);
            ProfileImportSchemaValidator.ValidatedExtraction extraction;
            try {
                extraction = validator.validate(root.toString(), request.getRawText());
            } catch (IllegalArgumentException error) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_RESPONSE_INVALID.toException();
            }
            ProfileImportExtractionRespDTO response = response(
                    userId, requestId, scene, extraction, root, context.profile());
            response.setProfileVersion(context.profileVersion());
            response.setWorkLibraryVersion(context.workLibraryVersion());
            saveSuccessAudit(userId, requestId, scene, request.getRawText().length(), runtime, response,
                    elapsedMillis(startedAt));
            return response;
        } catch (BizException error) {
            saveFailureAuditBestEffort(
                    userId, requestId, scene, request.getRawText().length(), runtime, context,
                    elapsedMillis(startedAt), stableErrorCode(error));
            throw error;
        } catch (RuntimeException error) {
            BizException unavailable = ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
            saveFailureAuditBestEffort(
                    userId, requestId, scene, request.getRawText().length(), runtime, context,
                    elapsedMillis(startedAt), ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.errorCode());
            throw unavailable;
        }
    }

    private void validateRequestEnvelope(ProfileImportExtractReqDTO request) {
        if (request == null) throw new com.kaipai.common.exception.BizException("请求不能为空");
        if (!StringUtils.hasText(request.getRequestId())) {
            throw new com.kaipai.common.exception.BizException("requestId 不能为空");
        }
        if (request.getRequestId().trim().length() > 64) {
            throw new com.kaipai.common.exception.BizException("requestId 过长");
        }
    }

    private ProfileImportExtractionRespDTO response(Long userId, String requestId, String scene,
            ProfileImportSchemaValidator.ValidatedExtraction extraction, JsonNode root, ActorProfile currentProfile) {
        ProfileImportExtractionRespDTO response = new ProfileImportExtractionRespDTO();
        response.setRequestId(requestId);
        if ("full_profile".equals(scene)) {
            for (ProfileImportSchemaValidator.Candidate candidate : extraction.profileCandidates()) {
                ProfileImportExtractionRespDTO.ProfileCandidate item =
                        new ProfileImportExtractionRespDTO.ProfileCandidate();
                item.setCandidateId(candidate.candidateId()); item.setFieldKey(candidate.fieldKey());
                item.setCandidateValue(candidate.value()); item.setConfidence(candidate.confidence());
                item.setSourceText(candidate.sourceText()); item.setSourceType(candidate.sourceType());
                item.setWarning(candidate.warning());
                classifyProfileCandidate(item, candidate, currentProfile);
                item.setRequiresExplicitConfirmation(candidate.requiresExplicitConfirmation());
                item.setCandidateProof(proofs.issueProfile(
                        userId, requestId, candidate.candidateId(), candidate.fieldKey(), candidate.value(),
                        candidate.sourceType(), candidate.requiresExplicitConfirmation()));
                response.getProfileCandidates().add(item);
            }
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
            for (var entry : work.fields().entrySet()) {
                ProfileImportExtractionRespDTO.FieldEvidence evidence =
                        new ProfileImportExtractionRespDTO.FieldEvidence();
                evidence.setCandidateValue(entry.getValue().candidateValue());
                evidence.setConfidence(entry.getValue().confidence());
                evidence.setSourceText(entry.getValue().sourceText());
                evidence.setSourceType(entry.getValue().sourceType());
                evidence.setWarning(entry.getValue().warning());
                item.getFields().put(entry.getKey(), evidence);
            }
            response.getWorkCandidates().add(item);
        }
        workMatcher.match(userId, response.getWorkCandidates());
        for (ProfileImportExtractionRespDTO.WorkCandidate item : response.getWorkCandidates()) {
            item.setCandidateProof(proofs.issueWork(
                    userId, requestId, item.getCandidateId(), item.proofValue(), item.getSourceType(),
                    item.getMatchStatus(), item.getMatchedExperienceId(), item.getAllowedActions(),
                    item.getConflictFields()));
        }
        response.setProfileCandidateCount(response.getProfileCandidates().size());
        response.setWorkCandidateCount(response.getWorkCandidates().size());
        response.setConflictCount((int) response.getProfileCandidates().stream()
                .filter(candidate -> candidate.getConflict() != null).count()
                + response.getWorkCandidates().stream().mapToInt(work -> work.getConflicts().size()).sum());
        response.setIgnoredMediaPlaceholderCount(extraction.ignoredMediaPlaceholderCount());
        response.setUnmappedSegments(new ArrayList<>(extraction.unmappedSegments()));
        response.setWarnings(new ArrayList<>(extraction.warnings()));
        return response;
    }

    private void classifyProfileCandidate(ProfileImportExtractionRespDTO.ProfileCandidate item,
            ProfileImportSchemaValidator.Candidate candidate, ActorProfile currentProfile) {
        Object existing = currentProfileValue(currentProfile, candidate.fieldKey());
        Object normalizedCandidate = normalizedProfileValue(candidate.fieldKey(), candidate.value());
        boolean inferred = candidate.requiresExplicitConfirmation()
                || !Set.of("explicit", "direct").contains(candidate.sourceType());
        boolean unreadable = existing == UNREADABLE_PROFILE_VALUE;
        boolean empty = !unreadable && isEmpty(existing);
        boolean unchanged = !unreadable && !empty
                && Objects.equals(canonicalProfileValue(existing), canonicalProfileValue(normalizedCandidate));
        if (!unreadable && !empty && !unchanged) {
            ProfileImportExtractionRespDTO.Conflict conflict =
                    new ProfileImportExtractionRespDTO.Conflict();
            conflict.setFieldKey(candidate.fieldKey());
            conflict.setExistingValue(existing);
            conflict.setCandidateValue(normalizedCandidate);
            conflict.setSourceText(candidate.sourceText());
            item.setConflict(conflict);
        }

        item.setSelected(false);
        item.setConfirmed(false);
        if (unreadable) {
            item.setReviewStatus("unreadable");
            return;
        }
        if (inferred) {
            item.setReviewStatus("derived");
            return;
        }
        if (candidate.confidence() < 0.7d) {
            item.setReviewStatus("low_confidence");
            return;
        }
        if (empty) {
            item.setReviewStatus("available");
            item.setSelected(true);
            item.setConfirmed(true);
            return;
        }
        if (unchanged) {
            item.setReviewStatus("unchanged");
            item.setConfirmed(true);
            return;
        }
        item.setReviewStatus("conflict");
    }

    private void saveSuccessAudit(Long userId, String requestId, String scene, int inputLength,
            ProfileImportRuntimeConfig runtime, ProfileImportExtractionRespDTO response, long elapsedMs) {
        AiProfileImportRequestAudit audit = baseAudit(
                userId, requestId, scene, inputLength, runtime, response.getProfileVersion(),
                response.getWorkLibraryVersion(), elapsedMs);
        audit.setStatus("success");
        audit.setWorkLibraryVersion(response.getWorkLibraryVersion());
        audit.setCandidateCount(response.getProfileCandidateCount()); audit.setWorkCount(response.getWorkCandidateCount());
        audit.setConflictCount(response.getConflictCount());
        if (auditMapper.insert(audit) != 1) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
    }

    private void saveFailureAuditBestEffort(Long userId, String requestId, String scene, int inputLength,
            ProfileImportRuntimeConfig runtime, ProfileContext context, long elapsedMs, String errorCode) {
        try {
            AiProfileImportRequestAudit audit = baseAudit(
                    userId, requestId, scene, inputLength, runtime, context.profileVersion(),
                    context.workLibraryVersion(), elapsedMs);
            audit.setStatus("failed");
            audit.setErrorCode(errorCode);
            auditMapper.insert(audit);
        } catch (RuntimeException ignored) {
            // The original governed model/schema error remains authoritative.
        }
    }

    private AiProfileImportRequestAudit baseAudit(Long userId, String requestId, String scene,
            int inputLength, ProfileImportRuntimeConfig runtime, long profileVersion,
            long workLibraryVersion, long elapsedMs) {
        AiProfileImportRequestAudit audit = new AiProfileImportRequestAudit();
        audit.setRequestId(requestId);
        audit.setUserId(userId);
        audit.setConfigId(runtime.configId());
        audit.setModelName(runtime.modelName());
        audit.setScene(scene);
        audit.setInputLength(inputLength);
        audit.setCandidateCount(0);
        audit.setWorkCount(0);
        audit.setConflictCount(0);
        audit.setElapsedMs(elapsedMs);
        audit.setProfileVersion(profileVersion);
        audit.setWorkLibraryVersion(workLibraryVersion);
        return audit;
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private String stableErrorCode(BizException error) {
        for (ProfileDomainErrorCode candidate : ProfileDomainErrorCode.values()) {
            if (candidate.code() == error.getCode()) return candidate.errorCode();
        }
        return ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.errorCode();
    }

    private ProfileContext profileContext(Long userId) {
        List<ActorProfile> profiles = profileMapper.selectImportContextsByUserId(userId);
        if (profiles != null && profiles.size() > 1) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
        ActorProfile profile = profiles == null || profiles.isEmpty() ? null : profiles.get(0);
        if (profile == null) return new ProfileContext(0L, 0L, null);
        long profileVersion = profile.getVersion() == null ? 0L : profile.getVersion();
        long workLibraryVersion =
                profile.getWorkLibraryVersion() == null ? 0L : profile.getWorkLibraryVersion();
        return new ProfileContext(profileVersion, workLibraryVersion, profile);
    }

    private Object currentProfileValue(ActorProfile profile, String field) {
        if (profile == null) return null;
        return switch (field) {
            case "public_name" -> profile.getNickName();
            case "gender" -> switch (profile.getGender() == null ? 0 : profile.getGender()) {
                case 0 -> null;
                case 1 -> "male";
                case 2 -> "female";
                default -> UNREADABLE_PROFILE_VALUE;
            };
            case "age" -> profile.getAge();
            case "height" -> profile.getHeight();
            case "current_city" -> profile.getLocationCity();
            case "weight" -> profile.getWeight();
            case "origin_place" -> profile.getOriginPlace();
            case "school_name" -> profile.getSchoolName();
            case "major_name" -> profile.getMajorName();
            case "language_tags" -> jsonTags(profile.getLanguageTagsJson());
            case "specialty_tags" -> jsonTags(profile.getSpecialtyTagsJson());
            case "role_type_tags" -> jsonTags(profile.getRoleTypeTagsJson());
            case "professional_ability_tags" -> jsonTags(profile.getProfessionalAbilityTagsJson());
            case "intro" -> profile.getIntro();
            case "birth_year" -> profile.getBirthYear();
            case "birth_month" -> profile.getBirthMonth();
            case "birth_day" -> profile.getBirthDayOfMonth();
            case "birth_precision" -> profile.getBirthPrecision();
            default -> null;
        };
    }

    private Object normalizedProfileValue(String field, String value) {
        if (Set.of("age", "height", "weight", "birth_year", "birth_month", "birth_day")
                .contains(field)) return Integer.valueOf(value);
        if (Set.of("language_tags", "specialty_tags", "role_type_tags",
                "professional_ability_tags").contains(field)) return tagsFromCandidate(value);
        return value;
    }

    private List<String> tagsFromCandidate(String value) {
        try {
            JsonNode node = mapper.readTree(value);
            if (node != null && node.isArray()) {
                List<String> tags = new ArrayList<>();
                node.forEach(item -> { if (StringUtils.hasText(item.asText())) tags.add(item.asText().trim()); });
                return tags;
            }
        } catch (Exception ignored) {
            // Plain delimited model values remain supported during the migration window.
        }
        return List.of(value.split("[,，、]")).stream()
                .map(String::trim).filter(StringUtils::hasText).toList();
    }

    private Object jsonTags(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        try {
            JsonNode node = mapper.readTree(value);
            if (node == null || !node.isArray()) return UNREADABLE_PROFILE_VALUE;
            List<String> tags = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isTextual()) return UNREADABLE_PROFILE_VALUE;
                if (StringUtils.hasText(item.asText())) tags.add(item.asText());
            }
            return tags;
        } catch (Exception ignored) {
            return UNREADABLE_PROFILE_VALUE;
        }
    }

    private Object canonicalProfileValue(Object value) {
        if (value instanceof List<?> list) return new LinkedHashSet<>(list);
        return value instanceof String string ? string.trim() : value;
    }

    private boolean isEmpty(Object value) {
        return value == null || value instanceof String string && !StringUtils.hasText(string)
                || value instanceof List<?> list && list.isEmpty();
    }

    private record ProfileContext(long profileVersion, long workLibraryVersion, ActorProfile profile) {
    }
}
