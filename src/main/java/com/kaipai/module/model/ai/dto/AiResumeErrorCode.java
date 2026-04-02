package com.kaipai.module.model.ai.dto;

public final class AiResumeErrorCode {

    public static final int AUTH_REQUIRED = 7101;
    public static final int NOT_CERTIFIED = 7102;
    public static final int QUOTA_EXHAUSTED = 7103;
    public static final int CONTEXT_INVALID = 7104;
    public static final int CONTENT_BLOCKED = 7105;
    public static final int MODEL_TIMEOUT = 7106;
    public static final int RESPONSE_UNPARSABLE = 7107;
    public static final int PROFILE_STALE = 7108;
    public static final int HISTORY_NOT_FOUND = 7109;
    public static final int ROLLBACK_CONFLICT = 7110;

    private AiResumeErrorCode() {
    }
}
