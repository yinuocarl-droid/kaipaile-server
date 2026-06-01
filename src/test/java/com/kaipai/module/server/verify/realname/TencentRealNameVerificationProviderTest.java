package com.kaipai.integration.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TencentRealNameVerificationProviderTest {

    @Test
    void shouldRejectMissingTencentConfig() {
        RealNameVerificationProperties properties = new RealNameVerificationProperties();
        TencentRealNameVerificationProvider provider = new TencentRealNameVerificationProvider(properties, new ObjectMapper());

        BizException error = assertThrows(BizException.class,
                () -> provider.verify(new RealNameVerificationCommand(10000L, "林夏", "11010519491231002X")));

        assertTrue(error.getMessage().contains("腾讯云实名核验配置未完成"));
    }

    @Test
    void shouldParseMatchedResult() throws IOException {
        HttpServer server = startTencentServer("""
                {"Response":{"Result":"0","Description":"姓名和身份证号一致","RequestId":"req-ok"}}
                """);
        try {
            TencentRealNameVerificationProvider provider = newProvider(server);

            RealNameVerificationResult result = provider.verify(new RealNameVerificationCommand(10000L, "林夏", "11010519491231002X"));

            assertTrue(result.matched());
            assertTrue(result.definitive());
            assertEquals("tencent", result.providerCode());
            assertEquals("req-ok", result.requestId());
            assertEquals("0", result.resultCode());
            assertEquals("姓名和身份证号一致", result.resultMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldCallTencentIdCardOcrVerificationAction() throws IOException {
        CapturingTencentServer server = startCapturingTencentServer("""
                {"Response":{"Result":"0","Description":"姓名和身份证号一致","RequestId":"req-action"}}
                """);
        try {
            TencentRealNameVerificationProvider provider = newProvider(server.server());

            provider.verify(new RealNameVerificationCommand(10000L, "林夏", "11010519491231002X"));

            assertEquals("IdCardOCRVerification", server.action());
        } finally {
            server.server().stop(0);
        }
    }

    @Test
    void shouldParseMismatchResultAsDefinitiveFailure() throws IOException {
        HttpServer server = startTencentServer("""
                {"Response":{"Result":"-1","Description":"姓名和身份证号不一致","RequestId":"req-mismatch"}}
                """);
        try {
            TencentRealNameVerificationProvider provider = newProvider(server);

            RealNameVerificationResult result = provider.verify(new RealNameVerificationCommand(10000L, "林夏", "11010519491231002X"));

            assertFalse(result.matched());
            assertTrue(result.definitive());
            assertEquals("tencent", result.providerCode());
            assertEquals("req-mismatch", result.requestId());
            assertEquals("-1", result.resultCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldTreatTencentErrorAsProviderFailure() throws IOException {
        HttpServer server = startTencentServer("""
                {"Response":{"Error":{"Code":"UnauthorizedOperation.Nonactivated","Message":"service not activated"},"RequestId":"req-error"}}
                """);
        try {
            TencentRealNameVerificationProvider provider = newProvider(server);

            BizException error = assertThrows(BizException.class,
                    () -> provider.verify(new RealNameVerificationCommand(10000L, "林夏", "11010519491231002X")));

            assertTrue(error.getMessage().contains("腾讯云实名核验 API 错误"));
        } finally {
            server.stop(0);
        }
    }

    private TencentRealNameVerificationProvider newProvider(HttpServer server) {
        RealNameVerificationProperties properties = new RealNameVerificationProperties();
        RealNameVerificationProperties.Tencent tencent = properties.getTencent();
        tencent.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        tencent.setSecretId("secret-id");
        tencent.setSecretKey("secret-key");
        return new TencentRealNameVerificationProvider(properties, new ObjectMapper());
    }

    private HttpServer startTencentServer(String responseJson) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private CapturingTencentServer startCapturingTencentServer(String responseJson) throws IOException {
        CapturingTencentServer capturing = new CapturingTencentServer(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
        capturing.server().createContext("/", exchange -> {
            capturing.action = exchange.getRequestHeaders().getFirst("X-TC-Action");
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        capturing.server().start();
        return capturing;
    }

    private static class CapturingTencentServer {
        private final HttpServer server;
        private String action;

        private CapturingTencentServer(HttpServer server) {
            this.server = server;
        }

        private HttpServer server() {
            return server;
        }

        private String action() {
            return action;
        }
    }
}
