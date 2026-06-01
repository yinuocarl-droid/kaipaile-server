package com.kaipai.integration.sms;

import com.kaipai.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class RoutingSmsCodeSender implements SmsCodeSender {

    private final SmsProperties smsProperties;
    private final DevSmsCodeSender devSmsCodeSender;
    private final TencentSmsCodeSender tencentSmsCodeSender;

    @Override
    public SmsCodeSendResult sendCode(SmsCodeSendCommand command) {
        String providerCode = StringUtils.hasText(smsProperties.getProviderCode())
                ? smsProperties.getProviderCode().trim().toLowerCase(Locale.ROOT)
                : "tencent";
        if ("dev".equals(providerCode)) {
            return devSmsCodeSender.sendCode(command);
        }
        if ("tencent".equals(providerCode)) {
            return tencentSmsCodeSender.sendCode(command);
        }
        throw new BizException("未知短信服务商配置：" + providerCode);
    }
}

