package com.kaipai.module.server.auth.sms;

public record SmsCodeSendCommand(
        String phone,
        String code,
        long expireMinutes,
        String scene
) {
}

