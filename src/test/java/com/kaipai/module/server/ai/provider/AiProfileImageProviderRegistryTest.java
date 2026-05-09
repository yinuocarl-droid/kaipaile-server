package com.kaipai.module.server.ai.provider;

import com.kaipai.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiProfileImageProviderRegistryTest {

    @Test
    void resolveShouldRejectMockProviderCode() {
        AiProfileImageProviderRegistry registry = new AiProfileImageProviderRegistry(List.of(new StaticProvider("kplyyk")));

        BizException error = assertThrows(BizException.class, () -> registry.resolve("mock"));

        assertEquals("AI 分享图禁止使用 mock provider，请配置真实生图 provider", error.getMessage());
    }

    @Test
    void resolveShouldDefaultToKplyykProvider() {
        AiProfileImageProvider provider = new StaticProvider("kplyyk");
        AiProfileImageProviderRegistry registry = new AiProfileImageProviderRegistry(List.of(provider));

        assertEquals(provider, registry.resolve(""));
    }

    private record StaticProvider(String providerCode) implements AiProfileImageProvider {
        @Override
        public String modelCode() {
            return providerCode + "-model";
        }

        @Override
        public com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult generate(
                com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationRequest request) {
            return com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult.imageUrl("https://cdn.kplyyk.com/generated.png");
        }
    }
}
