package com.kaipai.module.server.ai.service.impl;

import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.ai.entity.ActorAiProfileCardPage;
import com.kaipai.module.model.ai.entity.ActorAiProfileCardTask;
import com.kaipai.module.server.ai.config.AiProfileCardProperties;
import com.kaipai.module.server.ai.mapper.ActorAiProfileCardPageMapper;
import com.kaipai.module.server.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.module.server.ai.profilecard.AiProfileCardGeneration;
import com.kaipai.module.server.ai.profilecard.AiProfileCardImageQualityInspection;
import com.kaipai.module.server.ai.profilecard.AiProfileCardImageQualityInspector;
import com.kaipai.module.server.ai.profilecard.AiProfileCardPrompt;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;
import com.kaipai.module.server.ai.profilecard.AiProfileCardPromptAgent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiProfileCardServiceImplTest {

    @Test
    void resolveGeneratedImageUrlShouldRejectSourceImageEcho() {
        AiProfileCardServiceImpl service = new AiProfileCardServiceImpl(null, null, null, null, null, null, null, null, null, null, null);
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
                null,
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
}
