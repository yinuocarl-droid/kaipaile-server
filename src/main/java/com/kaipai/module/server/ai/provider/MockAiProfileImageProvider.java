package com.kaipai.module.server.ai.provider;

import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;
import org.springframework.stereotype.Component;

@Component
public class MockAiProfileImageProvider implements AiProfileImageProvider {

    @Override
    public String providerCode() {
        return "mock";
    }

    @Override
    public String modelCode() {
        return "mock-profile-card-v1";
    }

    @Override
    public AiProfileImageGenerationResult generate(AiProfileImageGenerationRequest request) {
        return AiProfileImageGenerationResult.imageUrl(request.sourceImageUrl());
    }
}
