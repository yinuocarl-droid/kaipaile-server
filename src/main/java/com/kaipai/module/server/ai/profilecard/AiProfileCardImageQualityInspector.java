package com.kaipai.module.server.ai.profilecard;

public interface AiProfileCardImageQualityInspector {

    AiProfileCardImageQualityInspection inspectCover(String imageUrl, String generationProviderCode);
}
