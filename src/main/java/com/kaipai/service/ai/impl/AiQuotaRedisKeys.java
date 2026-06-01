package com.kaipai.service.ai.impl;

import java.time.LocalDate;

final class AiQuotaRedisKeys {

    private static final String QUOTA_PREFIX = "ai:quota:resume_polish:";

    private AiQuotaRedisKeys() {
    }

    static String quotaKey(LocalDate periodStart, Long userId) {
        return QUOTA_PREFIX + periodStart + ":" + userId;
    }

    static String quotaPattern(LocalDate periodStart) {
        return QUOTA_PREFIX + periodStart + ":*";
    }
}
