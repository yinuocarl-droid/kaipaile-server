package com.kaipai.service.ai.profileimport;

import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Renders governed profile-import Prompt bodies and computes unambiguous lineage hashes. */
@Component
public final class ProfileImportPromptRenderer {

    private static final String CONTENT_HASH_DOMAIN = "profile-import-prompt-content-v1";
    private static final String RUNTIME_HASH_DOMAIN = "profile-import-prompt-runtime-v1";

    private final ProfileImportPromptContract contract;
    private final ProfileImportPromptPolicy policy;

    public ProfileImportPromptRenderer(
            ProfileImportPromptContract contract,
            ProfileImportPromptPolicy policy) {
        this.contract = contract;
        this.policy = policy;
    }

    /** Computes the seven-field persisted content hash after LF normalization. */
    public String contentSha256(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version) {
        validate(template, version);
        return contentSha256Unchecked(template, version);
    }

    /** Renders exact model inputs and computes their four-field runtime hash. */
    public ProfileImportPromptRuntime render(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version) {
        validate(template, version);
        String contentSha256 = contentSha256Unchecked(template, version);
        String systemPrompt = normalizeLf(version.getSystemPromptBody())
                + "\n\n"
                + contract.systemSuffix(template.getScene());
        String repairPrompt = normalizeLf(version.getRepairPromptBody())
                + "\n\n"
                + contract.repairSuffix();
        policy.validateRenderedSystem(systemPrompt);
        String runtimeSha256 = framedSha256(List.of(
                RUNTIME_HASH_DOMAIN,
                contentSha256,
                systemPrompt,
                repairPrompt));
        return new ProfileImportPromptRuntime(
                template.getTemplateId(),
                template.getTemplateCode(),
                template.getScene(),
                version.getPromptVersionId(),
                version.getVersionNo(),
                version.getSchemaVersion(),
                version.getContractVersion(),
                systemPrompt,
                repairPrompt,
                runtimeSha256);
    }

    String framedSha256ForTest(String... fields) {
        return framedSha256(Arrays.asList(fields));
    }

    static String normalizeLf(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private void validate(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version) {
        policy.validateTemplateAndVersion(template, version);
        policy.validateBodies(version.getSystemPromptBody(), version.getRepairPromptBody());
    }

    private String contentSha256Unchecked(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version) {
        return framedSha256(List.of(
                CONTENT_HASH_DOMAIN,
                template.getTemplateCode(),
                template.getScene(),
                version.getSchemaVersion(),
                version.getContractVersion(),
                version.getSystemPromptBody(),
                version.getRepairPromptBody()));
    }

    private String framedSha256(List<String> fields) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                for (String field : fields) {
                    byte[] value = normalizeLf(Objects.requireNonNull(field))
                            .getBytes(StandardCharsets.UTF_8);
                    out.writeInt(value.length);
                    out.write(value);
                }
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 framing unavailable", error);
        }
    }
}
