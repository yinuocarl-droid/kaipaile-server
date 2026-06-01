package com.kaipai.integration.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kaipai.sms")
public class SmsProperties {

    private String providerCode = "tencent";

    private long codeExpireMinutes = 10;

    private Tencent tencent = new Tencent();

    @Data
    public static class Tencent {

        private String endpoint = "https://sms.tencentcloudapi.com";

        private String region = "ap-guangzhou";

        private String version = "2021-01-11";

        private String secretId = "";

        private String secretKey = "";

        private String smsSdkAppId = "";

        private String signName = "";

        private String templateId = "";

        /**
         * code: only verification code.
         * code_ttl: verification code + expire minutes.
         */
        private String templateParamMode = "code";

        private int connectTimeoutMs = 5000;

        private int readTimeoutMs = 10000;
    }
}
