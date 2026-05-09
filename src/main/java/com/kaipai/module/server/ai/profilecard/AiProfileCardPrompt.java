package com.kaipai.module.server.ai.profilecard;

public record AiProfileCardPrompt(
        String promptJson,
        String promptText,
        String negativePrompt
) {
}
