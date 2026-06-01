package com.kaipai.integration.ai.provider;

import com.kaipai.service.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.service.ai.profilecard.AiProfileImageGenerationResult;

public interface AiProfileImageProvider {

    String providerCode();

    String modelCode();

    AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request);
}
