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
        assertTrue(generation.prompt().promptText().contains("target 2160x3840"));
        assertTrue(generation.prompt().promptText().contains("hero right area"));
        assertTrue(generation.prompt().promptText().contains("styleCode=costume_actor_profile_full_card"));
        assertTrue(generation.prompt().promptText().contains("Mini program native components"));
        assertTrue(generation.prompt().promptText().contains("high-quality Chinese period actor profile sheet"));
        assertTrue(generation.prompt().promptText().contains("antique-gold double-line borders"));
        assertTrue(generation.prompt().promptText().contains("portrait thumbnail strip"));
        assertTrue(generation.prompt().negativePrompt().contains("watermark"));
        assertTrue(generation.prompt().negativePrompt().contains("random readable calligraphy"));
        assertTrue(generation.prompt().negativePrompt().contains("filled profile text"));

        AiProfileImageGenerationRequest request = provider.lastRequest.get();
        assertNotNull(request);
        assertEquals("aipf_test", request.taskId());
        assertEquals("gpt-image-2", request.modelCode());
        assertEquals("classic", request.templateSceneCode());
        assertEquals("costume_actor_profile_full_card", request.styleCode());
        assertEquals("https://cdn.kplyyk.com/source.png", request.sourceImageUrl());
        assertTrue(request.promptJson().contains("\"targetSize\":\"2160x3840\""));
        assertTrue(request.promptJson().contains("\"referenceQuality\""));
        assertTrue(request.promptJson().contains("premium Chinese period actor profile sheet"));
        assertTrue(request.promptJson().contains("\"profilePanelRegion\""));
        assertTrue(request.promptJson().contains("\"statsRegion\""));
        assertTrue(request.promptJson().contains("six clean rounded portrait thumbnail frames"));
        assertTrue(request.promptJson().contains("\"photoStripRegion\""));
    }

    private static final class CapturingProvider implements AiProfileImageProvider {
        private final AtomicReference<AiProfileImageGenerationRequest> lastRequest = new AtomicReference<>();

        @Override
        public String providerCode() {
            return "kplyyk";
        }

        @Override
        public String modelCode() {
            return "gpt-image-2";
        }

        @Override
        public AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request) {
            lastRequest.set(request);
            return AiProfileImageGenerationResult.imageUrl("https://cdn.kplyyk.com/generated.png");
        }
    }
}
