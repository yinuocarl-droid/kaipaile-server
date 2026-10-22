package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kaipai.integration.ai.provider.AiProfileImageProvider;
import com.kaipai.integration.ai.provider.AiProfileImageProviderRegistry;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.ai.ActorAiProfileCardTaskMapper;
import com.kaipai.model.actor.card.dto.ActorCardExpandImageReqDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.dto.ActorAssetAccessUrlRespDTO;
import com.kaipai.model.ai.entity.ActorAiProfileCardTask;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.config.AiProfileCardProperties;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationResult;
import java.time.Instant;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ActorCardExpandImageServiceTest {

    private ActorCardMapper cardMapper;
    private ActorAiProfileCardTaskMapper taskMapper;
    private AiProfileImageProviderRegistry registry;
    private AiImageProviderConfigService configService;
    private ActorMediaAssetService mediaAssetService;
    private ActorCardMediaBindingService mediaBindingService;
    private AiGeneratedImageStorage generatedImageStorage;
    private ActorCardExpandResultService resultService;
    private AiProfileImageProvider provider;
    private ActorCardExpandImageService service;

    @BeforeEach
    void setUp() {
        cardMapper = mock(ActorCardMapper.class);
        taskMapper = mock(ActorAiProfileCardTaskMapper.class);
        registry = mock(AiProfileImageProviderRegistry.class);
        configService = mock(AiImageProviderConfigService.class);
        mediaAssetService = mock(ActorMediaAssetService.class);
        mediaBindingService = mock(ActorCardMediaBindingService.class);
        generatedImageStorage = mock(AiGeneratedImageStorage.class);
        resultService = mock(ActorCardExpandResultService.class);
        provider = mock(AiProfileImageProvider.class);
        AiProfileCardProperties properties = new AiProfileCardProperties();
        service = new ActorCardExpandImageService(
                cardMapper,
                taskMapper,
                registry,
                configService,
                properties,
                mediaAssetService,
                mediaBindingService,
                generatedImageStorage,
                resultService);
        ReflectionTestUtils.setField(service, "taskExecutor", (Executor) command -> { });

        ActorCard card = new ActorCard();
        card.setId(11L);
        card.setUserId(7L);
        when(cardMapper.selectById(11L)).thenReturn(card);
        when(configService.resolveActiveProviderCode(any())).thenReturn("mock");
        when(provider.modelCode()).thenReturn("mock-model");
        when(registry.resolve("mock")).thenReturn(provider);
        when(taskMapper.insert(any())).thenReturn(1);
        when(mediaAssetService.issueOwnerReadyPhotoAccessUrl(7L, 51L)).thenReturn(
                new ActorAssetAccessUrlRespDTO(
                        "https://signed.test/source", Instant.parse("2026-08-18T13:00:00Z")));
    }

    @Test
    void privateAssetPersistsStableIdentityButNotItsSignedUrl() {
        ActorCardExpandImageReqDTO request = assetRequest();

        var result = service.submit(7L, 11L, request);

        assertEquals("https://signed.test/source", result.getOriginalUrl());
        ArgumentCaptor<ActorAiProfileCardTask> task = ArgumentCaptor.forClass(ActorAiProfileCardTask.class);
        verify(taskMapper).insert(task.capture());
        assertEquals(11L, task.getValue().getActorCardId());
        assertEquals(51L, task.getValue().getSourceAssetId());
        assertEquals(64, task.getValue().getSourceFingerprint().length());
        assertNull(task.getValue().getSourceImageUrl());
    }

    @Test
    void repeatedSourceReusesTheExistingTask() {
        String fingerprint = ActorCardMainVisualResolver.SourceReference.forAsset(51L).fingerprint();
        ActorAiProfileCardTask existing = task("acexp_existing", fingerprint, "pending");
        when(taskMapper.selectByCardModeAndSource(11L, "expand_image", fingerprint))
                .thenReturn(null, existing);

        service.submit(7L, 11L, assetRequest());
        var second = service.submit(7L, 11L, assetRequest());

        assertEquals("acexp_existing", second.getTaskId());
        verify(taskMapper, times(1)).insert(any());
    }

    @Test
    void aFailedTaskIsClaimedOnceForRetryInsteadOfCreatingAnotherTask() {
        String fingerprint = ActorCardMainVisualResolver.SourceReference.forAsset(51L).fingerprint();
        ActorAiProfileCardTask failed = task("acexp_failed", fingerprint, "failed");
        when(taskMapper.selectByCardModeAndSource(11L, "expand_image", fingerprint)).thenReturn(failed);
        when(taskMapper.update(any(), any())).thenReturn(1);

        var response = service.submit(7L, 11L, assetRequest());

        assertEquals("pending", response.getStatus());
        verify(taskMapper, times(0)).insert(any());
        verify(taskMapper).update(any(), any());
    }

    @Test
    void providerFailureIsRecordedInternallyAndDoesNotEscapeSubmission() {
        ReflectionTestUtils.setField(service, "taskExecutor", (Executor) Runnable::run);
        when(provider.generate(any())).thenThrow(new IllegalStateException("provider unavailable"));

        var response = service.submit(7L, 11L, assetRequest());

        assertEquals("pending", response.getStatus());
        ArgumentCaptor<UpdateWrapper<ActorAiProfileCardTask>> updates = wrapperCaptor();
        verify(taskMapper, times(2)).update(any(), updates.capture());
        assertTrue(updates.getAllValues().get(1).getParamNameValuePairs().containsValue("failed"));
        assertTrue(updates.getAllValues().get(1).getParamNameValuePairs()
                .containsValue("provider unavailable"));
    }

    @Test
    void generatedBytesAreStoredAndAppliedThroughTheSourceGuard() {
        ReflectionTestUtils.setField(service, "taskExecutor", (Executor) Runnable::run);
        when(provider.generate(any())).thenReturn(
                AiProfileImageGenerationResult.imageBytes(new byte[] {1, 2, 3}, "image/png"));
        when(generatedImageStorage.upload(any(), any(), any()))
                .thenReturn("https://managed.test/expanded.png");
        when(resultService.applyIfCurrent(any(), any(), any(), any())).thenReturn(true);

        service.submit(7L, 11L, assetRequest());

        verify(generatedImageStorage).upload(any(), any(), any());
        verify(resultService).applyIfCurrent(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(11L),
                any(),
                org.mockito.ArgumentMatchers.eq("https://managed.test/expanded.png"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<UpdateWrapper<ActorAiProfileCardTask>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(UpdateWrapper.class);
    }

    private ActorCardExpandImageReqDTO assetRequest() {
        ActorCardExpandImageReqDTO request = new ActorCardExpandImageReqDTO();
        request.setSourceAssetId(51L);
        return request;
    }

    private ActorAiProfileCardTask task(String taskId, String fingerprint, String status) {
        ActorAiProfileCardTask task = new ActorAiProfileCardTask();
        task.setTaskId(taskId);
        task.setUserId(7L);
        task.setActorCardId(11L);
        task.setSourceAssetId(51L);
        task.setSourceFingerprint(fingerprint);
        task.setGenerationMode("expand_image");
        task.setStatus(status);
        return task;
    }
}
