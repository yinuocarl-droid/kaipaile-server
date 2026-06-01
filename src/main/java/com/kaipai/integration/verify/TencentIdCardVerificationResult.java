package com.kaipai.integration.verify;

public record TencentIdCardVerificationResult(
        String resultCode,
        String description,
        String requestId
) {

    public boolean matched() {
        return "0".equals(resultCode);
    }
}
