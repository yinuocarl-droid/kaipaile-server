package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.provider.AiProfileImageProvider;
import com.kaipai.integration.ai.provider.AiProfileImageProviderRegistry;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardVisualInputMapper;
import com.kaipai.mapper.ai.ActorAiProfileCardTaskMapper;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.model.actor.card.dto.ActorCardExpandImageReqDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardVisualInput;
import com.kaipai.model.actor.dto.ActorAssetAccessUrlRespDTO;
import com.kaipai.model.ai.entity.ActorAiProfileCardTask;
import com.kaipai.model.user.entity.User;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.config.AiProfileCardProperties;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationResult;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DuplicateKeyException;

class ActorCardDualModeExpandImageServiceTest {

    private ActorAiProfileCardTaskMapper taskMapper;
    private ActorCardMapper cardMapper;
    private ActorCardVisualInputMapper inputMapper;
    private ActorMediaAssetService assetService;
    private ActorCardMediaBindingService bindingService;
    private AiGeneratedImageStorage imageStorage;
    private ActorCardExpandResultService resultService;
    private AiProfileImageProvider provider;
    private AiProfileImageProviderRegistry registry;
    private AiImageProviderConfigService configService;
    private ActorCardExpandImageService service;

    @BeforeEach
    void setUp() {
        cardMapper = mock(ActorCardMapper.class);
        taskMapper = mock(ActorAiProfileCardTaskMapper.class);
        registry = mock(AiProfileImageProviderRegistry.class);
        configService = mock(AiImageProviderConfigService.class);
        assetService = mock(ActorMediaAssetService.class);
        bindingService = mock(ActorCardMediaBindingService.class);
        imageStorage = mock(AiGeneratedImageStorage.class);
        resultService = mock(ActorCardExpandResultService.class);
        inputMapper = mock(ActorCardVisualInputMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        provider = mock(AiProfileImageProvider.class);
        service = new ActorCardExpandImageService(
                cardMapper, taskMapper, registry, configService, new AiProfileCardProperties(),
                assetService, bindingService, imageStorage, resultService, inputMapper, userMapper);
        ReflectionTestUtils.setField(service, "taskExecutor", (Executor) Runnable::run);

        ActorCard card = new ActorCard();
        card.setId(11L);
        card.setUserId(7L);
        card.setSourceImageUrl("https://cdn.test/legacy-person.png");
        when(cardMapper.selectById(11L)).thenReturn(card);
        User user = new User();
        user.setUserId(7L);
        user.setRealAuthStatus(2);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(configService.resolveActiveProviderCode(any())).thenReturn("openai");
        when(registry.resolve("openai")).thenReturn(provider);
        when(provider.modelCode()).thenReturn("gpt-image-1.5");
        when(taskMapper.insert(any())).thenReturn(1);
        when(imageStorage.upload(any(), any(), any())).thenReturn("https://managed.test/generated.png");
        when(resultService.applyVisualIfCurrent(any(), any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void sendsTwoIndependentlyResolvedReferencesAndAppliesOnlyThroughVisualFingerprintGuard() {
        ActorCardVisualInput input = backgroundInput();
        when(inputMapper.selectByCardId(11L)).thenReturn(input);
        when(provider.supportsReferenceImageCount(2)).thenReturn(true);
        when(assetService.issueOwnerReadyPhotoAccessUrl(7L, 51L)).thenReturn(
                new ActorAssetAccessUrlRespDTO("https://signed.test/person", Instant.now().plusSeconds(600)));
        when(imageStorage.issueReadUrl(eq("https://managed.test/background.png"), any()))
                .thenReturn("https://managed.test/background.png?signature=short");
        when(provider.generate(any())).thenReturn(
                AiProfileImageGenerationResult.imageBytes(new byte[] {1, 2, 3}, "image/png"));

        service.submitVisualInput(7L, 11L);

        ArgumentCaptor<AiProfileImageGenerationRequest> request =
                ArgumentCaptor.forClass(AiProfileImageGenerationRequest.class);
        verify(provider).generate(request.capture());
        assertEquals("https://signed.test/person", request.getValue().sourceImageUrl());
        assertEquals("https://managed.test/background.png?signature=short",
                request.getValue().backgroundImageUrl());
        assertTrue(request.getValue().promptText().contains("参考图1"));
        assertTrue(request.getValue().promptText().contains("参考图2"));
        verify(resultService).applyVisualIfCurrent(
                eq(7L), eq(11L), eq("f".repeat(64)), any(),
                eq("https://managed.test/generated.png"));
    }

    @Test
    void unsupportedActiveWriterIsRejectedBeforeCreatingOrCallingAProviderTask() {
        when(inputMapper.selectByCardId(11L)).thenReturn(backgroundInput());
        when(provider.supportsReferenceImageCount(2)).thenReturn(false);

        BizException error = org.junit.jupiter.api.Assertions.assertThrows(
                BizException.class, () -> service.submitVisualInput(7L, 11L));

        assertTrue(error.getMessage().contains("双参考图"));
        verify(taskMapper, never()).insert(any());
        verify(provider, never()).generate(any());
    }

    @Test
    void failedTaskRetryReusesTheSameFingerprintInsteadOfCreatingADuplicate() {
        ReflectionTestUtils.setField(service, "taskExecutor", (Executor) command -> { });
        ActorCardVisualInput input = backgroundInput();
        when(inputMapper.selectByCardId(11L)).thenReturn(input);
        when(provider.supportsReferenceImageCount(2)).thenReturn(true);
        ActorAiProfileCardTask failed = new ActorAiProfileCardTask();
        failed.setTaskId("visual_failed");
        failed.setUserId(7L);
        failed.setActorCardId(11L);
        failed.setGenerationMode("person_background_ai");
        failed.setSourceFingerprint(input.getInputFingerprint());
        failed.setStatus("failed");
        when(taskMapper.selectByCardModeAndSource(
                11L, "person_background_ai", input.getInputFingerprint())).thenReturn(failed);
        when(taskMapper.update(any(), any())).thenReturn(1);

        service.retryVisualInput(7L, 11L);

        verify(taskMapper, never()).insert(any());
        verify(taskMapper).update(any(), any(UpdateWrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"provider-timeout", "response-parse", "result-storage"})
    void tokenHubFailureDoesNotBillAgainOnAutomaticReadOrOrdinarySave(String failureStage) {
        AtomicReference<ActorAiProfileCardTask> persisted = tokenHubFailureFixture(failureStage);
        service.submitVisualInput(7L, 11L);
        assertEquals("failed", persisted.get().getStatus());
        String originalReason = persisted.get().getFailureReason();
        // Guard belongs to the failed task, not whichever provider is active now.
        when(configService.resolveActiveProviderCode(any())).thenReturn("openai");

        service.requestAutomaticExpansion(7L, 11L);
        service.requestAutomaticExpansion(7L, 11L);
        var response = service.submitVisualInput(7L, 11L);

        verify(provider, times(1)).generate(any());
        verify(taskMapper, times(1)).insert(any());
        assertEquals("failed", response.getStatus());
        assertEquals(originalReason, persisted.get().getFailureReason());
        assertEquals("tencent-tokenhub", persisted.get().getProviderCode());
    }

    @Test
    void tokenHubFailedTaskCanStillBeExplicitlyRetriedThroughTheExistingAtomicClaim() {
        AtomicReference<ActorAiProfileCardTask> persisted = tokenHubFailureFixture("provider-timeout");
        service.submitVisualInput(7L, 11L);

        service.retryVisualInput(7L, 11L);

        verify(provider, times(2)).generate(any());
        verify(taskMapper, times(1)).insert(any());
        assertEquals("failed", persisted.get().getStatus());
    }

    @Test
    void automaticLegacyReadPreservesFailedTokenHubTaskWithoutClaimOrProviderSelection() {
        String fingerprint = ActorCardMainVisualResolver.SourceReference
                .forUrl("https://cdn.test/legacy-person.png").fingerprint();
        ActorAiProfileCardTask failed = new ActorAiProfileCardTask();
        failed.setTaskId("tokenhub_legacy_unknown");
        failed.setUserId(7L);
        failed.setActorCardId(11L);
        failed.setProviderCode("tencent-tokenhub");
        failed.setGenerationMode("expand_image");
        failed.setSourceFingerprint(fingerprint);
        failed.setStatus("failed");
        failed.setFailureReason("TokenHub outcome unknown");
        when(taskMapper.selectByCardModeAndSource(11L, "expand_image", fingerprint)).thenReturn(failed);

        service.requestAutomaticExpansion(7L, 11L);
        service.requestAutomaticExpansion(7L, 11L);

        verify(taskMapper, never()).update(any(), any());
        verify(configService, never()).resolveActiveProviderCode(any());
        verify(provider, never()).generate(any());
        assertEquals("failed", failed.getStatus());
        assertEquals("TokenHub outcome unknown", failed.getFailureReason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"person_background_ai", "expand_image"})
    void duplicateInsertRaceDoesNotTurnAFailedTokenHubTaskIntoAnAutomaticRetry(String mode) {
        String fingerprint = "expand_image".equals(mode)
                ? ActorCardMainVisualResolver.SourceReference.forUrl("https://cdn.test/legacy-person.png").fingerprint()
                : "f".repeat(64);
        if (!"expand_image".equals(mode)) when(inputMapper.selectByCardId(11L)).thenReturn(backgroundInput());
        when(provider.supportsReferenceImageCount(2)).thenReturn(true);
        ActorAiProfileCardTask failed = new ActorAiProfileCardTask();
        failed.setTaskId("tokenhub_raced_unknown");
        failed.setUserId(7L);
        failed.setActorCardId(11L);
        failed.setProviderCode("tencent-tokenhub");
        failed.setGenerationMode(mode);
        failed.setSourceFingerprint(fingerprint);
        failed.setStatus("failed");
        failed.setFailureReason("TokenHub outcome unknown");
        when(taskMapper.selectByCardModeAndSource(11L, mode, fingerprint)).thenReturn(null, failed);
        when(taskMapper.insert(any())).thenThrow(new DuplicateKeyException("another request already inserted task"));

        service.requestAutomaticExpansion(7L, 11L);

        verify(taskMapper, never()).update(any(), any());
        verify(provider, never()).generate(any());
        assertEquals("failed", failed.getStatus());
        assertEquals("TokenHub outcome unknown", failed.getFailureReason());
    }

    private AtomicReference<ActorAiProfileCardTask> tokenHubFailureFixture(String failureStage) {
        when(configService.resolveActiveProviderCode(any())).thenReturn("tencent-tokenhub");
        when(registry.resolve("tencent-tokenhub")).thenReturn(provider);
        when(provider.modelCode()).thenReturn("hy-image-v3.5-preview");
        when(provider.supportsReferenceImageCount(2)).thenReturn(true);
        when(inputMapper.selectByCardId(11L)).thenReturn(backgroundInput());
        when(assetService.issueOwnerReadyPhotoAccessUrl(7L, 51L)).thenReturn(
                new ActorAssetAccessUrlRespDTO("https://signed.test/person", Instant.now().plusSeconds(600)));
        when(imageStorage.issueReadUrl(eq("https://managed.test/background.png"), any()))
                .thenReturn("https://managed.test/background.png?signature=short");
        if ("result-storage".equals(failureStage)) {
            when(provider.generate(any())).thenReturn(
                    AiProfileImageGenerationResult.imageBytes(new byte[] {1, 2, 3}, "image/png"));
            when(imageStorage.upload(any(), any(), any())).thenThrow(new BizException("storage failed after vendor success"));
        } else {
            when(provider.generate(any())).thenThrow(new BizException("TokenHub " + failureStage + " outcome unknown"));
        }
        AtomicReference<ActorAiProfileCardTask> persisted = new AtomicReference<>();
        when(taskMapper.insert(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        });
        when(taskMapper.selectByCardModeAndSource(11L, "person_background_ai", "f".repeat(64)))
                .thenAnswer(invocation -> persisted.get());
        when(taskMapper.update(any(), any())).thenAnswer(invocation -> {
            UpdateWrapper<?> update = invocation.getArgument(1);
            update.getSqlSet(); // Materialize parameter map like the actual MyBatis writer.
            var values = update.getParamNameValuePairs().values();
            if (values.contains("failed")) {
                persisted.get().setStatus("failed");
                persisted.get().setFailureReason("TokenHub " + failureStage + " outcome unknown");
            } else if (values.contains("pending")) {
                persisted.get().setStatus("pending");
            } else if (values.contains("running")) {
                persisted.get().setStatus("running");
            }
            return 1;
        });
        return persisted;
    }

    @Test
    void queuedTaskDoesNotCallProviderAfterTheCurrentVisualFingerprintChanges() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        ReflectionTestUtils.setField(service, "taskExecutor", (Executor) queued::set);
        ActorCardVisualInput original = backgroundInput();
        ActorCardVisualInput changed = backgroundInput();
        changed.setRevision(3L);
        changed.setInputFingerprint("a".repeat(64));
        when(inputMapper.selectByCardId(11L)).thenReturn(original, changed);
        when(provider.supportsReferenceImageCount(2)).thenReturn(true);

        service.submitVisualInput(7L, 11L);
        queued.get().run();

        verify(provider, never()).generate(any());
        verify(resultService, never()).applyVisualIfCurrent(any(), any(), any(), any(), any());
    }

    @Test
    void automaticLegacyExpansionContinuesOnlyForThePreciseMissingVisualTableError() {
        ReflectionTestUtils.setField(service, "taskExecutor", (Executor) command -> { });
        when(inputMapper.selectByCardId(11L)).thenThrow(new RuntimeException(new SQLException(
                "Table 'kaipai.actor_card_visual_input' doesn't exist", "42S02", 1146)));

        service.requestAutomaticExpansion(7L, 11L);

        verify(bindingService).list(7L, 11L, "source_image", null);
        verify(taskMapper).insert(any());
    }

    @Test
    void automaticExpansionDoesNotHideVisualTableSqlSyntaxErrorsAsLegacy() {
        when(inputMapper.selectByCardId(11L)).thenThrow(new RuntimeException(new SQLException(
                "syntax error near actor_card_visual_input", "42000", 1064)));

        service.requestAutomaticExpansion(7L, 11L);

        verify(bindingService, never()).list(any(), any(), any(), any());
        verify(taskMapper, never()).insert(any());
    }

    @Test
    void explicitVisualRetryReportsUnavailableWhenV003IsMissing() {
        when(inputMapper.selectByCardId(11L)).thenThrow(new RuntimeException(new SQLException(
                "Table 'kaipai.actor_card_visual_input' doesn't exist", "42S02", 1146)));

        BizException error = assertThrows(BizException.class,
                () -> service.retryVisualInput(7L, 11L));

        assertEquals(503, error.getCode());
        verify(taskMapper, never()).insert(any());
    }

    @Test
    void legacyExplicitSubmitIsRejectedBeforeSigningOrBillingWhenVisualInputExists() {
        when(inputMapper.selectByCardId(11L)).thenReturn(backgroundInput());
        ActorCardExpandImageReqDTO request = new ActorCardExpandImageReqDTO();
        request.setSourceAssetId(51L);

        BizException error = assertThrows(BizException.class,
                () -> service.submit(7L, 11L, request));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("新版首图"));
        verify(assetService, never()).issueOwnerReadyPhotoAccessUrl(any(), any());
        verify(taskMapper, never()).insert(any());
        verify(provider, never()).generate(any());
    }

    @Test
    void legacyExplicitSubmitStillWorksWhenOnlyTheVisualTableIsNotMigrated() {
        ReflectionTestUtils.setField(service, "taskExecutor", (Executor) command -> { });
        when(inputMapper.selectByCardId(11L)).thenThrow(new RuntimeException(new SQLException(
                "Table 'kaipai.actor_card_visual_input' doesn't exist", "42S02", 1146)));
        when(assetService.issueOwnerReadyPhotoAccessUrl(7L, 51L)).thenReturn(
                new ActorAssetAccessUrlRespDTO(
                        "https://signed.test/person", Instant.now().plusSeconds(600)));
        ActorCardExpandImageReqDTO request = new ActorCardExpandImageReqDTO();
        request.setSourceAssetId(51L);

        service.submit(7L, 11L, request);

        verify(taskMapper).insert(any());
    }

    private ActorCardVisualInput backgroundInput() {
        ActorCardVisualInput input = new ActorCardVisualInput();
        input.setCardId(11L);
        input.setUserId(7L);
        input.setGenerationMode("person_background_ai");
        input.setPersonSourceType("asset");
        input.setPersonAssetId(51L);
        input.setBackgroundId(31L);
        input.setBackgroundImageUrl("https://managed.test/background.png");
        input.setRevision(2L);
        input.setInputFingerprint("f".repeat(64));
        input.setGenerationConfigVersion("actor-card-visual-v1");
        return input;
    }
}
