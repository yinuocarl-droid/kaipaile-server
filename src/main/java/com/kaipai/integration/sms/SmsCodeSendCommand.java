package com.kaipai.integration.sms;

public record SmsCodeSendCommand(
        String phone,
        String code,
        long expireMinutes,
        String scene
) {
}

