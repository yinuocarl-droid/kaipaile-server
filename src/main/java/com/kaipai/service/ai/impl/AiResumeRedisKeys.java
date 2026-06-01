package com.kaipai.service.ai.impl;

final class AiResumeRedisKeys {

    private static final String DRAFT_PREFIX = "ai:resume_polish:draft:";
    private static final String HISTORY_PREFIX = "ai:resume_polish:history:";

    private AiResumeRedisKeys() {
    }

    static String draftKey(Long userId, String draftId) {
        return DRAFT_PREFIX + userId + ":" + draftId;
    }

    static String historyKey(Long userId) {
        return HISTORY_PREFIX + userId;
    }

    static String historyPattern() {
        return HISTORY_PREFIX + "*";
    }

    static Long extractUserIdFromHistoryKey(String key) {
        if (key == null || !key.startsWith(HISTORY_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(key.substring(HISTORY_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
