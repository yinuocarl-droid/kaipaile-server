package com.kaipai.service.ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import com.kaipai.service.ai.ProfileImportConnectionTester;
import com.kaipai.service.ai.profileimport.ProfileImportEndpointPolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileImportConnectionTesterImpl implements ProfileImportConnectionTester {
    private final ObjectMapper mapper;
    private final ProfileImportEndpointPolicy endpointPolicy;

    @Override
    public void test(AiProfileImportConfig config, String apiKey) {
        try {
            URI endpoint = URI.create(config.getEndpoint());
            endpointPolicy.validateResolved(endpoint, host -> {
                try {
                    return InetAddress.getAllByName(host);
                } catch (Exception error) {
                    throw new IllegalArgumentException(error);
                }
            });
            String body = mapper.writeValueAsString(Map.of(
                    "model", config.getModelName(),
                    "messages", List.of(Map.of("role", "user", "content", "health check")),
                    "max_tokens", 1));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("DeepSeek 连接测试失败");
            }
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("DeepSeek 连接测试失败");
        }
    }
}

