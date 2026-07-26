package com.kaipai.service.ai.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.profileimport.DeepSeekProfileTextExtractor;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.ai.dto.ProfileImportPromptTestResultRespDTO;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import com.kaipai.service.ai.ProfileImportPromptTester;
import com.kaipai.service.ai.ProfileImportRuntimeConfig;
import com.kaipai.service.ai.profileimport.ProfileImportPromptFixtureCatalog;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRenderer;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRuntime;
import com.kaipai.service.ai.profileimport.ProfileImportSchemaValidator;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

class ProfileImportPromptTesterImplTest {

    private static final String FULL_FIXTURE =
            "艺名林晓禾，女，1998年5月出生，身高168cm，现居杭州，籍贯浙江宁波。\n"
                    + "毕业于东海艺术学院表演专业，会普通话和舞蹈。\n"
                    + "2024年参演短剧《夏日回声》，饰演苏晴，已播，原声拍摄。\n"
                    + "2025年参演短剧《长街灯火》，饰演周宁，待播。\n"
                    + "[图片]";
    private static final String WORKS_FIXTURE =
            "2023年参演短剧《纸上星光》，饰演许安，已播，原声拍摄。\n"
                    + "2024年参演微电影《下一站》，饰演顾言，平台为星河视频。\n"
                    + "[视频]";

    @Test
    void fullProfileUsesTheGovernedRuntimeAndFixedFixtureAndReturnsOnlyBoundCounts()
            throws Exception {
        DeepSeekProfileTextExtractor extractor = mock(DeepSeekProfileTextExtractor.class);
        ProfileImportPromptRenderer renderer = mock(ProfileImportPromptRenderer.class);
        AiProfileImportPromptTemplate template = template("full_profile");
        AiProfileImportPromptVersion version = version();
        ProfileImportPromptRuntime promptRuntime = promptRuntime("full_profile");
        when(renderer.contentSha256(template, version)).thenReturn("content-hash");
        when(renderer.render(template, version)).thenReturn(promptRuntime);
        when(extractor.extract(any(), eq("sk-memory-only"), same(promptRuntime),
                anyString(), anyString())).thenReturn(json(fullProfileResponse()));
        ProfileImportPromptTester tester = new ProfileImportPromptTesterImpl(
                extractor,
                renderer,
                new ProfileImportSchemaValidator(),
                new ProfileImportPromptFixtureCatalog(new DefaultResourceLoader()));

        ProfileImportPromptTestResultRespDTO result =
                tester.execute(template, version, runtime());

        ArgumentCaptor<AiProfileImportConfig> config =
                ArgumentCaptor.forClass(AiProfileImportConfig.class);
        ArgumentCaptor<String> fixture = ArgumentCaptor.forClass(String.class);
        verify(extractor).extract(
                config.capture(),
                eq("sk-memory-only"),
                same(promptRuntime),
                fixture.capture(),
                anyString());
        assertEquals(FULL_FIXTURE, fixture.getValue());
        assertEquals(3L, config.getValue().getConfigId());
        assertEquals("deepseek-chat", config.getValue().getModelName());
        assertEquals(101L, result.getPromptVersionId());
        assertEquals("content-hash", result.getContentSha256());
        assertEquals("runtime-hash", result.getRuntimeSha256());
        assertEquals("full-profile-v1", result.getFixtureCode());
        assertEquals("1", result.getFixtureVersion());
        assertEquals(framedFixtureHash("full-profile-v1", FULL_FIXTURE),
                result.getFixtureSha256());
        assertEquals("deepseek-chat", result.getModelName());
        assertEquals(17, result.getConfigVersion());
        assertEquals("success", result.getStatus());
        assertEquals(1, result.getCandidateCount());
        assertEquals(1, result.getWorkCount());
        assertTrue(result.getElapsedMs() >= 0L);
        assertNull(result.getErrorCode());
        assertNull(result.getTestedBy());
        assertNull(result.getTestedAt());
        String persistedShape = result.toString();
        assertFalse(persistedShape.contains(FULL_FIXTURE));
        assertFalse(persistedShape.contains("governed system secret"));
        assertFalse(persistedShape.contains("sk-memory-only"));
        assertFalse(persistedShape.contains(fullProfileResponse()));
    }

