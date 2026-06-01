package com.kaipai.model.ai.dto;

public final class AiResumeErrorCode {

    public static final int AUTH_REQUIRED = 401;
    public static final int NOT_CERTIFIED = 403;
    public static final int QUOTA_EXHAUSTED = 429;
    public static final int CONTEXT_INVALID = 400;
    public static final int CONTENT_BLOCKED = 451;
    public static final int MODEL_TIMEOUT = 408;
    public static final int RESPONSE_UNPARSABLE = 422;
    public static final int PROFILE_STALE = 409;
    public static final int HISTORY_NOT_FOUND = 404;
    public static final int ROLLBACK_CONFLICT = 409;

    private AiResumeErrorCode() {
    }
}
