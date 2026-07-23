package com.kaipai.service.ai.profileimport;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProfileImportCandidateProofService {
    private final String secret;

    public ProfileImportCandidateProofService(
            @Value("${PROFILE_IMPORT_PROOF_SECRET:${AI_PROVIDER_CONFIG_MASTER_KEY:}}") String secret) {
        this.secret = secret;
    }

    public String issue(String requestId, String candidateId, String value, String sourceType,
            boolean requiresConfirmation) {
        return sign(canonical(requestId, candidateId, value, sourceType, requiresConfirmation));
    }

    public boolean verify(String proof, String requestId, String candidateId, String value, String sourceType,
            boolean requiresConfirmation) {
        return proof != null
                && sign(canonical(requestId, candidateId, value, sourceType, requiresConfirmation)).equals(proof);
    }

    private String canonical(String requestId, String candidateId, String value, String sourceType,
            boolean requiresConfirmation) {
        return requestId + "|" + candidateId + "|" + value + "|" + sourceType + "|" + requiresConfirmation;
    }

    private String sign(String value) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("PROFILE_IMPORT_PROOF_SECRET is not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
