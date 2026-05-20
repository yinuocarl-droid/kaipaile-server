package com.kaipai.module.server.ai.service.impl;

import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.ai.entity.ActorAiProfileCardPage;
import com.kaipai.module.model.ai.entity.ActorAiProfileCardTask;
import com.kaipai.module.model.card.dto.ActorMyShareCardItemDTO;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.actor.service.ActorProfileService;
import com.kaipai.module.server.ai.config.AiProfileCardProperties;
import com.kaipai.module.server.ai.mapper.ActorAiProfileCardPageMapper;
import com.kaipai.module.server.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.module.server.ai.profilecard.AiGeneratedImageStorage.CroppedImageBand;
import com.kaipai.module.server.ai.profilecard.AiProfileCardGeneration;
import com.kaipai.module.server.ai.profilecard.AiProfileCardImageQualityInspection;
import com.kaipai.module.server.ai.profilecard.AiProfileCardImageQualityInspector;
import com.kaipai.module.server.ai.profilecard.AiProfileCardPrompt;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;
import com.kaipai.module.server.ai.profilecard.AiProfileCardPromptAgent;
import com.kaipai.module.server.ai.service.AiImageProviderConfigService;
import com.kaipai.module.server.card.service.ActorCardConfigService;
import com.kaipai.module.server.card.service.UserShareCardService;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    void generateCoverPageShouldRetryWhenQualityGateRejectsFirstImage() {
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

        ActorAiProfileCardTask task = new ActorAiProfileCardTask();
        task.setTaskId("aipf_retry");
        task.setProviderCode("tencent-hunyuan");
        task.setTemplateSceneCode("costume");
        task.setStyleCode("costume");
        task.setSourceImageUrl("https://cdn.example.com/source.png");

        ActorAiProfileCardPage page = new ActorAiProfileCardPage();
        page.setPageId(7L);
        page.setTaskId(task.getTaskId());
        page.setPageType("cover");
        page.setPageNo(1);

        AiProfileCardPrompt prompt = new AiProfileCardPrompt("{}", "prompt", "negative");
        when(promptAgent.generatePage(
                any(ActorProfileDTO.class),
                anyString(),
                eq("tencent-hunyuan"),
                eq("costume"),
                eq("costume"),
                eq("https://cdn.example.com/source.png"),
                eq("cover"),
                eq(1)))
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
                "generateCoverPageWithQualityGate",
                new ActorProfileDTO(),
                task,
                page,
                "cover",
                1);

        assertEquals("https://cos.example.com/good.png", imageUrl);
        verify(promptAgent, times(2)).generatePage(
                any(ActorProfileDTO.class),
                anyString(),
                eq("tencent-hunyuan"),
                eq("costume"),
                eq("costume"),
                eq("https://cdn.example.com/source.png"),
                eq("cover"),
                eq(1));
        verify(qualityInspector, times(2)).inspectCover(anyString(), eq("tencent-hunyuan"));
    }

    @Test
    void runGenerationShouldPassTailReferenceBetweenPagesAndSkipRendererForPageTwoAndThree() {
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

        when(pageMapper.selectList(any())).thenReturn(List.of(
                newPage(1L, "aipf_flow", "cover"),
                newPage(2L, "aipf_flow", "resume"),
                newPage(3L, "aipf_flow", "gallery")));
        when(pageMapper.updateById(any(ActorAiProfileCardPage.class))).thenReturn(1);
        when(pageMapper.update(any(), any())).thenReturn(1);

        String coverTailReferenceUrl = "https://cdn.example.com/crop/cover-tail.png";
        String resumeTailReferenceUrl = "https://cdn.example.com/crop/resume-tail.png";
        List<GenerationCall> calls = new ArrayList<>();
        when(promptAgent.generatePage(
                any(ActorProfileDTO.class),
                anyString(),
                eq("tencent-hunyuan"),
                eq("classic"),
                eq("classic"),
                anyString(),
                anyString(),
                anyInt()))
                .thenAnswer(invocation -> {
                    String sourceImageUrl = invocation.getArgument(5, String.class);
                    String pageType = invocation.getArgument(6, String.class);
                    int pageNo = invocation.getArgument(7, Integer.class);
                    calls.add(new GenerationCall(pageType, pageNo, sourceImageUrl));
                    String tempImageUrl = switch (pageType) {
                        case "cover" -> "https://tmp.example.com/cover.png";
                        case "resume" -> "https://tmp.example.com/resume.png";
                        default -> "https://tmp.example.com/gallery.png";
                    };
                    return new AiProfileCardGeneration(
                            "tencent-hunyuan",
                            "hunyuan-image-3.0",
                            new AiProfileCardPrompt("{}", "prompt", "negative"),
                            AiProfileImageGenerationResult.imageUrl(tempImageUrl));
                });
        when(imageStorage.uploadFromUrl(anyString(), eq("ai-profile-card"))).thenAnswer(invocation -> {
            String imageUrl = invocation.getArgument(0, String.class);
            if ("https://tmp.example.com/cover.png".equals(imageUrl)) {
                return "https://cdn.example.com/cover.png";
            }
            if ("https://tmp.example.com/resume.png".equals(imageUrl)) {
                return "https://cdn.example.com/resume.png";
            }
            if ("https://tmp.example.com/gallery.png".equals(imageUrl)) {
                return "https://cdn.example.com/gallery.png";
            }
            return "https://cdn.example.com/persisted.png";
        });
        when(imageStorage.uploadBottomBandFromUrl(anyString(), eq("ai-profile-card/continuity"), anyDouble()))
                .thenAnswer(invocation -> {
                    String imageUrl = invocation.getArgument(0, String.class);
                    if ("https://cdn.example.com/cover.png".equals(imageUrl)) {
                        return new CroppedImageBand(coverTailReferenceUrl, 2160, 3840, 3264, 576, 0.15d);
                    }
                    if ("https://cdn.example.com/resume.png".equals(imageUrl)) {
                        return new CroppedImageBand(resumeTailReferenceUrl, 2160, 3840, 3264, 576, 0.15d);
                    }
                    return new CroppedImageBand("https://cdn.example.com/unused.png", 2160, 3840, 3264, 576, 0.15d);
                });

        ActorMyShareCardItemDTO card = new ActorMyShareCardItemDTO();
        card.setCardId(88L);
        when(userShareCardService.createCard(eq(7L), any())).thenReturn(card);

        initializePageTableInfo();
        ReflectionTestUtils.invokeMethod(service, "runGeneration", "aipf_flow");

        assertEquals(List.of(
                new GenerationCall("cover", 1, "https://cdn.example.com/source.png"),
                new GenerationCall("resume", 2, coverTailReferenceUrl),
                new GenerationCall("gallery", 3, resumeTailReferenceUrl)
        ), calls);
        verify(imageStorage, atLeastOnce()).uploadBottomBandFromUrl("https://cdn.example.com/cover.png", "ai-profile-card/continuity", 0.15d);
        verify(imageStorage, atLeastOnce()).uploadBottomBandFromUrl("https://cdn.example.com/resume.png", "ai-profile-card/continuity", 0.15d);
    }

    private ActorAiProfileCardTask newTask() {
        ActorAiProfileCardTask task = new ActorAiProfileCardTask();
        task.setTaskId("aipf_flow");
        task.setUserId(7L);
        task.setProviderCode("tencent-hunyuan");
        task.setTemplateSceneCode("classic");
        task.setStyleCode("classic");
        task.setSourceImageUrl("https://cdn.example.com/source.png");
        task.setStatus("pending");
        return task;
    }

    private ActorAiProfileCardPage newPage(Long pageId, String taskId, String pageType) {
        ActorAiProfileCardPage page = new ActorAiProfileCardPage();
        page.setPageId(pageId);
        page.setTaskId(taskId);
        page.setPageType(pageType);
        page.setPageNo(pageId.intValue());
        page.setProviderCode("tencent-hunyuan");
        page.setModelCode("hunyuan-image-3.0");
        page.setStatus("pending");
        return page;
    }

    private void initializePageTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "test"), ActorAiProfileCardPage.class);
    }

    private record GenerationCall(String pageType, int pageNo, String sourceImageUrl) {
    }
}
