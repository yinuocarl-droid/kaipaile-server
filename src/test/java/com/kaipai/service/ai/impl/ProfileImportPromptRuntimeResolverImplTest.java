package com.kaipai.service.ai.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.ai.AiProfileImportPromptTemplateMapper;
import com.kaipai.mapper.ai.AiProfileImportPromptVersionMapper;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import com.kaipai.service.ai.ProfileImportPromptRuntimeResolver;
import com.kaipai.service.ai.profileimport.ProfileImportPromptContract;
import com.kaipai.service.ai.profileimport.ProfileImportPromptPolicy;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRenderer;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRuntime;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ProfileImportPromptRuntimeResolverImplTest {

    @Mock
    private AiProfileImportPromptTemplateMapper templateMapper;

    @Mock
    private AiProfileImportPromptVersionMapper versionMapper;

    private ProfileImportPromptRuntimeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ProfileImportPromptRuntimeResolverImpl(
                templateMapper, versionMapper, renderer());
    }

    @Test
    void resolvesReleasedVersionOwnedByTheSceneTemplate() {
        AiProfileImportPromptTemplate template = template(11L, 101L);
        AiProfileImportPromptVersion version = releasedVersion(template);
        when(templateMapper.selectByScene("full_profile")).thenReturn(template);
        when(versionMapper.selectOwned(11L, 101L)).thenReturn(version);

        ProfileImportPromptRuntime runtime = resolver.resolve("full_profile");

        assertEquals(11L, runtime.templateId());
        assertEquals(101L, runtime.promptVersionId());
        assertEquals("full_profile", runtime.scene());
        verify(templateMapper).selectByScene("full_profile");
        verify(versionMapper).selectOwned(11L, 101L);
        verifyNoMoreInteractions(templateMapper, versionMapper);
    }

    @ParameterizedTest
    @MethodSource("invalidRuntimeRows")
    void missingCrossOwnedNonReleasedDeletedDamagedOrUnsupportedRowsFailClosed(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version) {
        when(templateMapper.selectByScene("full_profile")).thenReturn(template);
        boolean readsVersion = readsVersion(template);
        if (readsVersion) {
            when(versionMapper.selectOwned(
                            template.getTemplateId(), template.getActiveVersionId()))
                    .thenReturn(version);
        }

        BizException error = assertThrows(
                BizException.class, () -> resolver.resolve("full_profile"));

        assertEquals(46002, error.getCode());
        verify(templateMapper).selectByScene("full_profile");
        if (readsVersion) {
            verify(versionMapper).selectOwned(
                    template.getTemplateId(), template.getActiveVersionId());
        }
        verifyNoMoreInteractions(templateMapper, versionMapper);
    }

    @Test
    void unsupportedSceneFailsClosedBeforeReadingRows() {
        BizException error = assertThrows(
                BizException.class, () -> resolver.resolve("unsupported"));

        assertEquals(46002, error.getCode());
        verifyNoInteractions(templateMapper, versionMapper);
    }

    @Test
    void templateReadFailureIsSanitizedWithoutReadingTheVersion() {
        String marker = "SENSITIVE_TEMPLATE_READ_MARKER";
        when(templateMapper.selectByScene("full_profile"))
                .thenThrow(new IllegalStateException(marker));

        BizException error = assertThrows(
                BizException.class, () -> resolver.resolve("full_profile"));

        assertUnavailableWithoutMarker(error, marker);
        verify(templateMapper).selectByScene("full_profile");
        verifyNoMoreInteractions(templateMapper, versionMapper);
    }

    @Test
    void versionReadFailureIsSanitizedAfterTheOwnedLookup() {
        String marker = "SENSITIVE_VERSION_READ_MARKER";
        AiProfileImportPromptTemplate template = template(11L, 101L);
        when(templateMapper.selectByScene("full_profile")).thenReturn(template);
        when(versionMapper.selectOwned(11L, 101L))
                .thenThrow(new IllegalStateException(marker));

        BizException error = assertThrows(
                BizException.class, () -> resolver.resolve("full_profile"));

        assertUnavailableWithoutMarker(error, marker);
        verify(templateMapper).selectByScene("full_profile");
        verify(versionMapper).selectOwned(11L, 101L);
        verifyNoMoreInteractions(templateMapper, versionMapper);
    }

    @Test
    void rendererFailureIsSanitizedAfterContentHashValidation() {
        String marker = "SENSITIVE_PROMPT_RENDER_MARKER";
        AiProfileImportPromptTemplate template = template(11L, 101L);
        AiProfileImportPromptVersion version = releasedVersion(template);
        ProfileImportPromptRenderer rendererMock = mock(ProfileImportPromptRenderer.class);
        ProfileImportPromptRuntimeResolver localResolver =
                new ProfileImportPromptRuntimeResolverImpl(
                        templateMapper, versionMapper, rendererMock);
        when(templateMapper.selectByScene("full_profile")).thenReturn(template);
        when(versionMapper.selectOwned(11L, 101L)).thenReturn(version);
        when(rendererMock.contentSha256(template, version))
                .thenReturn(version.getContentSha256());
        when(rendererMock.render(template, version))
                .thenThrow(new IllegalStateException(marker));

        BizException error = assertThrows(
                BizException.class, () -> localResolver.resolve("full_profile"));

        assertUnavailableWithoutMarker(error, marker);
        verify(templateMapper).selectByScene("full_profile");
        verify(versionMapper).selectOwned(11L, 101L);
        verify(rendererMock).contentSha256(template, version);
        verify(rendererMock).render(template, version);
        verifyNoMoreInteractions(templateMapper, versionMapper, rendererMock);
    }

    @Test
    void resolverHasNoCacheAndReadsBothRowsForEveryCall() {
        AiProfileImportPromptTemplate template = template(11L, 101L);
        AiProfileImportPromptVersion version = releasedVersion(template);
        when(templateMapper.selectByScene("full_profile")).thenReturn(template);
        when(versionMapper.selectOwned(11L, 101L)).thenReturn(version);

        resolver.resolve("full_profile");
        resolver.resolve("full_profile");

        verify(templateMapper, times(2)).selectByScene("full_profile");
        verify(versionMapper, times(2)).selectOwned(11L, 101L);
        verifyNoMoreInteractions(templateMapper, versionMapper);
    }

    private static Stream<Arguments> invalidRuntimeRows() {
        AiProfileImportPromptTemplate nullActive = template(11L, null);

        AiProfileImportPromptTemplate mismatchedScene = template(11L, 101L);
        mismatchedScene.setScene("works_only");

        AiProfileImportPromptTemplate deletedTemplate = template(11L, 101L);
        deletedTemplate.setDeleted(1);

        AiProfileImportPromptTemplate wrongOwnerTemplate = template(11L, 101L);
        AiProfileImportPromptVersion wrongOwner = releasedVersion(wrongOwnerTemplate);
        wrongOwner.setTemplateId(12L);

        AiProfileImportPromptTemplate draftTemplate = template(11L, 101L);
        AiProfileImportPromptVersion draft = releasedVersion(draftTemplate);
        draft.setLifecycleStatus("draft");

        AiProfileImportPromptTemplate abandonedTemplate = template(11L, 101L);
        AiProfileImportPromptVersion abandoned = releasedVersion(abandonedTemplate);
        abandoned.setLifecycleStatus("abandoned");

        AiProfileImportPromptTemplate deletedVersionTemplate = template(11L, 101L);
        AiProfileImportPromptVersion deletedVersion = releasedVersion(deletedVersionTemplate);
        deletedVersion.setDeleted(1);

        AiProfileImportPromptTemplate wrongVersionIdTemplate = template(11L, 101L);
        AiProfileImportPromptVersion wrongVersionId = releasedVersion(wrongVersionIdTemplate);
        wrongVersionId.setPromptVersionId(102L);

        AiProfileImportPromptTemplate damagedHashTemplate = template(11L, 101L);
        AiProfileImportPromptVersion damagedHash = releasedVersion(damagedHashTemplate);
        damagedHash.setContentSha256("0".repeat(64));

        AiProfileImportPromptTemplate unsupportedSchemaTemplate = template(11L, 101L);
        AiProfileImportPromptVersion unsupportedSchema =
                releasedVersion(unsupportedSchemaTemplate);
        unsupportedSchema.setSchemaVersion("profile-import-json-v2");
        unsupportedSchema.setContentSha256(
                rawContentSha256(unsupportedSchemaTemplate, unsupportedSchema));

        AiProfileImportPromptTemplate unsupportedContractTemplate = template(11L, 101L);
        AiProfileImportPromptVersion unsupportedContract =
                releasedVersion(unsupportedContractTemplate);
        unsupportedContract.setContractVersion("profile-import-contract-v2");
        unsupportedContract.setContentSha256(
                rawContentSha256(unsupportedContractTemplate, unsupportedContract));

        AiProfileImportPromptTemplate invalidBodyTemplate = template(11L, 101L);
        AiProfileImportPromptVersion invalidBody = releasedVersion(invalidBodyTemplate);
        invalidBody.setSystemPromptBody("too short");
        invalidBody.setContentSha256(rawContentSha256(invalidBodyTemplate, invalidBody));

        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(nullActive, null),
                Arguments.of(mismatchedScene, null),
                Arguments.of(deletedTemplate, null),
                Arguments.of(template(11L, 101L), null),
                Arguments.of(wrongOwnerTemplate, wrongOwner),
                Arguments.of(draftTemplate, draft),
                Arguments.of(abandonedTemplate, abandoned),
                Arguments.of(deletedVersionTemplate, deletedVersion),
                Arguments.of(wrongVersionIdTemplate, wrongVersionId),
                Arguments.of(damagedHashTemplate, damagedHash),
                Arguments.of(unsupportedSchemaTemplate, unsupportedSchema),
                Arguments.of(unsupportedContractTemplate, unsupportedContract),
                Arguments.of(invalidBodyTemplate, invalidBody));
    }

    private static boolean readsVersion(AiProfileImportPromptTemplate template) {
        return template != null
                && Integer.valueOf(0).equals(template.getDeleted())
                && "full_profile".equals(template.getScene())
                && template.getActiveVersionId() != null;
    }

    private static void assertUnavailableWithoutMarker(
            BizException error, String marker) {
        assertEquals(46002, error.getCode());
        assertEquals(
                ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.message(),
                error.getMessage());
        assertFalse(error.getMessage().contains(marker));
    }

    private static AiProfileImportPromptTemplate template(
            Long templateId, Long activeVersionId) {
        AiProfileImportPromptTemplate template = new AiProfileImportPromptTemplate();
        template.setTemplateId(templateId);
        template.setTemplateCode("profile-import-full-profile");
        template.setScene("full_profile");
        template.setActiveVersionId(activeVersionId);
        template.setDeleted(0);
        return template;
    }

    private static AiProfileImportPromptVersion releasedVersion(
            AiProfileImportPromptTemplate template) {
        AiProfileImportPromptVersion version = new AiProfileImportPromptVersion();
        version.setPromptVersionId(101L);
        version.setTemplateId(template.getTemplateId());
        version.setVersionNo(1);
        version.setLifecycleStatus("released");
        version.setSystemPromptBody("System editable profile import instructions. ".repeat(6));
        version.setRepairPromptBody("Repair JSON syntax without changing facts.");
        version.setSchemaVersion(ProfileImportPromptContract.SCHEMA_VERSION);
        version.setContractVersion(ProfileImportPromptContract.CONTRACT_VERSION);
        version.setDeleted(0);
        version.setContentSha256(renderer().contentSha256(template, version));
        return version;
    }

    private static String rawContentSha256(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version) {
        List<String> fields = List.of(
                "profile-import-prompt-content-v1",
                template.getTemplateCode(),
                template.getScene(),
                version.getSchemaVersion(),
                version.getContractVersion(),
                version.getSystemPromptBody(),
                version.getRepairPromptBody());
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                for (String field : fields) {
                    byte[] value = field.replace("\r\n", "\n")
                            .replace('\r', '\n')
                            .getBytes(StandardCharsets.UTF_8);
                    out.writeInt(value.length);
                    out.write(value);
                }
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static ProfileImportPromptRenderer renderer() {
        ProfileImportPromptContract contract = new ProfileImportPromptContract();
        return new ProfileImportPromptRenderer(
                contract, new ProfileImportPromptPolicy(contract));
    }
}
