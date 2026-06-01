package com.kaipai.module.server.verify.provider;

public record TencentIdCardVerificationResult(
        String resultCode,
        String description,
        String requestId
) {

    public boolean matched() {
        return "0".equals(resultCode);
    }
}
