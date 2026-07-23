package com.kaipai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/** Offline-only 00-199 migration gate. It never opens a database connection. */
public final class CareerProfileMigrationRunner {
    private final List<ProfileRow> profiles = new ArrayList<>();
    private int mediaAssetCount;
    private int relationCount;

    public void seedProfile(long userId, String extendedField) { profiles.add(new ProfileRow(userId, extendedField)); }
    public void seedAssetAndRelation() { mediaAssetCount++; relationCount++; }
    public InspectionReport inspect() {
        Set<Long> malformed = new LinkedHashSet<>();
        for (ProfileRow row : profiles) {
            if (row.extendedField() != null && !row.extendedField().isBlank() && !looksLikeJsonObject(row.extendedField())) malformed.add(row.userId());
        }
        return new InspectionReport(malformed, profiles.size(), mediaAssetCount, relationCount);
    }
    public DryRunReport dryRun(long userId) {
        InspectionReport before = inspect();
        Optional<ProfileRow> profile = profiles.stream().filter(row -> row.userId() == userId).findFirst();
        return new DryRunReport(profile.isPresent(), before.mediaAssetCount(), before.relationCount());
    }
    public VerificationReport verify(Baseline baseline) {
        InspectionReport report = inspect();
        return new VerificationReport(report.malformedExtendedFieldProfileIds().isEmpty(), report.profileCount() == baseline.profileCount(), report.mediaAssetCount() == baseline.mediaAssetCount(), report.relationCount() == baseline.relationCount());
    }
    public static Baseline loadBaseline() throws IOException {
        try (InputStream input = CareerProfileMigrationRunner.class.getResourceAsStream("/profile-migration/wang-huohuo-baseline.json")) {
            if (input == null) throw new IOException("fixture missing");
            return new ObjectMapper().readValue(input, Baseline.class);
        }
    }
    private boolean looksLikeJsonObject(String value) { String trimmed = value.trim(); return trimmed.startsWith("{") && trimmed.endsWith("}"); }
    public record ProfileRow(long userId, String extendedField) {}
    public record InspectionReport(Set<Long> malformedExtendedFieldProfileIds, int profileCount, int mediaAssetCount, int relationCount) {}
    public record DryRunReport(boolean profileFound, int mediaAssetCount, int relationCount) {}
    public record VerificationReport(boolean fieldsClean, boolean profileCountMatches, boolean mediaAssetCountMatches, boolean relationCountMatches) { public boolean passed() { return fieldsClean && profileCountMatches && mediaAssetCountMatches && relationCountMatches; } }
    public static class Baseline { public int profileCount; public int mediaAssetCount; public int relationCount; public Baseline() {} public int profileCount(){return profileCount;} public int mediaAssetCount(){return mediaAssetCount;} public int relationCount(){return relationCount;} }
}
