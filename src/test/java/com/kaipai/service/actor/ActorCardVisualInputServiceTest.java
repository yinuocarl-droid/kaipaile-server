package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.provider.AiProfileImageProvider;
import com.kaipai.integration.ai.provider.AiProfileImageProviderRegistry;
import com.kaipai.mapper.actor.ActorCardBackgroundMapper;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardVisualInputMapper;
import com.kaipai.mapper.ai.ActorAiProfileCardTaskMapper;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.model.actor.card.dto.ActorCardVisualInputDTO.SaveReq;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardBackground;
import com.kaipai.model.actor.card.entity.ActorCardVisualInput;
import com.kaipai.model.user.entity.User;
import com.kaipai.model.ai.entity.ActorAiProfileCardTask;
import java.time.Duration;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.config.AiProfileCardProperties;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

class ActorCardVisualInputServiceTest {

    private ActorCardVisualInputMapper mapper;
    private ActorCardMapper cardMapper;
    private UserMapper userMapper;
    private ActorMediaAssetService assetService;
    private ActorCardBackgroundMapper backgroundMapper;
    private AiGeneratedImageStorage imageStorage;
    private ActorCardDecorationAssignmentService assignmentService;
    private ActorAiProfileCardTaskMapper taskMapper;
    private AiProfileImageProvider provider;
    private ActorCardVisualInputService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ActorCardVisualInputMapper.class);
        cardMapper = mock(ActorCardMapper.class);
        userMapper = mock(UserMapper.class);
        assetService = mock(ActorMediaAssetService.class);
        backgroundMapper = mock(ActorCardBackgroundMapper.class);
        imageStorage = mock(AiGeneratedImageStorage.class);
        assignmentService = mock(ActorCardDecorationAssignmentService.class);
        taskMapper = mock(ActorAiProfileCardTaskMapper.class);
        provider = mock(AiProfileImageProvider.class);
        AiProfileImageProviderRegistry registry = mock(AiProfileImageProviderRegistry.class);
        AiImageProviderConfigService configService = mock(AiImageProviderConfigService.class);
        AiProfileCardProperties properties = new AiProfileCardProperties();
        service = new ActorCardVisualInputService(
                mapper, cardMapper, userMapper, assetService, backgroundMapper,
                imageStorage, assignmentService, taskMapper, registry, configService, properties);

        ActorCard card = new ActorCard();
        card.setId(11L);
        card.setUserId(7L);
        card.setStatus("draft");
        when(cardMapper.selectById(11L)).thenReturn(card);
        when(cardMapper.selectOwnedForUpdate(7L, 11L)).thenReturn(card);
        User user = new User();
        user.setUserId(7L);
        user.setRealAuthStatus(2);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(configService.resolveActiveProviderCode(any())).thenReturn("openai");
        when(registry.resolve("openai")).thenReturn(provider);
        when(provider.providerCode()).thenReturn("openai");
        when(provider.modelCode()).thenReturn("gpt-image-1.5");
        when(provider.supportsReferenceImageCount(1)).thenReturn(true);
        when(provider.supportsReferenceImageCount(2)).thenReturn(true);
        when(mapper.insert(any())).thenReturn(1);
    }

    @Test
    void getReturnsNullInsteadOfInventingAVisualInputForLegacyCards() {
        assertNull(service.get(7L, 11L));
    }

    @Test
    void generatedMainVisualAndTaskAreFreshlySignedWithoutPersistingSignatures() {
        String stable = "https://managed.test/ai/generated.png";
        ActorCardVisualInput value = managedInput(null, 3L);
        value.setInputFingerprint("current-input");
        value.setGeneratedFingerprint("current-input");
        value.setGeneratedImageUrl(stable);
        ActorAiProfileCardTask task = new ActorAiProfileCardTask();
        task.setTaskId("generated-task");
        task.setStatus("success");
        task.setGeneratedImageUrl(stable);
        when(mapper.selectByCardId(11L)).thenReturn(value);
        when(taskMapper.selectByCardModeAndSource(11L, "person_ai", "current-input")).thenReturn(task);
        when(imageStorage.issueReadUrl(stable, Duration.ofMinutes(10)))
                .thenReturn(stable + "?signature=first", stable + "?signature=second");

        var first = service.get(7L, 11L);
        var second = service.get(7L, 11L);

        assertEquals(stable + "?signature=first", first.mainVisualUrl());
        assertEquals(first.mainVisualUrl(), first.task().generatedImageUrl());
        assertEquals(stable + "?signature=second", second.mainVisualUrl());
        assertEquals(second.mainVisualUrl(), second.task().generatedImageUrl());
        assertEquals(stable, value.getGeneratedImageUrl());
        assertEquals(stable, task.getGeneratedImageUrl());
        assertEquals("current-input", value.getInputFingerprint());
        verify(mapper, never()).insert(any());
    }

    @Test
    void staleGeneratedMainVisualIsNotSignedOrReturned() {
        ActorCardVisualInput value = managedInput(null, 3L);
        value.setInputFingerprint("new-input");
        value.setGeneratedFingerprint("old-input");
        value.setGeneratedImageUrl("https://managed.test/ai/stale.png");
        when(mapper.selectByCardId(11L)).thenReturn(value);
        assertNull(service.get(7L, 11L).mainVisualUrl());
        verify(imageStorage, never()).issueReadUrl(any(), any());
    }

    @Test
    void visualInputEndpointMapsOnlyItsOwnMissingTableToServiceUnavailable() {
        RuntimeException missingTable = new RuntimeException(new SQLException(
                "Table 'kaipai.actor_card_visual_input' doesn't exist", "42S02", 1146));
        when(mapper.selectByCardId(11L)).thenThrow(missingTable);

        BizException error = assertThrows(BizException.class, () -> service.get(7L, 11L));

        assertEquals(503, error.getCode());
        assertTrue(error.getMessage().contains("V20261022_003"));
    }

    @Test
    void visualInputEndpointDoesNotHideSqlSyntaxErrorsAsLegacyCompatibility() {
        RuntimeException syntax = new RuntimeException(new SQLException(
                "syntax error near actor_card_visual_input", "42000", 1064));
        when(mapper.selectByCardId(11L)).thenThrow(syntax);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.get(7L, 11L));

        assertTrue(thrown == syntax);
    }

    @Test
    void savesIndependentOwnedPersonAndEnabledBackgroundWithStableFingerprint() {
        ActorCardBackground background = background(31L, 1, "https://managed.test/background.png");
        when(backgroundMapper.selectById(31L)).thenReturn(background);

        var response = service.save(7L, 11L,
                new SaveReq("person_background_ai", 51L, null, 31L, null));

        assertEquals("person_background_ai", response.generationMode());
        assertEquals(51L, response.personAssetId());
        assertEquals(31L, response.backgroundId());
        assertEquals(1L, response.revision());
        assertEquals(64, response.inputFingerprint().length());
        verify(assetService).requireOwnedReadyPhoto(7L, 51L);
        verify(assignmentService).selectForMode(7L, 11L, "person_background_ai", 31L);
        ArgumentCaptor<ActorCardVisualInput> saved = ArgumentCaptor.forClass(ActorCardVisualInput.class);
        verify(mapper).insert(saved.capture());
        assertNull(saved.getValue().getPersonImageUrl());
        assertEquals("https://managed.test/background.png", saved.getValue().getBackgroundImageUrl());
        assertEquals("actor-card-visual-v1", saved.getValue().getGenerationConfigVersion());
        InOrder writeOrder = inOrder(cardMapper, mapper);
        writeOrder.verify(cardMapper).selectOwnedForUpdate(7L, 11L);
        writeOrder.verify(mapper).selectByCardIdForUpdate(11L);
        writeOrder.verify(mapper).insert(any());
    }

    @Test
    void rejectsBackgroundModeWhenTheSelectedRequestWriterHasNoTrueDualReferenceSupport() {
        when(provider.supportsReferenceImageCount(2)).thenReturn(false);
        when(backgroundMapper.selectById(31L)).thenReturn(
                background(31L, 1, "https://managed.test/background.png"));

        BizException error = assertThrows(BizException.class, () -> service.save(
                7L, 11L, new SaveReq("person_background_ai", 51L, null, 31L, null)));

        assertTrue(error.getMessage().contains("openai"));
        assertTrue(error.getMessage().contains("双参考图"));
        verify(mapper, never()).insert(any());
    }

    @Test
    void rejectsGenerationBeforeStorageWhenOwnerIsNotRealNameVerified() {
        User user = new User();
        user.setUserId(7L);
        user.setRealAuthStatus(1);
        when(userMapper.selectById(7L)).thenReturn(user);

        BizException error = assertThrows(BizException.class, () -> service.save(
                7L, 11L, new SaveReq("person_ai", 51L, null, null, null)));

        assertTrue(error.getMessage().contains("实名认证"));
        verify(assetService, never()).requireOwnedReadyPhoto(any(), any());
        verify(mapper, never()).insert(any());
    }

    @Test
    void publishedCardCannotMutateVisualInputOrDecorationAssignment() {
        ActorCard card = cardMapper.selectOwnedForUpdate(7L, 11L);
        card.setStatus("published");

        BizException error = assertThrows(BizException.class, () -> service.save(
                7L, 11L, new SaveReq("person_ai", 51L, null, null, 0L)));

        assertTrue(error.getMessage().contains("草稿状态"));
        verify(mapper, never()).selectByCardIdForUpdate(any());
        verify(mapper, never()).insert(any());
        verify(assetService, never()).requireOwnedReadyPhoto(any(), any());
        verify(assignmentService, never()).selectForMode(any(), any(), any(), any());
    }

    @Test
    void rejectsDisabledBackgroundAndDoesNotSilentlyGeneratePersonOnly() {
        when(backgroundMapper.selectById(31L)).thenReturn(
                background(31L, 0, "https://managed.test/background.png"));

        BizException error = assertThrows(BizException.class, () -> service.save(
                7L, 11L, new SaveReq("person_background_ai", 51L, null, 31L, null)));

        assertTrue(error.getMessage().contains("背景"));
        verify(mapper, never()).insert(any());
    }

    @Test
    void managedUrlIsStoredWithoutAResponseSignatureAndSignedOnlyWhenRead() {
        ActorCard card = cardMapper.selectById(11L);
        card.setSourceImageUrl("https://managed.test/person.png");
        when(imageStorage.isManagedUrl("https://managed.test/person.png")).thenReturn(true);
        when(imageStorage.issueReadUrl(any(), any())).thenReturn("https://managed.test/person.png?signature=short");
        ActorCardVisualInput stored = new ActorCardVisualInput();
        stored.setCardId(11L);
        stored.setUserId(7L);
        stored.setGenerationMode("person_ai");
        stored.setPersonSourceType("managed_url");
        stored.setPersonImageUrl("https://managed.test/person.png");
        stored.setRevision(1L);
        stored.setInputFingerprint("f".repeat(64));
        when(mapper.selectByCardId(11L)).thenReturn(stored);

        var response = service.get(7L, 11L);

        assertEquals("https://managed.test/person.png?signature=short", response.personImageUrl());
        assertEquals("https://managed.test/person.png", response.personStableImageUrl());
        assertEquals("f".repeat(64), response.inputFingerprint());
    }

    @Test
    void arbitraryManagedBucketUrlIsRejectedWithoutCardOwnershipEvidence() {
        when(imageStorage.isManagedUrl("https://managed.test/another-users-private.png"))
                .thenReturn(true);

        BizException error = assertThrows(BizException.class, () -> service.save(
                7L, 11L, new SaveReq("person_ai", null,
                        "https://managed.test/another-users-private.png", null, 0L)));

        assertTrue(error.getMessage().contains("personAssetId"));
        verify(mapper, never()).insert(any());
    }

    @Test
    void legacyCardSourceUrlCannotAuthorizeTheFirstManagedVisualInput() {
        ActorCard card = cardMapper.selectById(11L);
        card.setSourceImageUrl("https://managed.test/person.png");
        when(imageStorage.isManagedUrl("https://managed.test/person.png")).thenReturn(true);

        BizException error = assertThrows(BizException.class, () -> service.save(
                7L, 11L, new SaveReq("person_ai", null,
                        "https://managed.test/person.png", null, 0L)));

        assertTrue(error.getMessage().contains("personAssetId"));
        verify(mapper, never()).insert(any());
    }

    @Test
    void signedLegacyCardSourceUrlIsNeverPersistedAsAStablePersonIdentity() {
        String signed = "https://managed.test/person.png?q-signature=short-lived";
        ActorCard card = cardMapper.selectById(11L);
        card.setSourceImageUrl(signed);
        when(imageStorage.isManagedUrl(signed)).thenReturn(true);

        BizException error = assertThrows(BizException.class, () -> service.save(
                7L, 11L, new SaveReq("person_ai", null, signed, null, 0L)));

        assertTrue(error.getMessage().contains("personAssetId"));
        verify(mapper, never()).insert(any());
    }

    @Test
    void anExistingStableManagedPersonCanBeResubmittedWithoutGuessingItsSignature() {
        ActorCardVisualInput current = managedInput("https://managed.test/person.png", 1L);
        current.setInputFingerprint(ActorCardVisualInputService.fingerprint(
                "person_ai", "url:https://managed.test/person.png", "",
                ActorCardVisualInputService.GENERATION_CONFIG_VERSION));
        when(mapper.selectByCardIdForUpdate(11L)).thenReturn(current);
        when(imageStorage.isManagedUrl("https://managed.test/person.png")).thenReturn(true);
        when(imageStorage.issueReadUrl(any(), any()))
                .thenReturn("https://managed.test/person.png?signature=fresh");

        var response = service.save(7L, 11L, new SaveReq(
                "person_ai", null, "https://managed.test/person.png", null, 1L));

        assertEquals("https://managed.test/person.png", response.personStableImageUrl());
        assertEquals("https://managed.test/person.png?signature=fresh", response.personImageUrl());
        verify(mapper, never()).updateInputCas(any(), any(Long.class));
    }

    @Test
    void duplicateInitialInsertRaceIsReportedAsRevisionConflict() {
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate card_id"));

        BizException error = assertThrows(BizException.class, () -> service.save(
                7L, 11L, new SaveReq("person_ai", 51L, null, null, 0L)));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("版本冲突"));
        verify(assignmentService, never()).selectForMode(any(), any(), any(), any());
    }

    @Test
    void everyRevisionConflictUsesHttp409AndNeverPretendsToSave() {
        BizException missingCurrent = assertThrows(BizException.class, () -> service.save(
                7L, 11L, new SaveReq("person_ai", 51L, null, null, 2L)));
        assertEquals(409, missingCurrent.getCode());

        ActorCardVisualInput current = new ActorCardVisualInput();
        current.setCardId(11L);
        current.setUserId(7L);
        current.setRevision(3L);
        when(mapper.selectByCardIdForUpdate(11L)).thenReturn(current);
        BizException staleRead = assertThrows(BizException.class, () -> service.save(
                7L, 11L, new SaveReq("person_ai", 51L, null, null, 2L)));
        assertEquals(409, staleRead.getCode());

        current.setRevision(3L);
        current.setInputFingerprint("old");
        when(mapper.updateInputCas(any(), org.mockito.ArgumentMatchers.eq(3L))).thenReturn(0);
        BizException lostCas = assertThrows(BizException.class, () -> service.save(
                7L, 11L, new SaveReq("person_ai", 51L, null, null, 3L)));
        assertEquals(409, lostCas.getCode());
        assertFalse(lostCas.getMessage().isBlank());
    }

    private ActorCardVisualInput managedInput(String url, long revision) {
        ActorCardVisualInput value = new ActorCardVisualInput();
        value.setCardId(11L);
        value.setUserId(7L);
        value.setGenerationMode("person_ai");
        value.setPersonSourceType("managed_url");
        value.setPersonImageUrl(url);
        value.setRevision(revision);
        value.setGenerationConfigVersion(ActorCardVisualInputService.GENERATION_CONFIG_VERSION);
        return value;
    }

    private ActorCardBackground background(Long id, int enabled, String url) {
        ActorCardBackground background = new ActorCardBackground();
        background.setId(id);
        background.setEnabled(enabled);
        background.setImageUrl(url);
        return background;
    }
}
