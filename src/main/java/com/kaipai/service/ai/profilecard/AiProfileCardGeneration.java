package com.kaipai.service.ai.profilecard;

public record AiProfileCardGeneration(
        String providerCode,
        String modelCode,
        AiProfileCardPrompt prompt,
        AiProfileImageGenerationResult imageResult
) {
}
