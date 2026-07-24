package com.kaipai.integration.ai.profileimport;

import com.kaipai.service.ai.profileimport.ProfileImportHttpTransport;
import com.kaipai.service.ai.profileimport.ProfileImportEndpointPolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekProfileImportHttpTransport implements ProfileImportHttpTransport {
    private final ProfileImportEndpointPolicy endpointPolicy;
    private final Function<String, InetAddress[]> resolver;
    private final IntFunction<HttpClient> clientFactory;

    @Autowired
    public DeepSeekProfileImportHttpTransport(ProfileImportEndpointPolicy endpointPolicy) {
        this(endpointPolicy, DeepSeekProfileImportHttpTransport::resolve,
                DeepSeekProfileImportHttpTransport::newHttpClient);
    }

    DeepSeekProfileImportHttpTransport(
            ProfileImportEndpointPolicy endpointPolicy,
            Function<String, InetAddress[]> resolver,
            IntFunction<HttpClient> clientFactory) {
        this.endpointPolicy = endpointPolicy;
        this.resolver = resolver;
        this.clientFactory = clientFactory;
    }

    @Override
    public String post(String endpoint, String apiKey, String body,
            int connectTimeoutMs, int readTimeoutMs) {
        URI uri = URI.create(endpoint);
        endpointPolicy.validateConfigured(uri);
        endpointPolicy.validateResolved(uri, resolver);
        try {
            HttpClient client = clientFactory.apply(Math.max(1000, connectTimeoutMs));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(Math.max(1000, readTimeoutMs)))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("provider status");
            }
            return response.body();
        } catch (java.net.http.HttpTimeoutException error) {
            throw new ProfileImportHttpTransport.Timeout();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("provider request interrupted", error);
        } catch (Exception error) {
            throw new IllegalStateException("provider request failed", error);
        }
    }

    static HttpClient newHttpClient(int connectTimeoutMs) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMs)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private static InetAddress[] resolve(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (Exception error) {
            throw new IllegalArgumentException("DeepSeek host resolution failed", error);
        }
    }
}