    @Test
    void worksOnlyProfileCandidateBecomesASanitizedStableSchemaFailure() throws Exception {
        DeepSeekProfileTextExtractor extractor = mock(DeepSeekProfileTextExtractor.class);
        ProfileImportPromptRenderer renderer = mock(ProfileImportPromptRenderer.class);
        AiProfileImportPromptTemplate template = template("works_only");
        AiProfileImportPromptVersion version = version();
        ProfileImportPromptRuntime promptRuntime = promptRuntime("works_only");
        when(renderer.contentSha256(template, version)).thenReturn("content-hash");
        when(renderer.render(template, version)).thenReturn(promptRuntime);
        when(extractor.extract(any(), anyString(), same(promptRuntime), anyString(), anyString()))
                .thenReturn(json(worksOnlyViolationResponse()));
        ProfileImportPromptTester tester = new ProfileImportPromptTesterImpl(
                extractor,
                renderer,
                new ProfileImportSchemaValidator(),
                new ProfileImportPromptFixtureCatalog(new DefaultResourceLoader()));

        ProfileImportPromptTestResultRespDTO result =
                tester.execute(template, version, runtime());

        assertEquals(WORKS_FIXTURE, capturedFixture(extractor, promptRuntime));
        assertEquals("failed", result.getStatus());
        assertEquals(ProfileDomainErrorCode.PROFILE_IMPORT_RESPONSE_INVALID.errorCode(),
                result.getErrorCode());
        assertEquals(0, result.getCandidateCount());
        assertEquals(0, result.getWorkCount());
        assertFalse(result.toString().contains("林晓禾"));
    }

    @Test
    void fullProfileWithoutProfileCandidatesIsAStableSchemaFailure() throws Exception {
        assertInvalidSceneOutcome("full_profile", fullProfileWorkOnlyResponse());
    }

    @Test
    void fullProfileWithoutWorkCandidatesIsAStableSchemaFailure() throws Exception {
        assertInvalidSceneOutcome("full_profile", profileOnlyResponse());
    }

    @Test
    void worksOnlyWithoutWorkCandidatesIsAStableSchemaFailure() throws Exception {
        assertInvalidSceneOutcome("works_only", emptyResponse());
    }

    @Test
    void worksOnlyWithAWorkCandidateSucceedsWithoutProfileCandidates() throws Exception {
        ProfileImportPromptTestResultRespDTO result = executeResponse(
                "works_only", worksOnlySuccessResponse());

        assertEquals("success", result.getStatus());
        assertEquals(0, result.getCandidateCount());
        assertEquals(1, result.getWorkCount());
        assertNull(result.getErrorCode());
    }

    @Test
    void providerTimeoutAndUnexpectedProviderFailuresExposeOnlyStableErrorCodes()
            throws Exception {
        assertStableFailure(
                new BizException(46006, "timeout with raw provider body and sk-private"),
                ProfileDomainErrorCode.PROFILE_IMPORT_MODEL_TIMEOUT.errorCode());
        assertStableFailure(
                new IllegalStateException("provider response and governed prompt secret"),
                ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.errorCode());
    }

    @Test
    void unrelatedBusinessFailuresAreSanitizedAsProfileImportUnavailable()
            throws Exception {
        assertStableFailure(
                ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.toException(),
                ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.errorCode());
    }

    @Test
    void fixtureHashNormalizesCrLfAndBareCrAndRemovesExactlyOneTerminalLf()
            throws Exception {
        String lf = fixtureHashFromResource("line one\nline two\n");
        String crlf = fixtureHashFromResource("line one\r\nline two\r\n");
        String bareCr = fixtureHashFromResource("line one\rline two\r");
        assertEquals(lf, crlf);
        assertEquals(lf, bareCr);
        assertEquals(fixtureHashFromResource("line one"),
                fixtureHashFromResource("line one\n"));
        assertNotEquals(fixtureHashFromResource("line one\n"),
                fixtureHashFromResource("line one\n\n"));
    }

    @Test
    void fixtureCatalogReturnsSafeIdentityWithoutRenderingTheBody() throws Exception {
        ResourceLoader resources = mock(ResourceLoader.class);
        when(resources.getResource(anyString())).thenReturn(new ByteArrayResource(
                "fixture body secret\n".getBytes(StandardCharsets.UTF_8)));

        ProfileImportPromptFixtureCatalog.Fixture fixture =
                new ProfileImportPromptFixtureCatalog(resources).load("full_profile");

        assertEquals("full-profile-v1", fixture.code());
        assertEquals("1", fixture.version());
        assertEquals("fixture body secret", fixture.body());
        assertFalse(fixture.toString().contains("fixture body secret"));
        assertTrue(fixture.toString().contains(fixture.sha256()));
    }

