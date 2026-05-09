package com.kaipai.module.server.ai.profilecard;

public record AiProfileImageGenerationResult(
        String imageUrl,
        byte[] imageBytes,
        String contentType
) {

    public static AiProfileImageGenerationResult imageUrl(String imageUrl) {
        return new AiProfileImageGenerationResult(imageUrl, null, null);
    }

    public static AiProfileImageGenerationResult imageBytes(byte[] imageBytes, String contentType) {
        return new AiProfileImageGenerationResult(null, imageBytes, contentType);
    }
}
