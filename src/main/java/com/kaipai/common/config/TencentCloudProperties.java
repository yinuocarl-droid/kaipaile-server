package com.kaipai.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "tencent")
public class TencentCloudProperties {

    private String secretId;

    private String secretKey;

    private Cos cos = new Cos();

    @Data
    public static class Cos {

        private String region;

        private String bucketName;

        /** Dedicated private bucket for 00-199 actor assets. No public-bucket fallback. */
        private String privateBucketName;
    }
}
