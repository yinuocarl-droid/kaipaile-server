package com.kaipai.service.ai.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.profileimport.DeepSeekProfileTextExtractor;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.ai.AiProfileImportRequestAuditMapper;
import com.kaipai.model.ai.dto.ProfileImportCapabilityRespDTO;
import com.kaipai.model.ai.dto.ProfileImportExtractReqDTO;
import com.kaipai.model.ai.dto.ProfileImportExtractionRespDTO;
import com.kaipai.model.ai.entity.AiProfileImportRequestAudit;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.service.ai.ProfileImportConfigService;
import com.kaipai.service.ai.ProfileImportRateLimiter;
import com.kaipai.service.ai.ProfileImportRuntimeConfig;
import com.kaipai.service.ai.profileimport.ProfileImportCandidateProofService;
import com.kaipai.service.ai.profileimport.ProfileImportSchemaValidator;
import com.kaipai.service.ai.profileimport.ProfileImportWorkMatcher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProfileImportServiceImplTest {
    private ProfileImportConfigService config;
    private DeepSeekProfileTextExtractor extractor;
    private AiProfileImportRequestAuditMapper audit;
    private ProfileImportRateLimiter limiter;
    private ActorExperienceMapper experienceMapper;
    private ActorProfileMapper profileMapper;
    private ProfileImportCandidateProofService proofs;
    private ProfileImportServiceImpl service;

    @BeforeEach
    void setup() {
        config = mock(ProfileImportConfigService.class);
        extractor = mock(DeepSeekProfileTextExtractor.class);
        audit = mock(AiProfileImportRequestAuditMapper.class);
        limiter = mock(ProfileImportRateLimiter.class);
        experienceMapper = mock(ActorExperienceMapper.class);
        profileMapper = mock(ActorProfileMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        proofs = new ProfileImportCandidateProofService("secret");
        when(config.runtimeConfig()).thenReturn(new ProfileImportRuntimeConfig(
                3L, "https://api.deepseek.com/chat/completions", "deepseek-chat", "sk-memory",
                3000, 30000, 20000, 8000, 10));
        when(experienceMapper.selectList(any())).thenReturn(List.of());
        when(audit.insert(any())).thenReturn(1);
        service = new ProfileImportServiceImpl(config, extractor, audit, limiter,
                new ProfileImportSchemaValidator(),
                new ProfileImportWorkMatcher(
                        experienceMapper,
                        new com.kaipai.service.ai.profileimport.ProfileImportWorkMatchSupport(objectMapper)),
                profileMapper, proofs, objectMapper);
    }

    @Test
    void disabledCapabilityRejectsBeforeModelCall() {
        when(config.capability()).thenReturn(capability(false, false, "智能导入未启用"));

        BizException error = assertThrows(BizException.class, () -> service.extract(7L, request("文本")));

        assertEquals(46001, error.getCode());
        verify(config, never()).runtimeConfig();
        verifyNoInteractions(limiter, extractor);
        verify(audit, never()).insert(any());
    }

    @Test
    void enabledButUnavailableCapabilityReturns46002WithoutRuntimeOrSyntheticAudit() {
        when(config.capability()).thenReturn(
                capability(true, false, "智能导入配置未通过连接测试"));

        BizException error = assertThrows(
                BizException.class, () -> service.extract(7L, request("文本")));

        assertEquals(46002, error.getCode());
        verify(config, never()).runtimeConfig();
        verifyNoInteractions(limiter, extractor);
        verify(audit, never()).insert(any());
    }

    @Test
    void capabilityReadFailureReturns46002WithoutFabricatingAnInvocationAudit() {
        when(config.capability()).thenThrow(new IllegalStateException("configuration database unavailable"));

        BizException error = assertThrows(
                BizException.class, () -> service.extract(7L, request("文本")));

        assertEquals(46002, error.getCode());
        verify(config, never()).runtimeConfig();
        verifyNoInteractions(limiter, extractor);
        verify(audit, never()).insert(any());
    }

    @Test
    void runtimeConfigFailureAfterAvailableCapabilityReturns46002WithoutSyntheticAudit() {
        when(config.capability()).thenReturn(availableCapability());
        when(config.runtimeConfig()).thenThrow(
                com.kaipai.model.actor.dto.ProfileDomainErrorCode.PROFILE_IMPORT_DISABLED.toException());

        BizException error = assertThrows(
                BizException.class, () -> service.extract(7L, request("文本")));

        assertEquals(46002, error.getCode());
        verifyNoInteractions(limiter, extractor);
        verify(audit, never()).insert(any());
    }

    @Test
    void emptyAndTooLongInputsHaveStableErrors() {
        when(config.capability()).thenReturn(availableCapability());

        assertEquals(46003,
                assertThrows(BizException.class, () -> service.extract(7L, request(" "))).getCode());
        assertEquals(46004, assertThrows(BizException.class,
                () -> service.extract(7L, request("x".repeat(20001)))).getCode());
    }

    @Test
    void missingRequestIdAndUnsupportedSceneFailBeforeRateLimitOrModelCall() {
        ProfileImportExtractReqDTO missingRequestId = request("演员资料");
        missingRequestId.setRequestId(" ");
        ProfileImportExtractReqDTO unsupportedScene = request("演员资料");
        unsupportedScene.setScene("admin_override");

        assertEquals(400, assertThrows(
                BizException.class, () -> service.extract(7L, missingRequestId)).getCode());
        assertEquals(400, assertThrows(
                BizException.class, () -> service.extract(7L, unsupportedScene)).getCode());
        verifyNoInteractions(limiter, extractor);
        verify(audit, never()).insert(any());
    }

    @Test
    void dailyLimitRejectsBeforeModelCall() {
        when(config.capability()).thenReturn(availableCapability());
        when(limiter.allow(7L, 10)).thenReturn(false);

        BizException error = assertThrows(BizException.class, () -> service.extract(7L, request("文本")));

        assertEquals(46005, error.getCode());
        verifyNoInteractions(extractor);
        ArgumentCaptor<AiProfileImportRequestAudit> captor =
                ArgumentCaptor.forClass(AiProfileImportRequestAudit.class);
        verify(audit).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals("PROFILE_IMPORT_RATE_LIMITED", captor.getValue().getErrorCode());
        assertEquals("full_profile", captor.getValue().getScene());
        assertEquals(2, captor.getValue().getInputLength());
        assertTrue(captor.getValue().getElapsedMs() >= 0L);
    }

    @Test
    void duplicateActiveProfilesFailClosedBeforeModelCall() {
        when(config.capability()).thenReturn(availableCapability());
        when(limiter.allow(7L, 10)).thenReturn(true);
        when(profileMapper.selectImportContextsByUserId(7L))
                .thenReturn(List.of(currentProfile(), currentProfile()));

        BizException error = assertThrows(
                BizException.class, () -> service.extract(7L, request("演员资料")));

        assertEquals(46002, error.getCode());
        verifyNoInteractions(extractor);
        ArgumentCaptor<AiProfileImportRequestAudit> captor =
                ArgumentCaptor.forClass(AiProfileImportRequestAudit.class);
        verify(audit).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals("PROFILE_IMPORT_UNAVAILABLE", captor.getValue().getErrorCode());
        assertEquals(4, captor.getValue().getInputLength());
    }

    @Test
    void contextReadFailureWritesSanitizedAuditAndReturns46002() {
        String rawText = "敏感原文证据";
        when(config.capability()).thenReturn(availableCapability());
        when(limiter.allow(7L, 10)).thenReturn(true);
        when(profileMapper.selectImportContextsByUserId(7L))
                .thenThrow(new IllegalStateException("database failure with private detail"));

        BizException error = assertThrows(
                BizException.class, () -> service.extract(7L, request(rawText)));

        assertEquals(46002, error.getCode());
        verifyNoInteractions(extractor);
        ArgumentCaptor<AiProfileImportRequestAudit> captor =
                ArgumentCaptor.forClass(AiProfileImportRequestAudit.class);
        verify(audit).insert(captor.capture());
        AiProfileImportRequestAudit failed = captor.getValue();
        assertEquals("failed", failed.getStatus());
        assertEquals("PROFILE_IMPORT_UNAVAILABLE", failed.getErrorCode());
        assertEquals(rawText.length(), failed.getInputLength());
        assertEquals(0, failed.getCandidateCount());
        assertEquals(0, failed.getWorkCount());
        assertFalse(failed.toString().contains(rawText));
        assertFalse(failed.toString().contains("private detail"));
    }

    @Test
    void successUsesRuntimeConfigMatchesWorksAndWritesOnlySanitizedAudit() throws Exception {
        when(config.capability()).thenReturn(availableCapability());
        when(limiter.allow(7L, 10)).thenReturn(true);
        ProfileImportRuntimeConfig runtime = new ProfileImportRuntimeConfig(
                3L, "https://api.deepseek.com/chat/completions", "deepseek-chat", "sk-memory",
                3000, 30000, 20000, 8000, 10);
        when(config.runtimeConfig()).thenReturn(runtime);
        ActorProfile current = new ActorProfile();
        current.setUserId(7L);
        current.setVersion(3);
        current.setWorkLibraryVersion(5L);
        when(profileMapper.selectImportContextsByUserId(7L)).thenReturn(List.of(current));
        when(extractor.extract(any(), eq("sk-memory"), any(), eq("req-1"))).thenReturn(
                new ObjectMapper().readTree("""
                        {"profileCandidates":[{"candidateId":"name-1","fieldKey":"public_name","candidateValue":"王火火","confidence":0.99,"sourceText":"演员王火火","sourceType":"explicit"}],
                         "workCandidates":[
                           {"candidateId":"w1","projectName":"作品一","fields":{"projectName":{"candidateValue":"作品一","confidence":0.99,"sourceText":"作品一","sourceType":"explicit"}}},
                           {"candidateId":"w2","projectName":"作品二","fields":{"projectName":{"candidateValue":"作品二","confidence":0.99,"sourceText":"作品二","sourceType":"explicit"}}}
                         ]}
                        """));

        ProfileImportExtractReqDTO request = request("演员王火火，作品一，作品二");
        request.setProfileVersion(99L);
        request.setWorkLibraryVersion(88L);
        var response = service.extract(7L, request);

        assertEquals(3L, response.getProfileVersion());
        assertEquals(5L, response.getWorkLibraryVersion());
        assertEquals(1, response.getProfileCandidates().size());
        assertNotNull(response.getProfileCandidates().get(0).getCandidateProof());
        var profile = response.getProfileCandidates().get(0);
        assertTrue(proofs.verifyProfile(profile.getCandidateProof(), 7L, "req-1", profile.getCandidateId(),
                profile.getFieldKey(), profile.getCandidateValue(), profile.getSourceType(),
                profile.isRequiresExplicitConfirmation()));
        assertEquals(2, response.getWorkCandidates().size());
        assertEquals("new", response.getWorkCandidates().get(0).getMatchStatus());
        assertEquals("create", response.getWorkCandidates().get(0).getSelectedAction());
        assertEquals(List.of("create"), response.getWorkCandidates().get(0).getAllowedActions());
        assertEquals(List.of(), response.getWorkCandidates().get(0).getConflictFields());
        assertNotNull(response.getWorkCandidates().get(0).getCandidateProof());
        var work = response.getWorkCandidates().get(0);
        assertTrue(proofs.verifyWork(work.getCandidateProof(), 7L, "req-1", work.getCandidateId(),
                work.proofValue(), work.getSourceType(), work.getMatchStatus(),
                work.getMatchedExperienceId(), work.getAllowedActions(), work.getConflictFields()));

        ArgumentCaptor<AiProfileImportRequestAudit> captor =
                ArgumentCaptor.forClass(AiProfileImportRequestAudit.class);
        verify(audit).insert(captor.capture());
        assertEquals(13, captor.getValue().getInputLength());
        assertEquals(1, captor.getValue().getCandidateCount());
        assertEquals(2, captor.getValue().getWorkCount());
        assertEquals(3L, captor.getValue().getProfileVersion());
        assertEquals(5L, captor.getValue().getWorkLibraryVersion());
        assertEquals("full_profile", sceneOf(captor.getValue()));
    }

    @Test
    void worksOnlyExtractionOmitsProfileCandidatesAndAuditsZeroProfileCandidates() throws Exception {
        stubExtraction("""
                {
                  "profileCandidates": [
                    {"candidateId":"name-1","fieldKey":"public_name","candidateValue":"王火火","confidence":0.99,"sourceText":"演员王火火","sourceType":"explicit"}
                  ],
                  "workCandidates": [
                    {"candidateId":"work-1","projectName":"作品一","fields":{
                      "projectName":{"candidateValue":"作品一","confidence":0.99,"sourceText":"作品一","sourceType":"explicit"}
                    }}
                  ]
                }
                """);
        ProfileImportExtractReqDTO request = request("演员王火火，作品一");
        request.setScene("works_only");

        ProfileImportExtractionRespDTO response = service.extract(7L, request);

        assertTrue(response.getProfileCandidates().isEmpty());
        assertEquals(0, response.getProfileCandidateCount());
        assertEquals(1, response.getWorkCandidates().size());
        assertEquals(1, response.getWorkCandidateCount());
        ArgumentCaptor<AiProfileImportRequestAudit> captor =
                ArgumentCaptor.forClass(AiProfileImportRequestAudit.class);
        verify(audit).insert(captor.capture());
        assertEquals("works_only", sceneOf(captor.getValue()));
        assertEquals(0, captor.getValue().getCandidateCount());
        assertEquals(1, captor.getValue().getWorkCount());
    }

    @Test
    void modelTimeoutWritesSanitizedFailedAuditAndPreservesOriginalError() {
        when(config.capability()).thenReturn(availableCapability());
        when(limiter.allow(7L, 10)).thenReturn(true);
        when(extractor.extract(any(), eq("sk-memory"), any(), eq("req-1")))
                .thenThrow(new BizException(46006, "timeout with private source"));

        BizException error = assertThrows(
                BizException.class, () -> service.extract(7L, request("演员王火火")));

        assertEquals(46006, error.getCode());
        ArgumentCaptor<AiProfileImportRequestAudit> captor =
                ArgumentCaptor.forClass(AiProfileImportRequestAudit.class);
        verify(audit).insert(captor.capture());
        AiProfileImportRequestAudit failed = captor.getValue();
        assertEquals("failed", failed.getStatus());
        assertEquals("PROFILE_IMPORT_MODEL_TIMEOUT", failed.getErrorCode());
        assertEquals("full_profile", sceneOf(failed));
        assertEquals(5, failed.getInputLength());
        assertEquals(0, failed.getCandidateCount());
        assertEquals(0, failed.getWorkCount());
        assertEquals(0, failed.getConflictCount());
        assertTrue(failed.getElapsedMs() >= 0L);
    }

    @Test
    void invalidSchemaWritesFailedAuditWithStableErrorCode() throws Exception {
        when(config.capability()).thenReturn(availableCapability());
        when(limiter.allow(7L, 10)).thenReturn(true);
        when(extractor.extract(any(), eq("sk-memory"), any(), eq("req-1")))
                .thenReturn(new ObjectMapper().readTree("""
                        {"profileCandidates":[{"fieldKey":"unknown","candidateValue":"secret"}],
                         "workCandidates":[]}
                        """));

        BizException error = assertThrows(
                BizException.class, () -> service.extract(7L, request("演员王火火")));

        assertEquals(46007, error.getCode());
        ArgumentCaptor<AiProfileImportRequestAudit> captor =
                ArgumentCaptor.forClass(AiProfileImportRequestAudit.class);
        verify(audit).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals("PROFILE_IMPORT_RESPONSE_INVALID", captor.getValue().getErrorCode());
        assertEquals("full_profile", sceneOf(captor.getValue()));
    }

    @Test
    void failedAuditInsertNeverMasksOriginalModelError() {
        when(config.capability()).thenReturn(availableCapability());
        when(limiter.allow(7L, 10)).thenReturn(true);
        when(audit.insert(any())).thenReturn(0);
        when(extractor.extract(any(), eq("sk-memory"), any(), eq("req-1")))
                .thenThrow(new BizException(46006, "timeout"));

        BizException error = assertThrows(
                BizException.class, () -> service.extract(7L, request("演员王火火")));

        assertEquals(46006, error.getCode());
    }

    @Test
    void profileCandidatesAreClassifiedAgainstCurrentDatabaseValues() throws Exception {
        ActorProfile current = currentProfile();
        current.setNickName("王火火");
        current.setHeight(null);
        current.setOriginPlace("浙江");
        when(profileMapper.selectImportContextsByUserId(7L)).thenReturn(List.of(current));
        stubExtraction("""
                {
                  "profileCandidates": [
                    {"candidateId":"name-1","fieldKey":"public_name","candidateValue":"王火火","confidence":0.99,"sourceText":"演员王火火","sourceType":"explicit"},
                    {"candidateId":"height-1","fieldKey":"height","candidateValue":"170","confidence":0.99,"sourceText":"170cm","sourceType":"explicit"},
                    {"candidateId":"origin-1","fieldKey":"origin_place","candidateValue":"中国香港","confidence":0.99,"sourceText":"籍贯：中国香港","sourceType":"explicit"}
                  ],
                  "workCandidates": []
                }
                """);

        ProfileImportExtractionRespDTO response = service.extract(
                7L, request("演员王火火 170cm 籍贯：中国香港"));

        var unchanged = candidate(response, "public_name");
        assertEquals("unchanged", unchanged.getReviewStatus());
        assertFalse(unchanged.isSelected());
        assertNull(unchanged.getConflict());

        var available = candidate(response, "height");
        assertEquals("available", available.getReviewStatus());
        assertTrue(available.isSelected());
        assertNull(available.getConflict());

        var conflict = candidate(response, "origin_place");
        assertEquals("conflict", conflict.getReviewStatus());
        assertFalse(conflict.isSelected());
        assertEquals("浙江", conflict.getConflict().getExistingValue());
        assertEquals("中国香港", conflict.getConflict().getCandidateValue());
        assertEquals("籍贯：中国香港", conflict.getConflict().getSourceText());
        assertEquals(1, response.getConflictCount());
    }

    @Test
    void lowConfidenceAndDerivedCandidatesAreNotSelected() throws Exception {
        ActorProfile current = currentProfile();
        current.setHeight(180);
        current.setAge(99);
        when(profileMapper.selectImportContextsByUserId(7L)).thenReturn(List.of(current));
        stubExtraction("""
                {
                  "profileCandidates": [
                    {"candidateId":"height-1","fieldKey":"height","candidateValue":"170","confidence":0.45,"sourceText":"约170cm","sourceType":"explicit","warning":"原文为约数"},
                    {"candidateId":"birth-year-1","fieldKey":"birth_year","candidateValue":"2004","confidence":0.99,"sourceText":"生日：2004.9","sourceType":"explicit"},
                    {"candidateId":"birth-month-1","fieldKey":"birth_month","candidateValue":"9","confidence":0.99,"sourceText":"生日：2004.9","sourceType":"explicit"},
                    {"candidateId":"birth-precision-1","fieldKey":"birth_precision","candidateValue":"month","confidence":0.99,"sourceText":"生日：2004.9","sourceType":"explicit"}
                  ],
                  "workCandidates": []
                }
                """);

        ProfileImportExtractionRespDTO response = service.extract(
                7L, request("约170cm 生日：2004.9"));

        var lowConfidence = candidate(response, "height");
        assertEquals("low_confidence", lowConfidence.getReviewStatus());
        assertFalse(lowConfidence.isSelected());
        assertEquals(0.45d, lowConfidence.getConfidence());
        assertEquals("原文为约数", lowConfidence.getWarning());
        assertEquals(180, lowConfidence.getConflict().getExistingValue());
        assertEquals(170, lowConfidence.getConflict().getCandidateValue());
        assertEquals("约170cm", lowConfidence.getConflict().getSourceText());

        var derived = candidate(response, "age");
        assertEquals("derived", derived.getReviewStatus());
        assertFalse(derived.isSelected());
        assertEquals("根据部分生日动态推算", derived.getWarning());
        assertEquals(99, derived.getConflict().getExistingValue());
        assertEquals(Integer.valueOf(derived.getCandidateValue()), derived.getConflict().getCandidateValue());
        assertEquals("生日：2004.9", derived.getConflict().getSourceText());
        assertEquals(2, response.getConflictCount());
    }

    @Test
    void inferredGenderRetainsRiskStatusAndExistingValueConflict() throws Exception {
        ActorProfile current = currentProfile();
        current.setGender(1);
        when(profileMapper.selectImportContextsByUserId(7L)).thenReturn(List.of(current));
        stubExtraction("""
                {
                  "profileCandidates": [{
                    "candidateId":"gender-1","fieldKey":"gender","candidateValue":"female",
                    "confidence":0.86,"sourceText":"女主 / 女二","sourceType":"inferred_from_roles"
                  }],
                  "workCandidates": [
                    {"candidateId":"work-1","projectName":"作品一","roleLevelCode":"female_lead","fields":{
                      "projectName":{"candidateValue":"作品一","confidence":0.99,"sourceText":"《作品一》女主","sourceType":"explicit"},
                      "roleLevelCode":{"candidateValue":"female_lead","confidence":0.99,"sourceText":"女主","sourceType":"explicit"}
                    }},
                    {"candidateId":"work-2","projectName":"作品二","roleLevelCode":"female_supporting_2","fields":{
                      "projectName":{"candidateValue":"作品二","confidence":0.99,"sourceText":"《作品二》女二","sourceType":"explicit"},
                      "roleLevelCode":{"candidateValue":"female_supporting_2","confidence":0.99,"sourceText":"女二","sourceType":"explicit"}
                    }}
                  ]
                }
                """);

        ProfileImportExtractionRespDTO response = service.extract(
                7L, request("《作品一》女主\n《作品二》女二"));

        var gender = candidate(response, "gender");
        assertEquals("derived", gender.getReviewStatus());
        assertFalse(gender.isSelected());
        assertFalse(gender.isConfirmed());
        assertTrue(gender.isRequiresExplicitConfirmation());
        assertEquals("male", gender.getConflict().getExistingValue());
        assertEquals("female", gender.getConflict().getCandidateValue());
        assertEquals("女主 / 女二", gender.getConflict().getSourceText());
        assertEquals(1, response.getConflictCount());
    }

    @Test
    void unreadablePersistedValuesAreNeverTreatedAsEmptyOrMale() throws Exception {
        ActorProfile current = currentProfile();
        current.setGender(3);
        current.setLanguageTagsJson("not-json");
        when(profileMapper.selectImportContextsByUserId(7L)).thenReturn(List.of(current));
        stubExtraction("""
                {
                  "profileCandidates": [
                    {"candidateId":"gender-1","fieldKey":"gender","candidateValue":"male","confidence":0.99,"sourceText":"性别：男","sourceType":"explicit"},
                    {"candidateId":"languages-1","fieldKey":"language_tags","candidateValue":["粤语"],"confidence":0.99,"sourceText":"语言：粤语","sourceType":"explicit"}
                  ],
                  "workCandidates": []
                }
                """);

        ProfileImportExtractionRespDTO response = service.extract(
                7L, request("性别：男 语言：粤语"));

        assertEquals("unreadable", candidate(response, "gender").getReviewStatus());
        assertFalse(candidate(response, "gender").isSelected());
        assertNull(candidate(response, "gender").getConflict());
        assertEquals("unreadable", candidate(response, "language_tags").getReviewStatus());
        assertFalse(candidate(response, "language_tags").isSelected());
        assertNull(candidate(response, "language_tags").getConflict());
    }

    @Test
    void sanitizedAuditInsertFailureFailsClosed() throws Exception {
        when(config.capability()).thenReturn(availableCapability());
        when(limiter.allow(7L, 10)).thenReturn(true);
        when(audit.insert(any())).thenReturn(0);
        when(extractor.extract(any(), eq("sk-memory"), any(), eq("req-1")))
                .thenReturn(new ObjectMapper().readTree(
                        """
                        {"profileCandidates":[],"workCandidates":[]}
                        """));

        BizException error = assertThrows(
                BizException.class, () -> service.extract(7L, request("演员王火火")));

        assertEquals(46002, error.getCode());
    }

    private ProfileImportExtractReqDTO request(String text) {
        ProfileImportExtractReqDTO request = new ProfileImportExtractReqDTO();
        request.setRequestId("req-1");
        request.setRawText(text);
        request.setScene("full_profile");
        return request;
    }

    private ActorProfile currentProfile() {
        ActorProfile profile = new ActorProfile();
        profile.setUserId(7L);
        profile.setVersion(3);
        profile.setWorkLibraryVersion(5L);
        return profile;
    }

    private void stubExtraction(String json) throws Exception {
        when(config.capability()).thenReturn(availableCapability());
        when(limiter.allow(7L, 10)).thenReturn(true);
        when(extractor.extract(any(), eq("sk-memory"), any(), eq("req-1")))
                .thenReturn(new ObjectMapper().readTree(json));
    }

    private ProfileImportCapabilityRespDTO availableCapability() {
        return capability(true, true, null);
    }

    private ProfileImportCapabilityRespDTO capability(
            boolean enabled, boolean available, String unavailableReason) {
        return new ProfileImportCapabilityRespDTO(
                enabled, available, "deepseek", "deepseek-chat", 20000, unavailableReason);
    }

    private ProfileImportExtractionRespDTO.ProfileCandidate candidate(
            ProfileImportExtractionRespDTO response, String fieldKey) {
        return response.getProfileCandidates().stream()
                .filter(item -> fieldKey.equals(item.getFieldKey()))
                .findFirst()
                .orElseThrow();
    }

    private String sceneOf(AiProfileImportRequestAudit value) {
        try {
            return (String) value.getClass().getMethod("getScene").invoke(value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("request audit scene is missing", error);
        }
    }
}