    @Test
    void testerContractAndDependenciesExcludeOrdinaryUserSideEffects() throws Exception {
        Method[] methods = ProfileImportPromptTester.class.getDeclaredMethods();
        assertEquals(1, methods.length);
        Method execute = methods[0];
        assertEquals("execute", execute.getName());
        assertEquals(ProfileImportPromptTestResultRespDTO.class, execute.getReturnType());
        assertArrayEquals(
                new Class<?>[] {
                    AiProfileImportPromptTemplate.class,
                    AiProfileImportPromptVersion.class,
                    ProfileImportRuntimeConfig.class
                },
                execute.getParameterTypes());

        Set<Class<?>> dependencies = Arrays.stream(
                        ProfileImportPromptTesterImpl.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(java.lang.reflect.Field::getType)
                .collect(Collectors.toSet());
        assertEquals(
                Set.of(
                        DeepSeekProfileTextExtractor.class,
                        ProfileImportPromptRenderer.class,
                        ProfileImportSchemaValidator.class,
                        ProfileImportPromptFixtureCatalog.class),
                dependencies);
    }

    private void assertStableFailure(RuntimeException failure, String expectedErrorCode)
            throws Exception {
        DeepSeekProfileTextExtractor extractor = mock(DeepSeekProfileTextExtractor.class);
        ProfileImportPromptRenderer renderer = mock(ProfileImportPromptRenderer.class);
        AiProfileImportPromptTemplate template = template("full_profile");
        AiProfileImportPromptVersion version = version();
        ProfileImportPromptRuntime promptRuntime = promptRuntime("full_profile");
        when(renderer.contentSha256(template, version)).thenReturn("content-hash");
        when(renderer.render(template, version)).thenReturn(promptRuntime);
        when(extractor.extract(any(), anyString(), same(promptRuntime), anyString(), anyString()))
                .thenThrow(failure);
        ProfileImportPromptTester tester = new ProfileImportPromptTesterImpl(
                extractor,
                renderer,
                new ProfileImportSchemaValidator(),
                new ProfileImportPromptFixtureCatalog(new DefaultResourceLoader()));

        ProfileImportPromptTestResultRespDTO result =
                tester.execute(template, version, runtime());

        assertEquals("failed", result.getStatus());
        assertEquals(expectedErrorCode, result.getErrorCode());
        assertEquals(0, result.getCandidateCount());
        assertEquals(0, result.getWorkCount());
        assertFalse(result.toString().contains(failure.getMessage()));
    }

    private void assertInvalidSceneOutcome(String scene, String response) throws Exception {
        ProfileImportPromptTestResultRespDTO result = executeResponse(scene, response);

        assertEquals("failed", result.getStatus());
        assertEquals(ProfileDomainErrorCode.PROFILE_IMPORT_RESPONSE_INVALID.errorCode(),
                result.getErrorCode());
        assertEquals(0, result.getCandidateCount());
        assertEquals(0, result.getWorkCount());
    }

    private ProfileImportPromptTestResultRespDTO executeResponse(String scene, String response)
            throws Exception {
        DeepSeekProfileTextExtractor extractor = mock(DeepSeekProfileTextExtractor.class);
        ProfileImportPromptRenderer renderer = mock(ProfileImportPromptRenderer.class);
        AiProfileImportPromptTemplate template = template(scene);
        AiProfileImportPromptVersion version = version();
        ProfileImportPromptRuntime promptRuntime = promptRuntime(scene);
        when(renderer.contentSha256(template, version)).thenReturn("content-hash");
        when(renderer.render(template, version)).thenReturn(promptRuntime);
        when(extractor.extract(any(), anyString(), same(promptRuntime), anyString(), anyString()))
                .thenReturn(json(response));
        ProfileImportPromptTester tester = new ProfileImportPromptTesterImpl(
                extractor,
                renderer,
                new ProfileImportSchemaValidator(),
                new ProfileImportPromptFixtureCatalog(new DefaultResourceLoader()));
        return tester.execute(template, version, runtime());
    }

    private String fixtureHashFromResource(String body) throws Exception {
        DeepSeekProfileTextExtractor extractor = mock(DeepSeekProfileTextExtractor.class);
        ProfileImportPromptRenderer renderer = mock(ProfileImportPromptRenderer.class);
        ResourceLoader resources = mock(ResourceLoader.class);
        AiProfileImportPromptTemplate template = template("full_profile");
        AiProfileImportPromptVersion version = version();
        ProfileImportPromptRuntime promptRuntime = promptRuntime("full_profile");
        when(resources.getResource(anyString())).thenReturn(
                new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8)));
        when(renderer.contentSha256(template, version)).thenReturn("content-hash");
        when(renderer.render(template, version)).thenReturn(promptRuntime);
        when(extractor.extract(any(), anyString(), same(promptRuntime), anyString(), anyString()))
                .thenReturn(json(emptyResponse()));
        ProfileImportPromptTester tester = new ProfileImportPromptTesterImpl(
                extractor,
                renderer,
                new ProfileImportSchemaValidator(),
                new ProfileImportPromptFixtureCatalog(resources));
        return tester.execute(template, version, runtime()).getFixtureSha256();
    }

    private String capturedFixture(
            DeepSeekProfileTextExtractor extractor,
            ProfileImportPromptRuntime promptRuntime) {
        ArgumentCaptor<String> fixture = ArgumentCaptor.forClass(String.class);
        verify(extractor).extract(
                any(), anyString(), same(promptRuntime), fixture.capture(), anyString());
        return fixture.getValue();
    }

    private AiProfileImportPromptTemplate template(String scene) {
        AiProfileImportPromptTemplate template = new AiProfileImportPromptTemplate();
        template.setTemplateId(11L);
        template.setTemplateCode(scene);
        template.setScene(scene);
        template.setDeleted(0);
        return template;
    }

    private AiProfileImportPromptVersion version() {
        AiProfileImportPromptVersion version = new AiProfileImportPromptVersion();
        version.setPromptVersionId(101L);
        version.setTemplateId(11L);
        version.setVersionNo(4);
        version.setContentSha256("content-hash");
        version.setSystemPromptBody("governed system body");
        version.setRepairPromptBody("governed repair body");
        version.setSchemaVersion("profile-import-json-v1");
        version.setContractVersion("profile-import-contract-v1");
        version.setLifecycleStatus("draft");
        version.setVersion(5);
        version.setDeleted(0);
        return version;
    }

    private ProfileImportPromptRuntime promptRuntime(String scene) {
        return new ProfileImportPromptRuntime(
                11L,
                scene,
                scene,
                101L,
                4,
                "profile-import-json-v1",
                "profile-import-contract-v1",
                "governed system secret",
                "governed repair secret",
                "runtime-hash");
    }

    private ProfileImportRuntimeConfig runtime() {
        return new ProfileImportRuntimeConfig(
                3L,
                17,
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat",
                "sk-memory-only",
                3000,
                30000,
                20000,
                8000,
                10);
    }

    private JsonNode json(String value) throws Exception {
        return new ObjectMapper().readTree(value);
    }

    private String emptyResponse() {
        return """
                {"profileCandidates":[],"workCandidates":[],
                 "ignoredMediaPlaceholderCount":0,"unmappedSegments":[],"warnings":[]}
                """;
    }

    private String fullProfileResponse() {
        return """
                {"profileCandidates":[{
                   "candidateId":"p1","fieldKey":"public_name","candidateValue":"林晓禾",
                   "confidence":0.99,"sourceText":"艺名林晓禾","sourceType":"explicit","warning":null
                 }],"workCandidates":[{
                   "candidateId":"w1","projectName":"夏日回声","sourceType":"explicit",
                   "fields":{"projectName":{"candidateValue":"夏日回声","confidence":0.99,
                     "sourceText":"《夏日回声》","sourceType":"explicit","warning":null}}
                 }],"ignoredMediaPlaceholderCount":1,"unmappedSegments":[],"warnings":[]}
                """;
    }

    private String profileOnlyResponse() {
        return """
                {"profileCandidates":[{
                   "candidateId":"p1","fieldKey":"public_name","candidateValue":"林晓禾",
                   "confidence":0.99,"sourceText":"艺名林晓禾","sourceType":"explicit","warning":null
                 }],"workCandidates":[],"ignoredMediaPlaceholderCount":1,
                 "unmappedSegments":[],"warnings":[]}
                """;
    }

    private String fullProfileWorkOnlyResponse() {
        return """
                {"profileCandidates":[],"workCandidates":[{
                   "candidateId":"w1","projectName":"夏日回声","sourceType":"explicit",
                   "fields":{"projectName":{"candidateValue":"夏日回声","confidence":0.99,
                     "sourceText":"《夏日回声》","sourceType":"explicit","warning":null}}
                 }],"ignoredMediaPlaceholderCount":1,"unmappedSegments":[],"warnings":[]}
                """;
    }

    private String worksOnlySuccessResponse() {
        return """
                {"profileCandidates":[],"workCandidates":[{
                   "candidateId":"w1","projectName":"纸上星光","sourceType":"explicit",
                   "fields":{"projectName":{"candidateValue":"纸上星光","confidence":0.99,
                     "sourceText":"《纸上星光》","sourceType":"explicit","warning":null}}
                 }],"ignoredMediaPlaceholderCount":1,"unmappedSegments":[],"warnings":[]}
                """;
    }

    private String worksOnlyViolationResponse() {
        return """
                {"profileCandidates":[{
                   "candidateId":"p1","fieldKey":"public_name","candidateValue":"林晓禾",
                   "confidence":0.99,"sourceText":"林晓禾","sourceType":"explicit","warning":null
                 }],"workCandidates":[],"ignoredMediaPlaceholderCount":1,
                 "unmappedSegments":[],"warnings":[]}
                """;
    }

    private String framedFixtureHash(String fixtureCode, String body) throws Exception {
        String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes)) {
            for (String field : List.of(
                    "profile-import-prompt-fixture-v1", fixtureCode, "1", normalized)) {
                byte[] value = field.getBytes(StandardCharsets.UTF_8);
                output.writeInt(value.length);
                output.write(value);
            }
            output.flush();
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }
    }
}
