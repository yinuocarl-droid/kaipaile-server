package com.kaipai.model.ai.dto;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public record ProfileImportWorkProofValue(
        String projectName,
        String roleName,
        String publishStatus,
        String workTypeCode,
        String roleLevelCode,
        Integer shootYear,
        Integer shootMonth,
        String platform,
        String syncSoundStatus,
        List<String> collaborators,
        String achievementText,
        String description) {

    public ProfileImportWorkProofValue {
        collaborators = collaborators == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(collaborators));
    }

    public byte[] canonicalBytes() {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(buffer);
            writeString(output, "profile-import-work-value-v2");
            writeString(output, projectName);
            writeString(output, roleName);
            writeString(output, publishStatus);
            writeString(output, workTypeCode);
            writeString(output, roleLevelCode);
            writeInteger(output, shootYear);
            writeInteger(output, shootMonth);
            writeString(output, platform);
            writeString(output, syncSoundStatus);
            writeStrings(output, collaborators);
            writeString(output, achievementText);
            writeString(output, description);
            output.flush();
            return buffer.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to canonicalize profile import work", error);
        }
    }

    public String canonical() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(canonicalBytes());
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

    private static void writeInteger(DataOutputStream output, Integer value) throws IOException {
        if (value == null) {
            output.writeByte(0);
            return;
        }
        output.writeByte(1);
        output.writeInt(value);
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
}
