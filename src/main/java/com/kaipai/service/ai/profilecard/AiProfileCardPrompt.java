package com.kaipai.service.ai.profilecard;

public record AiProfileCardPrompt(
        String promptJson,
        String promptText,
        String negativePrompt
) {
}
