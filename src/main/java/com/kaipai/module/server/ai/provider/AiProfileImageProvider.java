package com.kaipai.module.server.ai.provider;

import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;

public interface AiProfileImageProvider {

    String providerCode();

    String modelCode();

    AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request);
}
