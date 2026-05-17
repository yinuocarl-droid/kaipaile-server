package com.kaipai.module.server.ai.profilecard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.server.ai.provider.AiProfileImageProvider;
import com.kaipai.module.server.ai.provider.AiProfileImageProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(generation.prompt().promptText().contains("target 2160x3840"));
        assertTrue(generation.prompt().promptText().contains("mini-program design canvas 750x1334"));
        assertTrue(generation.prompt().promptText().contains("style-specific hero subject area"));
        assertTrue(generation.prompt().promptText().contains("styleCode=costume_actor_profile_full_card"));
        assertTrue(generation.prompt().promptText().contains("Mini program native components"));
        assertTrue(generation.prompt().promptText().contains("layoutPreset=costume_profile_v3"));
        assertTrue(generation.prompt().promptText().contains("full-bleed edge-to-edge background layer only"));
        assertTrue(generation.prompt().promptText().contains("warm low-detail ink-wash matte"));
        assertTrue(generation.prompt().promptText().contains("Do not draw hard information cards"));
        assertTrue(generation.prompt().promptText().contains("foreground components"));
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

        assertSceneContract(agent, provider, profile, "classic", "classic_profile_v3", "paper", "warm studio");
        assertSceneContract(agent, provider, profile, "costume", "costume_profile_v3", "period-paper", "warm ink-wash");
        assertSceneContract(agent, provider, profile, "urban", "urban_profile_v3", "cinema-glass", "no parchment");
        assertSceneContract(agent, provider, profile, "commercial", "commercial_profile_v3", "studio-light", "clean studio");
        assertSceneContract(agent, provider, profile, "artistic", "artistic_profile_v3", "gallery-glass", "gallery");
    }

    private void assertSceneContract(AiProfileCardPromptAgent agent,
                                     CapturingProvider provider,
                                     ActorProfileDTO profile,
                                     String scene,
                                     String layoutPreset,
                                     String panelTheme,
                                     String expectedPromptSignal) {
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
        assertTrue(request.promptText().contains("layoutPreset=" + layoutPreset));
        assertTrue(request.promptText().contains("mini-program design canvas 750x1334"));
        assertTrue(request.promptText().contains("Background boundary policy"));
        assertTrue(request.promptText().contains("full-bleed edge-to-edge background layer only"));
        assertTrue(request.promptText().contains(expectedPromptSignal));
        assertTrue(request.promptJson().contains("\"layoutPreset\":\"" + layoutPreset + "\""));
        assertTrue(request.promptJson().contains("\"panelTheme\":\"" + panelTheme + "\""));
        assertTrue(request.promptJson().contains("\"backgroundFramePolicy\""));
        assertTrue(request.promptJson().contains("\"designCanvas\""));
        assertTrue(request.promptJson().contains("\"providerCanvas\""));
        assertTrue(request.promptJson().contains("\"identity\""));
        assertTrue(request.promptJson().contains("\"intro\""));
        assertTrue(request.promptJson().contains("\"video\""));
        if ("urban".equals(scene) || "artistic".equals(scene)) {
            assertTrue(request.promptJson().contains("\"textTheme\":\"cinema-light\""));
            assertFalse(request.promptText().contains("pale parchment"));
            assertFalse(request.promptText().contains("Chinese period actor profile sheet"));
        }
        if ("classic".equals(scene) || "costume".equals(scene)) {
            assertFalse(request.promptText().contains("dossier"));
            assertFalse(request.promptText().contains("profile sheet"));
            assertFalse(request.promptText().contains("document cards"));
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

        AiProfileCardGeneration resume = agent.generatePage(
                profile,
                "aipf_test_resume",
                "kplyyk",
                "classic",
                "classic",
                "https://cdn.kplyyk.com/source.png",
                "resume",
                2);

        assertEquals("kplyyk", resume.providerCode());
        AiProfileImageGenerationRequest resumeRequest = provider.lastRequest.get();
        assertNotNull(resumeRequest);
        assertEquals("aipf_test_resume", resumeRequest.taskId());
        assertTrue(resumeRequest.promptText().contains("pageNo=2/3, pageType=resume"));
        assertTrue(resumeRequest.promptText().contains("resume information expansion page"));
        assertTrue(resumeRequest.promptText().contains("Do not reproduce the actor"));
        assertTrue(resumeRequest.promptText().contains("no person subject"));
        assertTrue(resumeRequest.promptJson().contains("\"pageType\":\"resume\""));
        assertTrue(resumeRequest.promptJson().contains("\"sourceImageMode\":\"loose_palette_reference_only_no_identity\""));
        assertTrue(resumeRequest.promptJson().contains("\"workTimeline\""));
        assertTrue(resumeRequest.promptJson().contains("\"languages\""));
        assertEquals("https://cdn.kplyyk.com/source.png", resumeRequest.sourceImageUrl());

        AiProfileCardGeneration gallery = agent.generatePage(
                profile,
                "aipf_test_gallery",
                "kplyyk",
                "classic",
                "classic",
                "https://cdn.kplyyk.com/source.png",
                "gallery",
                3);

        assertEquals("kplyyk", gallery.providerCode());
        AiProfileImageGenerationRequest galleryRequest = provider.lastRequest.get();
        assertNotNull(galleryRequest);
        assertEquals("aipf_test_gallery", galleryRequest.taskId());
        assertTrue(galleryRequest.promptText().contains("pageNo=3/3, pageType=gallery"));
        assertTrue(galleryRequest.promptText().contains("gallery page for real profile photos"));
        assertTrue(galleryRequest.promptText().contains("Do not reproduce the actor"));
        assertTrue(galleryRequest.promptText().contains("no person subject"));
        assertTrue(galleryRequest.promptJson().contains("\"pageType\":\"gallery\""));
        assertTrue(galleryRequest.promptJson().contains("\"sourceImageMode\":\"loose_palette_reference_only_no_identity\""));
        assertTrue(galleryRequest.promptJson().contains("\"portraitPhotos\""));
        assertTrue(galleryRequest.promptJson().contains("\"workPhotos\""));
        assertEquals("https://cdn.kplyyk.com/source.png", galleryRequest.sourceImageUrl());
    }

    @Test
    void generatePageShouldOmitSourceImageForTencentLayoutPages() {
        CapturingProvider provider = new CapturingProvider("tencent-hunyuan", "hunyuan-image-3.0");
        AiProfileCardPromptAgent agent = new AiProfileCardPromptAgent(
                new ObjectMapper(),
                new AiProfileImageProviderRegistry(List.of(provider)));
        ActorProfileDTO profile = new ActorProfileDTO();
        profile.setGender("female");
        profile.setAge(24);
        profile.setHeight(168);

        agent.generatePage(
                profile,
                "aipf_test_resume",
                "tencent-hunyuan",
                "costume",
                "costume_actor_profile_full_card",
                "https://cdn.kplyyk.com/source.png",
                "resume",
                2);

        AiProfileImageGenerationRequest resumeRequest = provider.lastRequest.get();
        assertNotNull(resumeRequest);
        assertEquals("", resumeRequest.sourceImageUrl());
        assertTrue(resumeRequest.promptText().contains("layout-only information page background"));
        assertTrue(resumeRequest.promptText().contains("Do not create or place any actor portrait"));
        assertTrue(resumeRequest.promptJson().contains("\"sourceImageMode\":\"none\""));

        agent.generatePage(
                profile,
                "aipf_test_cover",
                "tencent-hunyuan",
                "costume",
                "costume_actor_profile_full_card",
                "https://cdn.kplyyk.com/source.png",
                "cover",
                1);

        AiProfileImageGenerationRequest coverRequest = provider.lastRequest.get();
        assertNotNull(coverRequest);
        assertEquals("https://cdn.kplyyk.com/source.png", coverRequest.sourceImageUrl());
        assertTrue(coverRequest.promptText().contains("Preserve the actor's recognizable face"));
        assertTrue(coverRequest.promptJson().contains("\"sourceImageMode\":\"identity_reference\""));
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
