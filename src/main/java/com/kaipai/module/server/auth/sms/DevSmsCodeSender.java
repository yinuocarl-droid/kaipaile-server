package com.kaipai.module.server.auth.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DevSmsCodeSender {

    public SmsCodeSendResult sendCode(SmsCodeSendCommand command) {
        log.info("【开发模式】手机号 {} 验证码已生成", maskPhone(command.phone()));
        return SmsCodeSendResult.dev();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}

