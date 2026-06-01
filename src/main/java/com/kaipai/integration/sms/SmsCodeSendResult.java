package com.kaipai.integration.sms;

public record SmsCodeSendResult(
        String providerCode,
        String requestId,
        String serialNo,
        String statusCode,
        String message,
        boolean exposeCodeToClient
) {

    public static SmsCodeSendResult dev() {
        return new SmsCodeSendResult("dev", null, null, "OK", "dev sms code generated", true);
    }

    public static SmsCodeSendResult success(String providerCode, String requestId, String serialNo, String statusCode, String message) {
        return new SmsCodeSendResult(providerCode, requestId, serialNo, statusCode, message, false);
    }
}

