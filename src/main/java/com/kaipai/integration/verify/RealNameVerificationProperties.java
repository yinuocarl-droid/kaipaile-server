package com.kaipai.integration.verify;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kaipai.realname")
public class RealNameVerificationProperties {

    private String providerCode = "tencent";

    private Tencent tencent = new Tencent();

    @Data
    public static class Tencent {

        private String endpoint = "https://faceid.tencentcloudapi.com";

        private String version = "2018-03-01";

        private String secretId = "";

        private String secretKey = "";

        private int connectTimeoutMs = 5000;

        private int readTimeoutMs = 10000;
    }
}
