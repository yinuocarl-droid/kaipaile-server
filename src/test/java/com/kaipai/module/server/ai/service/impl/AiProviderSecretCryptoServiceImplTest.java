package com.kaipai.module.server.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiProviderSecretCryptoServiceImplTest {

    @Test
    void encryptShouldRoundTripWithoutExposingPlaintext() {
        AiProviderSecretCryptoServiceImpl service = new AiProviderSecretCryptoServiceImpl(new ObjectMapper());
        ReflectionTestUtils.setField(service, "masterKey",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        String plaintext = "{\"apiKey\":\"sk-test-123456\",\"secretKey\":\"secret-value\"}";
        String encrypted = service.encrypt(plaintext);

        assertFalse(encrypted.contains("sk-test-123456"));
        assertFalse(encrypted.contains("secret-value"));
        assertEquals(plaintext, service.decrypt(encrypted));
    }

    @Test
    void encryptShouldRejectMissingMasterKey() {
        AiProviderSecretCryptoServiceImpl service = new AiProviderSecretCryptoServiceImpl(new ObjectMapper());
        ReflectionTestUtils.setField(service, "masterKey", "");

        BizException error = assertThrows(BizException.class, () -> service.encrypt("{\"apiKey\":\"sk-test\"}"));

        assertEquals("AI_PROVIDER_CONFIG_MASTER_KEY 未配置，禁止保存、查看或调用 AI provider 密钥", error.getMessage());
    }
}
