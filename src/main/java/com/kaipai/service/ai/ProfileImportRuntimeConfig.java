package com.kaipai.service.ai;

public record ProfileImportRuntimeConfig(
        Long configId,
        String endpoint,
        String modelName,
        String apiKey,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxInputChars,
        int maxOutputTokens,
        int dailyLimit) {
    @Override
    public String toString() {
        return "ProfileImportRuntimeConfig[configId=" + configId
                + ", endpoint=" + endpoint
                + ", modelName=" + modelName
                + ", apiKey=REDACTED"
                + ", connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs
                + ", maxInputChars=" + maxInputChars
                + ", maxOutputTokens=" + maxOutputTokens
                + ", dailyLimit=" + dailyLimit + "]";
    }
}
