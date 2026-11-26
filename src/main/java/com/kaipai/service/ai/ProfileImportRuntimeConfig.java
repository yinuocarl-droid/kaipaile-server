package com.kaipai.service.ai;

public record ProfileImportRuntimeConfig(
        Long configId,
        Integer configVersion,
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
                + ", configVersion=" + configVersion
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
