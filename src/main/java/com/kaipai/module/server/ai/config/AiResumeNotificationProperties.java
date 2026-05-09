package com.kaipai.module.server.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kaipai.ai.resume.notification")
public class AiResumeNotificationProperties {

    private boolean enabled = false;

    private String providerCode = "manual";

    private String callbackHeader = "X-Kaipai-Ai-Notification-Token";

    private String callbackToken;

    private String callbackUrl;

    private HttpProvider http = new HttpProvider();

    @Data
    public static class HttpProvider {

        private String endpoint;

        private String authHeader = "Authorization";

        private String authToken;

        private int connectTimeoutMs = 5000;

        private int readTimeoutMs = 10000;
    }
}
