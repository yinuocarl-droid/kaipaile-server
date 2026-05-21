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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProfileCardPromptAgentTest {

    @Test
    void generateShouldBuildSingleCoverPromptAndCallResolvedProvider() {
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
        assertTrue(generation.prompt().promptText().contains("固定主题背景色"));
        assertTrue(generation.prompt().promptText().contains("只提供第一屏视觉背景底图"));
        assertTrue(generation.prompt().negativePrompt().contains("watermark"));
        assertTrue(generation.prompt().negativePrompt().contains("图片由AI生成"));
        assertTrue(generation.prompt().negativePrompt().contains("AI GENERATED SHARE"));
        assertTrue(generation.prompt().negativePrompt().contains("海报标题"));
        assertTrue(generation.prompt().negativePrompt().contains("姓名文字"));
        assertTrue(generation.prompt().negativePrompt().contains("typography"));
        assertTrue(generation.prompt().negativePrompt().contains("random readable calligraphy"));
        assertTrue(generation.prompt().negativePrompt().contains("filled profile text"));
        assertTrue(generation.prompt().negativePrompt().contains("hard information card frames"));
        assertTrue(generation.prompt().negativePrompt().contains("paper sheet edge"));
        assertTrue(generation.prompt().negativePrompt().contains("corner ornament"));
        assertTrue(generation.prompt().negativePrompt().contains("drawn video player"));
        assertFalse(generation.prompt().promptText().contains("性别="));
        assertFalse(generation.prompt().promptText().contains("年龄="));
        assertFalse(generation.prompt().promptText().contains("城市="));
        assertFalse(generation.prompt().promptText().contains("技能="));

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
        assertTrue(request.promptJson().contains("\"targetSize\":\"2160x3840\""));
        assertTrue(request.promptJson().contains("\"referenceQuality\""));
        assertTrue(request.promptJson().contains("\"layoutCompliance\""));
        assertTrue(request.promptJson().contains("\"backgroundFramePolicy\""));
        assertTrue(request.promptJson().contains("\"textFreePolicy\""));
        assertTrue(request.promptJson().contains("\"sourceImageMode\":\"identity_reference\""));
        assertTrue(request.promptJson().contains("\"layoutPreset\":\"costume_profile_v3\""));
        assertTrue(request.promptJson().contains("\"panelTheme\":\"period-paper\""));
        assertTrue(request.promptJson().contains("\"task\":\"image_to_image_actor_profile_card_single_cover_background\""));
        assertTrue(request.promptJson().contains("\"singleCover\""));
        assertTrue(request.promptJson().contains("\"flowTheme\""));
        assertTrue(request.promptJson().contains("\"backgroundColor\":\"#efe0c4\""));
        assertTrue(request.promptJson().contains("full-bleed edge-to-edge background layer only"));
        assertTrue(request.promptJson().contains("no typography anywhere"));
        assertTrue(request.promptJson().contains("quiet render-safe zones are mandatory in every style"));
        assertTrue(request.promptJson().contains("\"facts\""));
        assertTrue(request.promptJson().contains("\"video\""));
        assertTrue(request.promptJson().contains("design x="));
        assertTrue(request.promptJson().contains("provider x="));
        assertFalse(request.promptJson().contains("\"pageType\":\"resume\""));
        assertFalse(request.promptJson().contains("\"pageType\":\"gallery\""));
        assertFalse(request.promptJson().contains("tail_reference"));
        assertFalse(request.promptJson().contains("continuity"));
        assertFalse(request.promptText().contains("第1/3"));
        assertFalse(request.promptText().contains("上一页"));
        assertFalse(request.promptText().contains("resume"));
        assertFalse(request.promptText().contains("gallery"));
        assertFalse(request.promptText().contains("连续性"));
        assertFalse(request.promptText().contains("演员分享"));
        assertFalse(request.promptText().contains("分享封面"));
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
        assertTrue(request.promptJson().contains("\"textFreePolicy\""));
        assertTrue(request.promptJson().contains("\"designCanvas\""));
        assertTrue(request.promptJson().contains("\"providerCanvas\""));
        assertTrue(request.promptJson().contains("\"singleCover\""));
        assertTrue(request.promptJson().contains("\"flowTheme\""));
        assertTrue(request.promptJson().contains("\"flowBackgroundColor\""));
        assertTrue(request.promptJson().contains("\"identitySafeArea\""));
        assertTrue(request.promptJson().contains("\"safeSurfaceTone\""));
        assertTrue(request.promptJson().contains("\"backgroundColor\""));
        if ("urban".equals(scene) || "artistic".equals(scene)) {
            assertTrue(request.promptJson().contains("\"textTheme\":\"cinema-light\""));
        }
        assertFalse(request.promptJson().contains("\"pageType\":\"resume\""));
        assertFalse(request.promptJson().contains("\"pageType\":\"gallery\""));
        assertFalse(request.promptJson().contains("tail_reference"));
        assertFalse(request.promptJson().contains("continuity"));
    }

    private void assertChinesePromptContract(String promptText) {
        assertNotNull(promptText);
        assertTrue(promptText.contains("9:16"));
        assertTrue(promptText.contains("背景底图"));
        assertTrue(promptText.contains("构图"));
        assertTrue(promptText.contains("风格"));
        assertTrue(promptText.contains("背景"));
        assertTrue(promptText.contains("图中不要出现姓名、资料、标签、按钮、卡片、列表、排版块或任何假 UI"));
        assertTrue(promptText.contains("固定主题背景色"));
        assertTrue(promptText.contains("禁止出现任何可读字符"));
        assertTrue(promptText.contains("中文"));
        assertTrue(promptText.contains("英文"));
        assertTrue(promptText.contains("数字"));
        assertTrue(promptText.contains("图片由AI生成"));
        assertTrue(promptText.contains("水印"));
        assertTrue(promptText.contains("Logo") || promptText.contains("logo"));
        assertTrue(promptText.contains("标签"));
        assertTrue(promptText.contains("二维码"));
        assertTrue(promptText.contains("UI") || promptText.contains("前景组件"));
        assertTrue(promptText.trim().endsWith("Plain background image only, no typography, no captions, no watermark, no logo."));
    }

    private void assertCoverPromptContract(String promptText) {
        assertChinesePromptContract(promptText);
        assertTrue(promptText.contains("第一屏视觉背景底图"));
        assertTrue(promptText.contains("左侧保持干净、低细节、无字符"));
        assertTrue(promptText.contains("自然过渡到固定主题背景色"));
        assertTrue(promptText.contains("图中不要出现姓名、资料、标签、按钮、卡片、列表、排版块或任何假 UI"));
        assertTrue(promptText.contains("全幅铺满"));
        assertTrue(promptText.contains("禁止出现任何可读字符"));
        assertFalse(promptText.contains("第1/3"));
        assertFalse(promptText.contains("上一页"));
        assertFalse(promptText.contains("resume"));
        assertFalse(promptText.contains("gallery"));
        assertFalse(promptText.contains("连续性"));
        assertFalse(promptText.contains("tail_reference"));
        assertFalse(promptText.contains("分享封面背景"));
        assertFalse(promptText.contains("演员分享"));
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
