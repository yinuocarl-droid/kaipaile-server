package com.kaipai.service.ai.profilecard;

/** Provider-neutral request supporting one independent person and background reference. */
public record AiProfileImageGenerationRequest(
        String taskId,
        String modelCode,
        String templateSceneCode,
        String styleCode,
        String sourceImageUrl,
        String backgroundImageUrl,
        String promptText,
        String negativePrompt,
        String promptJson
) {

    /** Retains source compatibility for the existing single-reference pipeline. */
    public AiProfileImageGenerationRequest(
            String taskId,
            String modelCode,
            String templateSceneCode,
            String styleCode,
            String sourceImageUrl,
            String promptText,
            String negativePrompt,
            String promptJson) {
        this(taskId, modelCode, templateSceneCode, styleCode, sourceImageUrl, null,
                promptText, negativePrompt, promptJson);
    }
}
