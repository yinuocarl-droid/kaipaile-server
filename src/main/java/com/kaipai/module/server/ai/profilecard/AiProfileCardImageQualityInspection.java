package com.kaipai.module.server.ai.profilecard;

import org.springframework.util.StringUtils;

public record AiProfileCardImageQualityInspection(
        boolean accepted,
        String reason
) {

    public static AiProfileCardImageQualityInspection accept() {
        return new AiProfileCardImageQualityInspection(true, "");
    }

    public static AiProfileCardImageQualityInspection skipped(String reason) {
        return new AiProfileCardImageQualityInspection(true, normalizeReason(reason, "AI 分享图成图质检已跳过"));
    }

    public static AiProfileCardImageQualityInspection rejected(String reason) {
        return new AiProfileCardImageQualityInspection(false, normalizeReason(reason, "AI 分享图成图质检未通过"));
    }

    private static String normalizeReason(String reason, String fallback) {
        return StringUtils.hasText(reason) ? reason.trim() : fallback;
    }
}
