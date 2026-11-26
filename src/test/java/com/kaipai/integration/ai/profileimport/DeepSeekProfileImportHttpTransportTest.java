package com.kaipai.integration.ai.profileimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kaipai.service.ai.profileimport.ProfileImportEndpointPolicy;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class DeepSeekProfileImportHttpTransportTest {
    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";

    @Test
    void everyPostResolvesAgainAndRejectsPrivateDnsRebindingBeforeSend() throws Exception {
        HttpClient client = successfulClient("{\"ok\":true}");
        AtomicInteger resolutions = new AtomicInteger();
        Function<String, InetAddress[]> rebindingResolver = ignored ->
                resolutions.getAndIncrement() == 0
                        ? new InetAddress[] {address(8, 8, 8, 8)}
                        : new InetAddress[] {address(10, 0, 0, 7)};
        DeepSeekProfileImportHttpTransport transport = transport(rebindingResolver, ignored -> client);

        assertEquals("{\"ok\":true}",
                transport.post(ENDPOINT, "sk-memory", "{\"first\":true}", 1200, 3400));
        assertThrows(IllegalArgumentException.class,
                () -> transport.post(ENDPOINT, "sk-memory", "{\"second\":true}", 1200, 3400));

        assertEquals(2, resolutions.get());
        verify(client, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    void privateResolutionIsRejectedBeforeCreatingAnHttpClient() {
        @SuppressWarnings("unchecked")
        IntFunction<HttpClient> clientFactory = mock(IntFunction.class);
        DeepSeekProfileImportHttpTransport transport = transport(
                ignored -> new InetAddress[] {address(192, 168, 1, 9)}, clientFactory);

        assertThrows(IllegalArgumentException.class,
                () -> transport.post(ENDPOINT, "sk-memory", "{}", 1000, 1000));

        verifyNoInteractions(clientFactory);
    }

    @Test
    void publicResolutionPassesAuthorizationAndRequestBodyWithoutLoggingThem() throws Exception {
        HttpClient client = successfulClient("{\"accepted\":true}");
        DeepSeekProfileImportHttpTransport transport = transport(
                ignored -> new InetAddress[] {address(8, 8, 8, 8), address(1, 1, 1, 1)},
                ignored -> client);
        Logger logger = (Logger) LoggerFactory.getLogger(DeepSeekProfileImportHttpTransport.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String secret = "sk-never-log-this";
        String body = "{\"privateText\":\"never-log-this-body\"}";

        try {
            assertEquals("{\"accepted\":true}",
                    transport.post(ENDPOINT, secret, body, 1500, 2500));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any());
        assertEquals("Bearer " + secret,
                request.getValue().headers().firstValue("Authorization").orElseThrow());
        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertFalse(logs.contains(secret));
        assertFalse(logs.contains(body));
        assertFalse(logs.contains("never-log-this-body"));
    }

    @Test
    void productionHttpClientNeverFollowsRedirects() {
        assertEquals(HttpClient.Redirect.NEVER,
                DeepSeekProfileImportHttpTransport.newHttpClient(1234).followRedirects());
    }

    private DeepSeekProfileImportHttpTransport transport(
            Function<String, InetAddress[]> resolver,
            IntFunction<HttpClient> clientFactory) {
        return new DeepSeekProfileImportHttpTransport(
                new ProfileImportEndpointPolicy(), resolver, clientFactory);
    }

    private HttpClient successfulClient(String body) throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        return client;
    }

    private InetAddress address(int first, int second, int third, int fourth) {
        try {
            return InetAddress.getByAddress("api.deepseek.com", new byte[] {
                    (byte) first, (byte) second, (byte) third, (byte) fourth});
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
