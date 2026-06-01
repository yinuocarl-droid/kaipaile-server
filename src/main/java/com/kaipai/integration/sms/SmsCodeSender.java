package com.kaipai.integration.sms;

public interface SmsCodeSender {

    SmsCodeSendResult sendCode(SmsCodeSendCommand command);
}

