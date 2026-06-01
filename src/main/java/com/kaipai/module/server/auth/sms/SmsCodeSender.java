package com.kaipai.module.server.auth.sms;

public interface SmsCodeSender {

    SmsCodeSendResult sendCode(SmsCodeSendCommand command);
}

