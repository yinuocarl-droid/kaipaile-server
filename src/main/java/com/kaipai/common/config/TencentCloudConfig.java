package com.kaipai.common.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class TencentCloudConfig {

    private final TencentCloudProperties tencentCloudProperties;

    @Bean
    public COSClient cosClient() {
        COSCredentials credentials = new BasicCOSCredentials(
                tencentCloudProperties.getSecretId(),
                tencentCloudProperties.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(tencentCloudProperties.getCos().getRegion()));
        return new COSClient(credentials, clientConfig);
    }
}
