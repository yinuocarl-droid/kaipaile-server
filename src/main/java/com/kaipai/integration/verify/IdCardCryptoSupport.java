package com.kaipai.integration.verify;

import com.kaipai.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class IdCardCryptoSupport {

    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${kaipai.identity.id-card-cipher-key:}")
    private String cipherKey;

    public String encrypt(String idCardNo) {
        String normalized = normalize(idCardNo);
        String key = StringUtils.hasText(cipherKey) ? cipherKey : System.getenv("KAIPAI_ID_CARD_CIPHER_KEY");
        if (!StringUtils.hasText(key)) {
            return "sha256:" + sha256(normalized);
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(toAesKey(key), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return "aes-gcm:" + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception error) {
            throw new BizException("身份证号加密失败");
        }
    }

    public String mask(String idCardNo) {
        String normalized = normalize(idCardNo);
        if (normalized.length() < 8) {
            return normalized;
        }
        return normalized.substring(0, 3) + "***********" + normalized.substring(normalized.length() - 4);
    }

    public String hash(String idCardNo) {
        return sha256(normalize(idCardNo));
    }

    private String normalize(String idCardNo) {
        return idCardNo == null ? "" : idCardNo.replace(" ", "").trim().toUpperCase();
    }

    private byte[] toAesKey(String key) throws Exception {
        String trimmed = key.trim();
        try {
            byte[] raw = Base64.getDecoder().decode(trimmed);
            if (raw.length == 16 || raw.length == 24 || raw.length == 32) {
                return raw;
            }
        } catch (IllegalArgumentException ignored) {
            // Plain text deployment secrets are hashed into a valid AES-256 key.
        }
        return MessageDigest.getInstance("SHA-256").digest(trimmed.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 not supported", error);
        }
    }
}
