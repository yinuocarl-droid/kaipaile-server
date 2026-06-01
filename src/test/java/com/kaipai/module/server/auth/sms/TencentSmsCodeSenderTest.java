package com.kaipai.integration.sms;

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

class TencentSmsCodeSenderTest {

    @Test
    void shouldRejectMissingTencentConfig() {
        SmsProperties properties = new SmsProperties();
        TencentSmsCodeSender sender = new TencentSmsCodeSender(properties, new ObjectMapper());

        BizException error = assertThrows(BizException.class,
                () -> sender.sendCode(new SmsCodeSendCommand("13800138000", "123456", 5, "login")));

        assertTrue(error.getMessage().contains("腾讯云短信配置未完成"));
    }

    @Test
    void shouldParseTencentSuccessResponse() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = """
                    {"Response":{"SendStatusSet":[{"SerialNo":"sms-001","PhoneNumber":"+8613800138000","Fee":1,"SessionContext":"login","Code":"Ok","Message":"send success"}],"RequestId":"req-001"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            SmsProperties properties = new SmsProperties();
            SmsProperties.Tencent tencent = properties.getTencent();
            tencent.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
            tencent.setSecretId("secret-id");
            tencent.setSecretKey("secret-key");
            tencent.setSmsSdkAppId("1400000000");
            tencent.setSignName("开拍了");
            tencent.setTemplateId("123456");
            TencentSmsCodeSender sender = new TencentSmsCodeSender(properties, new ObjectMapper());

            SmsCodeSendResult result = sender.sendCode(new SmsCodeSendCommand("13800138000", "123456", 5, "login"));

            assertEquals("tencent", result.providerCode());
            assertEquals("req-001", result.requestId());
            assertEquals("sms-001", result.serialNo());
            assertFalse(result.exposeCodeToClient());
        } finally {
            server.stop(0);
        }
    }
}
