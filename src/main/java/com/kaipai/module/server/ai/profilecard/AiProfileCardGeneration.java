package com.kaipai.module.server.ai.profilecard;

public record AiProfileCardGeneration(
        String providerCode,
        String modelCode,
        AiProfileCardPrompt prompt,
        AiProfileImageGenerationResult imageResult
) {
}
