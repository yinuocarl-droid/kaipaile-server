package com.kaipai.module.server.ai.provider;

import com.kaipai.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiProfileImageProviderRegistry {

    private final List<AiProfileImageProvider> providers;

    public AiProfileImageProvider resolve(String providerCode) {
        String normalized = StringUtils.hasText(providerCode) ? providerCode.trim() : "kplyyk";
        if ("mock".equalsIgnoreCase(normalized)) {
            throw new BizException("AI 分享图禁止使用 mock provider，请配置真实生图 provider");
        }
        return providers.stream()
                .filter(provider -> normalized.equalsIgnoreCase(provider.providerCode()))
                .findFirst()
                .orElseThrow(() -> new BizException("AI 分享图模型 provider 未配置：" + normalized));
    }
}
