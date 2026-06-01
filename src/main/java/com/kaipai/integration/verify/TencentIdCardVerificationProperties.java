package com.kaipai.integration.verify;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "tencent.faceid")
public class TencentIdCardVerificationProperties {

    private boolean enabled = false;

    private String secretId;

    private String secretKey;

    private String endpoint = "https://faceid.tencentcloudapi.com";

    private String version = "2018-03-01";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 10000;
}
