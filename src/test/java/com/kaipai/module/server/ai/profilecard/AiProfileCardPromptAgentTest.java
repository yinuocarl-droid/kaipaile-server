package com.kaipai.module.server.ai.profilecard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.server.ai.provider.AiProfileImageProvider;
import com.kaipai.module.server.ai.provider.AiProfileImageProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProfileCardPromptAgentTest {

    @Test
    void generateShouldBuildPromptAndCallResolvedProvider() {
        CapturingProvider provider = new CapturingProvider();
        AiProfileCardPromptAgent agent = new AiProfileCardPromptAgent(
                new ObjectMapper(),
                new AiProfileImageProviderRegistry(List.of(provider)));
        ActorProfileDTO profile = new ActorProfileDTO();
        profile.setGender("female");
        profile.setAge(24);
        profile.setHeight(168);
        profile.setCity("上海");
        profile.setSkillTypes(List.of("表演", "舞蹈"));

        AiProfileCardGeneration generation = agent.generate(
                profile,
                "aipf_test",
                "kplyyk",
                "classic",
                "costume_actor_profile_full_card",
                "https://cdn.kplyyk.com/source.png");

        assertEquals("kplyyk", generation.providerCode());
        assertEquals("gpt-image-2", generation.modelCode());
        assertEquals("https://cdn.kplyyk.com/generated.png", generation.imageResult().imageUrl());
        assertNotNull(generation.prompt());
        assertCoverPromptContract(generation.prompt().promptText());
        assertTrue(generation.prompt().promptText().contains("9:16"));
        assertTrue(generation.prompt().promptText().contains("2160x3840"));
        assertTrue(generation.prompt().negativePrompt().contains("watermark"));
        assertTrue(generation.prompt().negativePrompt().contains("random readable calligraphy"));
        assertTrue(generation.prompt().negativePrompt().contains("filled profile text"));
        assertTrue(generation.prompt().negativePrompt().contains("hard information card frames"));
        assertTrue(generation.prompt().negativePrompt().contains("paper sheet edge"));
        assertTrue(generation.prompt().negativePrompt().contains("corner ornament"));
        assertTrue(generation.prompt().negativePrompt().contains("drawn video player"));

        AiProfileImageGenerationRequest request = provider.lastRequest.get();
        assertNotNull(request);
        assertEquals("aipf_test", request.taskId());
        assertEquals("gpt-image-2", request.modelCode());
        assertEquals("classic", request.templateSceneCode());
        assertEquals("costume_actor_profile_full_card", request.styleCode());
        assertEquals("https://cdn.kplyyk.com/source.png", request.sourceImageUrl());
        assertTrue(request.promptJson().contains("\"designCanvas\""));
        assertTrue(request.promptJson().contains("\"providerCanvas\""));
        assertTrue(request.promptJson().contains("\"coordinatePolicy\""));
        assertTrue(request.promptJson().contains("\"unit\":\"mini-program rpx logical design coordinate\""));
        assertTrue(request.promptJson().contains("\"targetSize\":\"2160x3840\""));
        assertTrue(request.promptJson().contains("\"referenceQuality\""));
        assertTrue(request.promptJson().contains("\"layoutCompliance\""));
        assertTrue(request.promptJson().contains("\"backgroundFramePolicy\""));
        assertTrue(request.promptJson().contains("\"sourceImageMode\":\"identity_reference\""));
        assertTrue(request.promptJson().contains("\"layoutPreset\":\"costume_profile_v3\""));
        assertTrue(request.promptJson().contains("\"panelTheme\":\"period-paper\""));
        assertTrue(request.promptJson().contains("full-bleed edge-to-edge background layer only"));
        assertTrue(request.promptJson().contains("quiet render-safe zones are mandatory in every style"));
        assertTrue(request.promptJson().contains("\"facts\""));
        assertTrue(request.promptJson().contains("\"video\""));
        assertTrue(request.promptJson().contains("no thumbnail frames"));
        assertTrue(request.promptJson().contains("design x="));
        assertTrue(request.promptJson().contains("provider x="));
    }

    @Test
    void generateShouldUseStyleSpecificContractForEveryScene() {
        CapturingProvider provider = new CapturingProvider();
        AiProfileCardPromptAgent agent = new AiProfileCardPromptAgent(
                new ObjectMapper(),
                new AiProfileImageProviderRegistry(List.of(provider)));
        ActorProfileDTO profile = new ActorProfileDTO();
        profile.setGender("female");
        profile.setAge(24);
        profile.setHeight(168);
        profile.setCity("上海");
        profile.setSkillTypes(List.of("影视表演", "短剧"));

        assertSceneContract(agent, provider, profile, "classic", "classic_profile_v3", "paper");
        assertSceneContract(agent, provider, profile, "costume", "costume_profile_v3", "period-paper");
        assertSceneContract(agent, provider, profile, "urban", "urban_profile_v3", "cinema-glass");
        assertSceneContract(agent, provider, profile, "commercial", "commercial_profile_v3", "studio-light");
        assertSceneContract(agent, provider, profile, "artistic", "artistic_profile_v3", "gallery-glass");
    }

    private void assertSceneContract(AiProfileCardPromptAgent agent,
                                     CapturingProvider provider,
                                     ActorProfileDTO profile,
                                     String scene,
                                     String layoutPreset,
                                     String panelTheme) {
        agent.generate(
                profile,
                "aipf_" + scene,
                "kplyyk",
                scene,
                scene,
                "https://cdn.kplyyk.com/source.png");

        AiProfileImageGenerationRequest request = provider.lastRequest.get();
        assertNotNull(request);
        assertEquals(scene, request.templateSceneCode());
        assertCoverPromptContract(request.promptText());
        assertTrue(request.promptJson().contains("\"layoutPreset\":\"" + layoutPreset + "\""));
        assertTrue(request.promptJson().contains("\"panelTheme\":\"" + panelTheme + "\""));
        assertTrue(request.promptJson().contains("\"sourceImageMode\":\"identity_reference\""));
        assertTrue(request.promptJson().contains("\"backgroundFramePolicy\""));
        assertTrue(request.promptJson().contains("\"designCanvas\""));
        assertTrue(request.promptJson().contains("\"providerCanvas\""));
        assertTrue(request.promptJson().contains("\"identity\""));
        assertTrue(request.promptJson().contains("\"intro\""));
        assertTrue(request.promptJson().contains("\"video\""));
        if ("urban".equals(scene) || "artistic".equals(scene)) {
            assertTrue(request.promptJson().contains("\"textTheme\":\"cinema-light\""));
        }
    }

    @Test
    void generatePageShouldBuildIndependentAlbumPageContracts() {
        CapturingProvider provider = new CapturingProvider();
        AiProfileCardPromptAgent agent = new AiProfileCardPromptAgent(
                new ObjectMapper(),
                new AiProfileImageProviderRegistry(List.of(provider)));
        ActorProfileDTO profile = new ActorProfileDTO();
        profile.setGender("female");
        profile.setAge(24);
        profile.setHeight(168);
        profile.setCity("上海");
        profile.setSkillTypes(List.of("影视表演", "短剧"));

        String coverTailReferenceUrl = "https://cdn.kplyyk.com/ai-profile-card/cover-tail-band.png";
        String resumeTailReferenceUrl = "https://cdn.kplyyk.com/ai-profile-card/resume-tail-band.png";

        AiProfileCardGeneration resume = agent.generatePage(
                profile,
                "aipf_test_resume",
                "kplyyk",
                "classic",
                "classic",
                coverTailReferenceUrl,
                "resume",
                2);

        assertEquals("kplyyk", resume.providerCode());
        AiProfileImageGenerationRequest resumeRequest = provider.lastRequest.get();
        assertNotNull(resumeRequest);
        assertEquals("aipf_test_resume", resumeRequest.taskId());
        assertContinuityPromptContract(resumeRequest.promptText());
        assertTrue(resumeRequest.promptJson().contains("\"pageType\":\"resume\""));
        assertTrue(resumeRequest.promptJson().contains("\"sourceImageMode\":\"tail_reference_only_no_identity\""));
        assertTrue(resumeRequest.promptJson().contains("\"workTimeline\""));
        assertTrue(resumeRequest.promptJson().contains("\"languages\""));
        assertEquals(coverTailReferenceUrl, resumeRequest.sourceImageUrl());

        AiProfileCardGeneration gallery = agent.generatePage(
                profile,
                "aipf_test_gallery",
                "kplyyk",
                "classic",
                "classic",
                resumeTailReferenceUrl,
                "gallery",
                3);

        assertEquals("kplyyk", gallery.providerCode());
        AiProfileImageGenerationRequest galleryRequest = provider.lastRequest.get();
        assertNotNull(galleryRequest);
        assertEquals("aipf_test_gallery", galleryRequest.taskId());
        assertContinuityPromptContract(galleryRequest.promptText());
        assertTrue(galleryRequest.promptJson().contains("\"pageType\":\"gallery\""));
        assertTrue(galleryRequest.promptJson().contains("\"sourceImageMode\":\"tail_reference_only_no_identity\""));
        assertTrue(galleryRequest.promptJson().contains("\"portraitPhotos\""));
        assertTrue(galleryRequest.promptJson().contains("\"workPhotos\""));
        assertEquals(resumeTailReferenceUrl, galleryRequest.sourceImageUrl());
    }

    @Test
    void generatePageShouldPassReferenceImageToTencentNonCoverPages() {
        CapturingProvider provider = new CapturingProvider("tencent-hunyuan", "hunyuan-image-3.0");
        AiProfileCardPromptAgent agent = new AiProfileCardPromptAgent(
                new ObjectMapper(),
                new AiProfileImageProviderRegistry(List.of(provider)));
        ActorProfileDTO profile = new ActorProfileDTO();
        profile.setGender("female");
        profile.setAge(24);
        profile.setHeight(168);

        String sourceImageUrl = "https://cdn.kplyyk.com/source.png";
        String coverTailReferenceUrl = "https://cdn.kplyyk.com/ai-profile-card/cover-tail-band.png";
        String resumeTailReferenceUrl = "https://cdn.kplyyk.com/ai-profile-card/resume-tail-band.png";

        agent.generatePage(
                profile,
                "aipf_test_cover",
                "tencent-hunyuan",
                "costume",
                "costume_actor_profile_full_card",
                sourceImageUrl,
                "cover",
                1);

        AiProfileImageGenerationRequest coverRequest = provider.lastRequest.get();
        assertNotNull(coverRequest);
        assertEquals(sourceImageUrl, coverRequest.sourceImageUrl());
        assertCoverPromptContract(coverRequest.promptText());
        assertTrue(coverRequest.promptJson().contains("\"sourceImageMode\":\"identity_reference\""));

        agent.generatePage(
                profile,
                "aipf_test_resume",
                "tencent-hunyuan",
                "costume",
                "costume_actor_profile_full_card",
                coverTailReferenceUrl,
                "resume",
                2);

        AiProfileImageGenerationRequest resumeRequest = provider.lastRequest.get();
        assertNotNull(resumeRequest);
        assertEquals(coverTailReferenceUrl, resumeRequest.sourceImageUrl());
        assertContinuityPromptContract(resumeRequest.promptText());
        assertTrue(resumeRequest.promptJson().contains("\"sourceImageMode\":\"tail_reference_only_no_identity\""));

        agent.generatePage(
                profile,
                "aipf_test_gallery",
                "tencent-hunyuan",
                "costume",
                "costume_actor_profile_full_card",
                resumeTailReferenceUrl,
                "gallery",
                3);

        AiProfileImageGenerationRequest galleryRequest = provider.lastRequest.get();
        assertNotNull(galleryRequest);
        assertEquals(resumeTailReferenceUrl, galleryRequest.sourceImageUrl());
        assertContinuityPromptContract(galleryRequest.promptText());
        assertTrue(galleryRequest.promptJson().contains("\"sourceImageMode\":\"tail_reference_only_no_identity\""));
    }

    private void assertChinesePromptContract(String promptText) {
        assertNotNull(promptText);
        assertTrue(promptText.contains("9:16"));
        assertTrue(promptText.contains("构图"));
        assertTrue(promptText.contains("风格"));
        assertTrue(promptText.contains("背景"));
        assertTrue(promptText.contains("不要可读文字") || promptText.contains("不要人物") || promptText.contains("不要文字"));
        assertTrue(promptText.contains("水印"));
        assertTrue(promptText.contains("Logo") || promptText.contains("logo"));
        assertTrue(promptText.contains("标签"));
        assertTrue(promptText.contains("二维码"));
        assertTrue(promptText.contains("UI形状") || promptText.contains("UI 形状"));
        assertTrue(promptText.trim().endsWith("Plain, unmarked, symbol-free."));
    }

    private void assertCoverPromptContract(String promptText) {
        assertChinesePromptContract(promptText);
        assertTrue(promptText.contains("底部约 15%") || promptText.contains("底部 15%"));
        assertTrue(promptText.contains("干净"));
        assertTrue(promptText.contains("低细节"));
        assertTrue(promptText.contains("无人物身体"));
        assertTrue(promptText.contains("无衣料主体"));
        assertTrue(promptText.contains("无文字"));
        assertTrue(promptText.contains("无 UI"));
        assertTrue(promptText.contains("可延展背景过渡带") || promptText.contains("可延展的背景过渡带"));
    }

    private void assertContinuityPromptContract(String promptText) {
        assertChinesePromptContract(promptText);
        assertTrue(promptText.contains("顶部") || promptText.contains("上方"));
        assertTrue(promptText.contains("15%"));
        assertTrue(promptText.contains("沿用上一页底部")
                || promptText.contains("延续上一页底部")
                || promptText.contains("延续上一页结尾")
                || promptText.contains("上一页底部裁切"));
        assertTrue(promptText.contains("主要形状"));
        assertTrue(promptText.contains("色彩"));
        assertTrue(promptText.contains("光线"));
        assertTrue(promptText.contains("纹理"));
        assertTrue(promptText.contains("空间方向"));
        assertTrue(promptText.contains("像直接从上一页底部继续向下生成"));
        assertTrue(promptText.contains("不是只保持同风格")
                || promptText.contains("不要替换成普通墙面")
                || promptText.contains("全新背景"));
        assertTrue(promptText.contains("不复制人物") || promptText.contains("不要人物"));
        assertTrue(promptText.contains("文字"));
        assertTrue(promptText.contains("Logo") || promptText.contains("logo"));
        assertTrue(promptText.contains("二维码"));
        assertTrue(promptText.contains("前景布局") || promptText.contains("UI形状") || promptText.contains("UI 形状"));
    }

    private static final class CapturingProvider implements AiProfileImageProvider {
        private final AtomicReference<AiProfileImageGenerationRequest> lastRequest = new AtomicReference<>();
        private final String providerCode;
        private final String modelCode;

        private CapturingProvider() {
            this("kplyyk", "gpt-image-2");
        }

        private CapturingProvider(String providerCode, String modelCode) {
            this.providerCode = providerCode;
            this.modelCode = modelCode;
        }

        @Override
        public String providerCode() {
            return providerCode;
        }

        @Override
        public String modelCode() {
            return modelCode;
        }

        @Override
        public AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request) {
            lastRequest.set(request);
            return AiProfileImageGenerationResult.imageUrl("https://cdn.kplyyk.com/generated.png");
        }
    }
}
