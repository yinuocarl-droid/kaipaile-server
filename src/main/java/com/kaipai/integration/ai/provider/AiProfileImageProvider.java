package com.kaipai.integration.ai.provider;

import com.kaipai.service.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationResult;

public interface AiProfileImageProvider {

    String providerCode();

    String modelCode();

    /**
     * Declares only request shapes that this provider's concrete writer really emits.
     * Providers remain single-reference unless they explicitly opt in.
     */
    default boolean supportsReferenceImageCount(int referenceImageCount) {
        return referenceImageCount == 1;
    }

    AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request);
}
