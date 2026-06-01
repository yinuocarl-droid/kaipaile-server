package com.kaipai.service.ai.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.service.ai.AiProviderSecretCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiProviderSecretCryptoServiceImpl implements AiProviderSecretCryptoService {

    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {
    };
    private static final String ALG = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${AI_PROVIDER_CONFIG_MASTER_KEY:}")
    private String masterKey;

    @Override
    public String encrypt(String plaintextJson) {
        if (!StringUtils.hasText(plaintextJson)) {
            throw new BizException("密钥明文不能为空");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintextJson.getBytes(StandardCharsets.UTF_8));

            Map<String, String> envelope = new LinkedHashMap<>();
            envelope.put("version", "1");
            envelope.put("alg", "AES-256-GCM");
            envelope.put("iv", Base64.getEncoder().encodeToString(iv));
            envelope.put("ciphertext", Base64.getEncoder().encodeToString(encrypted));
            return objectMapper.writeValueAsString(envelope);
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("AI provider 密钥加密失败");
        }
    }

    @Override
    public String decrypt(String envelopeJson) {
        if (!StringUtils.hasText(envelopeJson)) {
            return "{}";
        }
        try {
            Map<String, String> envelope = objectMapper.readValue(envelopeJson, STRING_MAP_TYPE);
            try {
                return decryptEnvelope(envelope, keySpec());
            } catch (Exception currentKeyError) {
                return decryptEnvelope(envelope, legacyPassphraseKeySpec());
            }
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("AI provider 密钥解密失败，请确认 AI_PROVIDER_CONFIG_MASTER_KEY 是否正确");
        }
    }

    private String decryptEnvelope(Map<String, String> envelope, SecretKeySpec key) throws Exception {
        byte[] iv = Base64.getDecoder().decode(envelope.get("iv"));
        byte[] ciphertext = Base64.getDecoder().decode(envelope.get("ciphertext"));
        Cipher cipher = Cipher.getInstance(ALG);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private SecretKeySpec keySpec() {
        if (!StringUtils.hasText(masterKey)) {
            throw new BizException("AI_PROVIDER_CONFIG_MASTER_KEY 未配置，禁止保存、查看或调用 AI provider 密钥");
        }
        String normalized = masterKey.trim();
        byte[] keyBytes = parseKeyBytes(normalized);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] parseKeyBytes(String value) {
        if (value.matches("^[0-9a-fA-F]{64}$")) {
            byte[] bytes = new byte[32];
            for (int index = 0; index < 32; index++) {
                bytes[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
            }
            return bytes;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to SHA-256 derivation below.
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new BizException("AI provider 密钥主密钥派生失败");
        }
    }

    private SecretKeySpec legacyPassphraseKeySpec() {
        if (!StringUtils.hasText(masterKey)) {
            throw new BizException("AI_PROVIDER_CONFIG_MASTER_KEY 未配置，禁止保存、查看或调用 AI provider 密钥");
        }
        String normalized = masterKey.trim();
        if (normalized.matches("^[0-9a-fA-F]{64}$")) {
            return keySpec();
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(normalized);
            if (decoded.length == 32) {
                return keySpec();
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to legacy passphrase derivation below.
        }
        String legacyKey = md5Hex(normalized).concat(md5Hex("kaipai:" + normalized)).substring(0, 32);
        return new SecretKeySpec(legacyKey.getBytes(StandardCharsets.UTF_8), "AES");
    }

    private String md5Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (Exception error) {
            throw new BizException("AI provider 密钥旧版主密钥派生失败");
        }
    }
}
