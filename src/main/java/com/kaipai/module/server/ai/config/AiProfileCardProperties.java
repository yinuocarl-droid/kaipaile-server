package com.kaipai.module.server.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kaipai.ai.profile-card")
public class AiProfileCardProperties {

    private String providerCode = "kplyyk";

    private String generationMode = "image_to_image";

    private int estimatedReadyMinutes = 10;

    private OpenAiProvider openai = new OpenAiProvider();

    private HttpProvider http = new HttpProvider();

    private KplyykProvider kplyyk = new KplyykProvider();

    private Executor executor = new Executor();

    @Data
    public static class OpenAiProvider {
        private String endpoint = "https://api.openai.com/v1/images/edits";
        private String apiKey;
        private String model = "gpt-image-1.5";
        private String size = "1024x1536";
        private String quality = "high";
        private String outputFormat = "png";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 120000;
    }

    @Data
    public static class HttpProvider {
        private String endpoint;
        private String authHeader = "Authorization";
        private String authToken;
        private String model = "profile-card-image";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 120000;
    }

    @Data
    public static class KplyykProvider {
        private String endpoint = "http://kplyyk.com/v0/management/image-generation/test";
        private String authHeader = "Authorization";
        private String authToken;
        private String model = "gpt-image-2";
        private String size = "2160x3840";
        private String quality = "high";
        private int count = 1;
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 120000;
        private int pollIntervalMs = 1500;
        private int maxPollAttempts = 400;
    }

    @Data
    public static class Executor {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 100;
    }
}
