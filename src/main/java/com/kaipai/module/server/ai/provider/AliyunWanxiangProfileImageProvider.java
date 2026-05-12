package com.kaipai.module.server.ai.provider;

import org.springframework.stereotype.Component;

@Component
public class AliyunWanxiangProfileImageProvider extends AliyunQwenImageProfileImageProvider {

    public AliyunWanxiangProfileImageProvider(com.kaipai.module.server.ai.service.AiImageProviderConfigService aiImageProviderConfigService,
                                              com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        super(aiImageProviderConfigService, objectMapper);
    }

    @Override
    public String providerCode() {
        return "aliyun-wanxiang";
    }
}
