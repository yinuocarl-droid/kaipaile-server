package com.kaipai.service.ai.profileimport;

import com.kaipai.model.ai.dto.ProfileImportWorkProofValue;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProfileImportCandidateProofService {
    private static final String PROOF_PREFIX = "v2.";
    private final String secret;

    public ProfileImportCandidateProofService(
            @Value("${PROFILE_IMPORT_PROOF_SECRET:${AI_PROVIDER_CONFIG_MASTER_KEY:}}") String secret) {
        this.secret = secret;
    }

    public String issueProfile(Long userId, String requestId, String candidateId, String fieldKey, String candidateValue,
            String sourceType, boolean requiresExplicitConfirmation) {
        return issue(profileCanonical(userId, requestId, candidateId, fieldKey, candidateValue, sourceType,
                requiresExplicitConfirmation));
    }

    public boolean verifyProfile(String proof, Long userId, String requestId, String candidateId, String fieldKey,
            String candidateValue, String sourceType, boolean requiresExplicitConfirmation) {
        return verify(proof, profileCanonical(userId, requestId, candidateId, fieldKey, candidateValue, sourceType,
                requiresExplicitConfirmation));
    }

    public String issueWork(Long userId, String requestId, String candidateId, ProfileImportWorkProofValue value,
            String sourceType, String matchStatus, Long matchedExperienceId, List<String> allowedActions,
            List<String> conflictFields) {
        return issue(workCanonical(userId, requestId, candidateId, value, sourceType, matchStatus,
                matchedExperienceId, allowedActions, conflictFields));
    }

    public boolean verifyWork(String proof, Long userId, String requestId, String candidateId,
            ProfileImportWorkProofValue value, String sourceType, String matchStatus,
            Long matchedExperienceId, List<String> allowedActions, List<String> conflictFields) {
        return verify(proof, workCanonical(userId, requestId, candidateId, value, sourceType, matchStatus,
                matchedExperienceId, allowedActions, conflictFields));
    }

    private byte[] profileCanonical(Long userId, String requestId, String candidateId, String fieldKey,
            String candidateValue, String sourceType, boolean requiresExplicitConfirmation) {
        return canonical(output -> {
            writeString(output, "profile-import-profile-proof-v2");
            writeLong(output, userId);
            writeString(output, requestId);
            writeString(output, candidateId);
            writeString(output, fieldKey);
            writeString(output, candidateValue);
            writeString(output, sourceType);
            output.writeBoolean(requiresExplicitConfirmation);
        });
    }

    private byte[] workCanonical(Long userId, String requestId, String candidateId, ProfileImportWorkProofValue value,
            String sourceType, String matchStatus, Long matchedExperienceId, List<String> allowedActions,
            List<String> conflictFields) {
        return canonical(output -> {
            writeString(output, "profile-import-work-proof-v2");
            writeLong(output, userId);
            writeString(output, requestId);
            writeString(output, candidateId);
            writeBytes(output, value == null ? null : value.canonicalBytes());
            writeString(output, sourceType);
            writeString(output, matchStatus);
            writeLong(output, matchedExperienceId);
            writeStrings(output, allowedActions);
            writeStrings(output, conflictFields);
        });
    }

    private String issue(byte[] canonical) {
        return PROOF_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(canonical));
    }

    private boolean verify(String proof, byte[] canonical) {
        if (proof == null || !proof.startsWith(PROOF_PREFIX)) {
            return false;
        }
        try {
            byte[] supplied = Base64.getUrlDecoder().decode(proof.substring(PROOF_PREFIX.length()));
            return MessageDigest.isEqual(sign(canonical), supplied);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private byte[] sign(byte[] value) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("PROFILE_IMPORT_PROOF_SECRET is not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private byte[] canonical(CanonicalWriter writer) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(buffer);
            writer.write(output);
            output.flush();
            return buffer.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to canonicalize profile import proof", error);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeByte(0);
            return;
        }
        output.writeByte(1);
        output.writeInt(value.length());
        for (int index = 0; index < value.length(); index++) {
            output.writeChar(value.charAt(index));
        }
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        output.writeInt(value.length);
        output.write(value);
    }

    private static void writeLong(DataOutputStream output, Long value) throws IOException {
        if (value == null) {
            output.writeByte(0);
            return;
        }
        output.writeByte(1);
        output.writeLong(value);
    }

    private static void writeStrings(DataOutputStream output, List<String> values) throws IOException {
        if (values == null) {
            output.writeInt(-1);
            return;
        }
        output.writeInt(values.size());
        for (String value : values) {
            writeString(output, value);
        }
    }

    @FunctionalInterface
    private interface CanonicalWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
