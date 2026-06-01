package com.kaipai.service.ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

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
    void encryptShouldRoundTripWithPassphraseMasterKey() {
        AiProviderSecretCryptoServiceImpl service = new AiProviderSecretCryptoServiceImpl(new ObjectMapper());
        ReflectionTestUtils.setField(service, "masterKey", "local-dev-master-key");

        String plaintext = "{\"apiKey\":\"sk-passphrase-123456\"}";
        String encrypted = service.encrypt(plaintext);

        assertFalse(encrypted.contains("sk-passphrase-123456"));
        assertEquals(plaintext, service.decrypt(encrypted));
    }

    @Test
    void decryptShouldSupportLegacyPassphraseDerivedCiphertext() throws Exception {
        AiProviderSecretCryptoServiceImpl service = new AiProviderSecretCryptoServiceImpl(new ObjectMapper());
        ReflectionTestUtils.setField(service, "masterKey", "legacy-master-key");

        String plaintext = "{\"apiKey\":\"sk-legacy-123456\"}";
        String encrypted = legacyEncrypt(plaintext, "legacy-master-key");

        assertEquals(plaintext, service.decrypt(encrypted));
    }

    @Test
    void encryptShouldRejectMissingMasterKey() {
        AiProviderSecretCryptoServiceImpl service = new AiProviderSecretCryptoServiceImpl(new ObjectMapper());
        ReflectionTestUtils.setField(service, "masterKey", "");

        BizException error = assertThrows(BizException.class, () -> service.encrypt("{\"apiKey\":\"sk-test\"}"));

        assertEquals("AI_PROVIDER_CONFIG_MASTER_KEY 未配置，禁止保存、查看或调用 AI provider 密钥", error.getMessage());
    }

    private String legacyEncrypt(String plaintext, String masterKey) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        String legacyKey = md5Hex(masterKey).concat(md5Hex("kaipai:" + masterKey)).substring(0, 32);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(legacyKey.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        Map<String, String> envelope = new LinkedHashMap<>();
        envelope.put("version", "1");
        envelope.put("alg", "AES-256-GCM");
        envelope.put("iv", Base64.getEncoder().encodeToString(iv));
        envelope.put("ciphertext", Base64.getEncoder().encodeToString(encrypted));
        return new ObjectMapper().writeValueAsString(envelope);
    }

    private String md5Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            builder.append(String.format("%02x", item & 0xff));
        }
        return builder.toString();
    }
}
