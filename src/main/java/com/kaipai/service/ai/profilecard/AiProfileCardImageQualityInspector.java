package com.kaipai.service.ai.profilecard;

public interface AiProfileCardImageQualityInspector {

    AiProfileCardImageQualityInspection inspectCover(String imageUrl, String generationProviderCode);
}
