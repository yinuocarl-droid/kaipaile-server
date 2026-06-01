package com.kaipai.integration.ai.provider;

import org.springframework.stereotype.Component;

@Component
public class AliyunWanxiangProfileImageProvider extends AliyunQwenImageProfileImageProvider {

    public AliyunWanxiangProfileImageProvider(com.kaipai.service.ai.AiImageProviderConfigService aiImageProviderConfigService,
                                              com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        super(aiImageProviderConfigService, objectMapper);
    }

    @Override
    public String providerCode() {
        return "aliyun-wanxiang";
    }
}
