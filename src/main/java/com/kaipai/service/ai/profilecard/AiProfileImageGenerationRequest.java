package com.kaipai.service.ai.profilecard;

public record AiProfileImageGenerationRequest(
        String taskId,
        String modelCode,
        String templateSceneCode,
        String styleCode,
        String sourceImageUrl,
        String promptText,
        String negativePrompt,
        String promptJson
) {
}
