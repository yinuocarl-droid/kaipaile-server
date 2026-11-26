package com.kaipai.service.ai.profileimport;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public final class ProfileImportPromptFixtureCatalog {

    private static final String HASH_DOMAIN = "profile-import-prompt-fixture-v1";
    private static final String FIXTURE_VERSION = "1";
    private static final String FULL_PROFILE_CODE = "full-profile-v1";
    private static final String WORKS_ONLY_CODE = "works-only-v1";
    private static final String FULL_PROFILE_RESOURCE =
            "classpath:ai/profile-import/prompt-fixtures/full-profile-v1.txt";
    private static final String WORKS_ONLY_RESOURCE =
            "classpath:ai/profile-import/prompt-fixtures/works-only-v1.txt";

    private final ResourceLoader resourceLoader;

    public ProfileImportPromptFixtureCatalog(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public Fixture load(String scene) {
        String code;
        String location;
        if ("full_profile".equals(scene)) {
            code = FULL_PROFILE_CODE;
            location = FULL_PROFILE_RESOURCE;
        } else if ("works_only".equals(scene)) {
            code = WORKS_ONLY_CODE;
            location = WORKS_ONLY_RESOURCE;
        } else {
            throw new IllegalArgumentException("unsupported profile import fixture scene");
        }
        String body = readNormalized(location);
        String sha256 = framedSha256(List.of(HASH_DOMAIN, code, FIXTURE_VERSION, body));
        return new Fixture(code, FIXTURE_VERSION, sha256, body);
    }

    private String readNormalized(String location) {
        Resource resource = resourceLoader.getResource(location);
        try (var input = resource.getInputStream()) {
            String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
            return normalized.endsWith("\n")
                    ? normalized.substring(0, normalized.length() - 1)
                    : normalized;
        } catch (IOException error) {
            throw new IllegalStateException("profile import prompt fixture unavailable", error);
        }
    }

    private String framedSha256(List<String> fields) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                for (String field : fields) {
                    byte[] value = field.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(value.length);
                    output.write(value);
                }
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 framing unavailable", error);
        }
    }

    public record Fixture(String code, String version, String sha256, String body) {
        @Override
        public String toString() {
            return "Fixture[code=" + code + ", version=" + version + ", sha256=" + sha256 + "]";
        }
    }
}
