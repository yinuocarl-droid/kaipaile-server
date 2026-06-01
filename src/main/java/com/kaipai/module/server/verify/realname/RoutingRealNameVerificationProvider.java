package com.kaipai.module.server.verify.realname;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class RoutingRealNameVerificationProvider implements RealNameVerificationProvider {

    private final RealNameVerificationProperties properties;
    private final ManualRealNameVerificationProvider manualProvider;
    private final TencentRealNameVerificationProvider tencentProvider;

    @Override
    public RealNameVerificationResult verify(RealNameVerificationCommand command) {
        String providerCode = StringUtils.hasText(properties.getProviderCode())
                ? properties.getProviderCode().trim().toLowerCase(Locale.ROOT)
                : "tencent";
        if ("manual".equals(providerCode)) {
            return manualProvider.verify(command);
        }
        if ("tencent".equals(providerCode)) {
            return tencentProvider.verify(command);
        }
        return RealNameVerificationResult.manual("未知实名服务商配置");
    }
}
