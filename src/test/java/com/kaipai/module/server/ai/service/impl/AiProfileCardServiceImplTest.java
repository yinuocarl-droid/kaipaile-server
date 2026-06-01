package com.kaipai.service.ai.impl;

import com.kaipai.common.exception.BizException;
import com.kaipai.model.actor.dto.ActorProfileDTO;
import com.kaipai.model.ai.dto.AiProfileCardTaskRespDTO;
import com.kaipai.model.ai.entity.ActorAiProfileCardTask;
import com.kaipai.model.card.dto.ActorCardConfigSaveDTO;
import com.kaipai.model.card.dto.ActorMyShareCardItemDTO;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.service.actor.ActorProfileService;
import com.kaipai.service.ai.config.AiProfileCardProperties;
import com.kaipai.mapper.ai.ActorAiProfileCardPageMapper;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.service.ai.profilecard.AiProfileCardGeneration;
import com.kaipai.service.ai.profilecard.AiProfileCardImageQualityInspection;
import com.kaipai.service.ai.profilecard.AiProfileCardImageQualityInspector;
import com.kaipai.service.ai.profilecard.AiProfileCardPrompt;
import com.kaipai.service.ai.profilecard.AiProfileCardPromptAgent;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationResult;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.card.ActorCardConfigService;
import com.kaipai.service.card.UserShareCardService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiProfileCardServiceImplTest {

    @Test
    void resolveGeneratedImageUrlShouldRejectSourceImageEcho() {
        AiProfileCardServiceImpl service = new AiProfileCardServiceImpl(null, null, null, null, null, null, null, null, null, null);
        AiProfileImageGenerationResult result = AiProfileImageGenerationResult.imageUrl(
                "https://cdn.kplyyk.com/source.png?token=generated");

        BizException error = assertThrows(BizException.class, () -> ReflectionTestUtils.invokeMethod(
                service,
                "resolveGeneratedImageUrl",
                result,
                "https://cdn.kplyyk.com/source.png?token=source"));

        assertEquals("AI 图片生成结果不能直接返回原始参考图", error.getMessage());
    }

    @Test
    void generateCoverImageWithQualityGateShouldRetryWhenFirstImageIsRejected() {
        AiProfileCardProperties properties = new AiProfileCardProperties();
        properties.setCoverQualityGateEnabled(true);
        properties.setCoverQualityMaxAttempts(2);
        AiProfileCardPromptAgent promptAgent = mock(AiProfileCardPromptAgent.class);
        AiGeneratedImageStorage imageStorage = mock(AiGeneratedImageStorage.class);
        ActorAiProfileCardPageMapper pageMapper = mock(ActorAiProfileCardPageMapper.class);
        AiProfileCardImageQualityInspector qualityInspector = mock(AiProfileCardImageQualityInspector.class);
        AiProfileCardServiceImpl service = spy(new AiProfileCardServiceImpl(
                null,
                null,
                properties,
                null,
                promptAgent,
                null,
                null,
                imageStorage,
                pageMapper,
                qualityInspector));
        doReturn(true).when(service).updateById(any(ActorAiProfileCardTask.class));

        ActorAiProfileCardTask task = newTask();
        task.setSourceImageUrl("https://cdn.example.com/source.png");

        AiProfileCardPrompt prompt = new AiProfileCardPrompt("{}", "prompt", "negative");
        when(promptAgent.generate(
                any(ActorProfileDTO.class),
                anyString(),
                eq("tencent-hunyuan"),
                eq("classic"),
                eq("classic"),
                eq("https://cdn.example.com/source.png")))
                .thenReturn(
                        new AiProfileCardGeneration(
                                "tencent-hunyuan",
                                "hunyuan-image",
                                prompt,
                                AiProfileImageGenerationResult.imageUrl("https://tmp.example.com/bad.png")),
                        new AiProfileCardGeneration(
                                "tencent-hunyuan",
                                "hunyuan-image",
                                prompt,
                                AiProfileImageGenerationResult.imageUrl("https://tmp.example.com/good.png")));
        when(imageStorage.uploadFromUrl("https://tmp.example.com/bad.png", "ai-profile-card"))
                .thenReturn("https://cos.example.com/bad.png");
        when(imageStorage.uploadFromUrl("https://tmp.example.com/good.png", "ai-profile-card"))
                .thenReturn("https://cos.example.com/good.png");
        when(qualityInspector.inspectCover("https://cos.example.com/bad.png", "tencent-hunyuan"))
                .thenReturn(AiProfileCardImageQualityInspection.rejected("封面成图检测到文字：乱码"));
        when(qualityInspector.inspectCover("https://cos.example.com/good.png", "tencent-hunyuan"))
                .thenReturn(AiProfileCardImageQualityInspection.accept());

        String imageUrl = ReflectionTestUtils.invokeMethod(
                service,
                "generateCoverImageWithQualityGate",
                new ActorProfileDTO(),
                task);

        assertEquals("https://cos.example.com/good.png", imageUrl);
        verify(promptAgent, times(2)).generate(
                any(ActorProfileDTO.class),
                anyString(),
                eq("tencent-hunyuan"),
                eq("classic"),
                eq("classic"),
                eq("https://cdn.example.com/source.png"));
        verify(qualityInspector, times(2)).inspectCover(anyString(), eq("tencent-hunyuan"));
        verifyNoInteractions(pageMapper);
    }

    @Test
    void generateCoverImageWithQualityGateShouldNotRetryWhenInspectionIsUnavailable() {
        AiProfileCardProperties properties = new AiProfileCardProperties();
        properties.setCoverQualityGateEnabled(true);
        properties.setCoverQualityMaxAttempts(3);
        AiProfileCardPromptAgent promptAgent = mock(AiProfileCardPromptAgent.class);
        AiGeneratedImageStorage imageStorage = mock(AiGeneratedImageStorage.class);
        ActorAiProfileCardPageMapper pageMapper = mock(ActorAiProfileCardPageMapper.class);
        AiProfileCardImageQualityInspector qualityInspector = mock(AiProfileCardImageQualityInspector.class);
        AiProfileCardServiceImpl service = spy(new AiProfileCardServiceImpl(
                null,
                null,
                properties,
                null,
                promptAgent,
                null,
                null,
                imageStorage,
                pageMapper,
                qualityInspector));
        doReturn(true).when(service).updateById(any(ActorAiProfileCardTask.class));

        ActorAiProfileCardTask task = newTask();
        task.setSourceImageUrl("https://cdn.example.com/source.png");

        AiProfileCardPrompt prompt = new AiProfileCardPrompt("{}", "prompt", "negative");
        when(promptAgent.generate(
                any(ActorProfileDTO.class),
                anyString(),
                eq("tencent-hunyuan"),
                eq("classic"),
                eq("classic"),
                eq("https://cdn.example.com/source.png")))
                .thenReturn(
                        new AiProfileCardGeneration(
                                "tencent-hunyuan",
                                "hunyuan-image",
                                prompt,
                                AiProfileImageGenerationResult.imageUrl("https://tmp.example.com/unavailable.png")));
        when(imageStorage.uploadFromUrl("https://tmp.example.com/unavailable.png", "ai-profile-card"))
                .thenReturn("https://cos.example.com/unavailable.png");
        when(qualityInspector.inspectCover("https://cos.example.com/unavailable.png", "tencent-hunyuan"))
                .thenReturn(AiProfileCardImageQualityInspection.unavailable("腾讯 OCR 服务不可用，封面质检无法执行"));

        RuntimeException error = assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                service,
                "generateCoverImageWithQualityGate",
                new ActorProfileDTO(),
                task));

        assertEquals("腾讯 OCR 服务不可用，封面质检无法执行", error.getMessage());
        verify(promptAgent, times(1)).generate(
                any(ActorProfileDTO.class),
                anyString(),
                eq("tencent-hunyuan"),
                eq("classic"),
                eq("classic"),
                eq("https://cdn.example.com/source.png"));
        verify(qualityInspector, times(1)).inspectCover(anyString(), eq("tencent-hunyuan"));
        verifyNoInteractions(pageMapper);
    }

    @Test
    void runGenerationShouldGenerateOnlySingleCoverAndPersistThemeColors() {
        AiProfileCardProperties properties = new AiProfileCardProperties();
        properties.setCoverQualityGateEnabled(false);
        properties.setCoverQualityMaxAttempts(1);

        ActorProfileService actorProfileService = mock(ActorProfileService.class);
        ActorProfileMapper actorProfileMapper = mock(ActorProfileMapper.class);
        AiImageProviderConfigService aiImageProviderConfigService = mock(AiImageProviderConfigService.class);
        AiProfileCardPromptAgent promptAgent = mock(AiProfileCardPromptAgent.class);
        UserShareCardService userShareCardService = mock(UserShareCardService.class);
        ActorCardConfigService actorCardConfigService = mock(ActorCardConfigService.class);
        AiGeneratedImageStorage imageStorage = mock(AiGeneratedImageStorage.class);
        ActorAiProfileCardPageMapper pageMapper = mock(ActorAiProfileCardPageMapper.class);
        AiProfileCardImageQualityInspector qualityInspector = mock(AiProfileCardImageQualityInspector.class);
        AiProfileCardServiceImpl service = spy(new AiProfileCardServiceImpl(
                actorProfileService,
                actorProfileMapper,
                properties,
                aiImageProviderConfigService,
                promptAgent,
                userShareCardService,
                actorCardConfigService,
                imageStorage,
                pageMapper,
                qualityInspector));
        doReturn(true).when(service).updateById(any(ActorAiProfileCardTask.class));
        doReturn(newTask()).when(service).getById("aipf_flow");

        ActorProfileDTO profile = new ActorProfileDTO();
        profile.setGender("female");
        profile.setAge(24);
        profile.setHeight(168);
        profile.setSkillTypes(List.of("表演", "舞蹈"));
        when(actorProfileService.mine(7L)).thenReturn(profile);

        AiProfileCardPrompt prompt = new AiProfileCardPrompt("{\"flowTheme\":true}", "prompt", "negative");
        when(promptAgent.generate(
                any(ActorProfileDTO.class),
                anyString(),
                eq("tencent-hunyuan"),
                eq("classic"),
                eq("classic"),
                eq("https://cdn.example.com/source.png")))
                .thenReturn(new AiProfileCardGeneration(
                        "tencent-hunyuan",
                        "hunyuan-image-3.0",
                        prompt,
                        AiProfileImageGenerationResult.imageUrl("https://tmp.example.com/cover.png")));
        when(imageStorage.uploadFromUrl("https://tmp.example.com/cover.png", "ai-profile-card"))
                .thenReturn("https://cdn.example.com/cover.png");

        ActorMyShareCardItemDTO card = new ActorMyShareCardItemDTO();
        card.setCardId(88L);
        when(userShareCardService.createCard(eq(7L), any())).thenReturn(card);

        ReflectionTestUtils.invokeMethod(service, "runGeneration", "aipf_flow");

        verify(promptAgent, times(1)).generate(
                any(ActorProfileDTO.class),
                anyString(),
                eq("tencent-hunyuan"),
                eq("classic"),
                eq("classic"),
                eq("https://cdn.example.com/source.png"));
        verifyNoInteractions(pageMapper);

        ArgumentCaptor<ActorCardConfigSaveDTO> configCaptor = ArgumentCaptor.forClass(ActorCardConfigSaveDTO.class);
        verify(actorCardConfigService).saveActorConfig(eq(7L), configCaptor.capture());
        ActorCardConfigSaveDTO config = configCaptor.getValue();
        assertEquals(88L, config.getShareCardId());
        assertEquals("#eee3cf", config.getBackgroundColor());
        assertEquals("#8c6f4f", config.getPrimaryColor());
        assertEquals("#eadfce", config.getAccentColor());
        assertEquals(List.of("https://cdn.example.com/cover.png", "https://cdn.example.com/source.png"), config.getHighlightedPhotos());
    }

    @Test
    void taskShouldExposeThemeAndEmptyPagesForSingleCoverFlow() {
        AiGeneratedImageStorage imageStorage = mock(AiGeneratedImageStorage.class);
        ActorAiProfileCardPageMapper pageMapper = mock(ActorAiProfileCardPageMapper.class);
        AiProfileCardServiceImpl service = spy(new AiProfileCardServiceImpl(
                null,
                null,
                new AiProfileCardProperties(),
                null,
                null,
                null,
                null,
                imageStorage,
                pageMapper,
                null));

        ActorAiProfileCardTask task = newTask();
        task.setStatus("success");
        task.setTemplateSceneCode("urban");
        task.setStyleCode("urban");
        task.setShareCardId(88L);
        task.setGeneratedImageUrl("https://cdn.example.com/cover.png");
        when(imageStorage.isManagedUrl("https://cdn.example.com/cover.png")).thenReturn(true);
        doReturn(task).when(service).getById("aipf_flow");

        AiProfileCardTaskRespDTO dto = service.task(7L, "aipf_flow");

        assertEquals("aipf_flow", dto.getTaskId());
        assertEquals("https://cdn.example.com/cover.png", dto.getGeneratedImageUrl());
        assertNotNull(dto.getTheme());
        assertEquals("#0f1115", dto.getTheme().getBackgroundColor());
        assertEquals("#181d24", dto.getTheme().getSurfaceColor());
        assertTrue(dto.getPages().isEmpty());
        verifyNoInteractions(pageMapper);
    }

    private ActorAiProfileCardTask newTask() {
        ActorAiProfileCardTask task = new ActorAiProfileCardTask();
        task.setTaskId("aipf_flow");
        task.setUserId(7L);
        task.setProviderCode("tencent-hunyuan");
        task.setModelCode("hunyuan-image-3.0");
        task.setTemplateSceneCode("classic");
        task.setStyleCode("classic");
        task.setSourceImageUrl("https://cdn.example.com/source.png");
        task.setStatus("pending");
        return task;
    }
}
