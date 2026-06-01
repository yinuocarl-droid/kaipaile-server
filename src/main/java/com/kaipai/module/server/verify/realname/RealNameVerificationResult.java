package com.kaipai.module.server.verify.realname;

public record RealNameVerificationResult(
        boolean matched,
        boolean definitive,
        String providerCode,
        String requestId,
        String resultCode,
        String resultMessage
) {

    public static RealNameVerificationResult manual(String reason) {
        return new RealNameVerificationResult(false, false, "manual", null, "MANUAL_REVIEW", reason);
    }

    public static RealNameVerificationResult matched(String providerCode, String requestId, String resultCode, String resultMessage) {
        return new RealNameVerificationResult(true, true, providerCode, requestId, resultCode, resultMessage);
    }

    public static RealNameVerificationResult mismatch(String providerCode, String requestId, String resultCode, String resultMessage) {
        return new RealNameVerificationResult(false, true, providerCode, requestId, resultCode, resultMessage);
    }

    public static RealNameVerificationResult manualReview(String providerCode, String requestId, String resultCode, String resultMessage) {
        return new RealNameVerificationResult(false, false, providerCode, requestId, resultCode, resultMessage);
    }
}
