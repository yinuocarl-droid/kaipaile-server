package com.kaipai.service.ai.profilecard;

import org.springframework.util.StringUtils;

public record AiProfileCardImageQualityInspection(
        boolean accepted,
        String reason,
        boolean retryable
) {

    public AiProfileCardImageQualityInspection(boolean accepted, String reason) {
        this(accepted, reason, !accepted);
    }

    public static AiProfileCardImageQualityInspection accept() {
        return new AiProfileCardImageQualityInspection(true, "", false);
    }

    public static AiProfileCardImageQualityInspection skipped(String reason) {
        return unavailable(reason);
    }

    public static AiProfileCardImageQualityInspection unavailable(String reason) {
        return new AiProfileCardImageQualityInspection(false, normalizeReason(reason, "AI 分享图成图质检无法执行"), false);
    }

    public static AiProfileCardImageQualityInspection rejected(String reason) {
        return new AiProfileCardImageQualityInspection(false, normalizeReason(reason, "AI 分享图成图质检未通过"), true);
    }

    private static String normalizeReason(String reason, String fallback) {
        return StringUtils.hasText(reason) ? reason.trim() : fallback;
    }
}
