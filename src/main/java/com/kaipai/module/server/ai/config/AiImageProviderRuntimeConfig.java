package com.kaipai.module.server.ai.config;

import com.kaipai.module.model.ai.dto.AiImageProviderPublicConfigDTO;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

public record AiImageProviderRuntimeConfig(
        String providerCode,
        String displayName,
        boolean enabled,
        boolean active,
        AiImageProviderPublicConfigDTO publicConfig,
        Map<String, String> secrets
) {

    public String model(String fallback) {
        return text(publicConfig == null ? null : publicConfig.getModel(), fallback);
    }

    public String endpoint(String fallback) {
        return text(publicConfig == null ? null : publicConfig.getEndpoint(), fallback);
    }

    public String region(String fallback) {
        return text(publicConfig == null ? null : publicConfig.getRegion(), fallback);
    }

    public String authHeader(String fallback) {
        return text(publicConfig == null ? null : publicConfig.getAuthHeader(), fallback);
    }

    public String size(String fallback) {
        return text(publicConfig == null ? null : publicConfig.getSize(), fallback);
    }

    public String quality(String fallback) {
        return text(publicConfig == null ? null : publicConfig.getQuality(), fallback);
    }

    public String responseFormat(String fallback) {
        return text(publicConfig == null ? null : publicConfig.getResponseFormat(), fallback);
    }

    public int count(int fallback) {
        Integer value = publicConfig == null ? null : publicConfig.getCount();
        return value == null || value <= 0 ? fallback : value;
    }

    public int connectTimeoutMs(int fallback) {
        Integer value = publicConfig == null ? null : publicConfig.getConnectTimeoutMs();
        return value == null || value <= 0 ? fallback : value;
    }

    public int readTimeoutMs(int fallback) {
        Integer value = publicConfig == null ? null : publicConfig.getReadTimeoutMs();
        return value == null || value <= 0 ? fallback : value;
    }

    public int pollIntervalMs(int fallback) {
        Integer value = publicConfig == null ? null : publicConfig.getPollIntervalMs();
        return value == null || value <= 0 ? fallback : value;
    }

    public int maxPollAttempts(int fallback) {
        Integer value = publicConfig == null ? null : publicConfig.getMaxPollAttempts();
        return value == null || value <= 0 ? fallback : value;
    }

    public Boolean watermark(Boolean fallback) {
        Boolean value = publicConfig == null ? null : publicConfig.getWatermark();
        return value == null ? fallback : value;
    }

    public String secret(String key) {
        Map<String, String> safeSecrets = secrets == null ? Collections.emptyMap() : secrets;
        String value = safeSecrets.get(key);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public String firstSecret(String... keys) {
        for (String key : keys) {
            String value = secret(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
