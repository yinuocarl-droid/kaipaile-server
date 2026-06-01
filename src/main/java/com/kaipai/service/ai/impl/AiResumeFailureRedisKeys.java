package com.kaipai.service.ai.impl;

final class AiResumeFailureRedisKeys {

    private static final String FAILURE_RECORDS_KEY = "ai:resume_polish:failures";

    private AiResumeFailureRedisKeys() {
    }

    static String recordsKey() {
        return FAILURE_RECORDS_KEY;
    }
}
