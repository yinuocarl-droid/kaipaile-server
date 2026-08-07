package com.kaipai.service.ai.profileimport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kaipai.model.ai.dto.ProfileImportWorkProofValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileImportCandidateProofServiceTest {
    private final ProfileImportCandidateProofService service =
            new ProfileImportCandidateProofService("test-secret");

    @Test
    void profileProofIsV2AndBindsEveryTypedField() {
        String proof = service.issueProfile(
                7L, "req-1", "gender-1", "gender", "female", "inferred_from_roles", true);

        assertTrue(proof.startsWith("v2."));
        assertTrue(service.verifyProfile(proof,
                7L, "req-1", "gender-1", "gender", "female", "inferred_from_roles", true));
        assertFalse(service.verifyProfile(proof,
                8L, "req-1", "gender-1", "gender", "female", "inferred_from_roles", true));
        assertFalse(service.verifyProfile(proof,
                7L, "req-2", "gender-1", "gender", "female", "inferred_from_roles", true));
        assertFalse(service.verifyProfile(proof,
                7L, "req-1", "gender-1", "public_name", "female", "inferred_from_roles", true));
        assertFalse(service.verifyProfile(proof,
                7L, "req-1", "gender-1", "gender", "male", "inferred_from_roles", true));
        assertFalse(service.verifyProfile(proof,
                7L, "req-1", "gender-1", "gender", "female", "direct", true));
        assertFalse(service.verifyProfile(proof,
                7L, "req-1", "gender-1", "gender", "female", "inferred_from_roles", false));
    }

    @Test
    void profileCanonicalCannotBeReboundThroughDelimiterCollision() {
        String proof = service.issueProfile(
                7L, "req|candidate", "id", "public_name", "value", "direct", false);

        assertFalse(service.verifyProfile(proof,
                7L, "req", "candidate|id", "public_name", "value", "direct", false));
    }

    @Test
    void workProofBindsMatchTargetAndAllowedActions() {
        ProfileImportWorkProofValue value = workValue("作品一", "角色一", List.of("甲", "乙"));
        String proof = service.issueWork(
                7L, "req-1", "work-1", value, "direct", "field_conflict", 99L,
                List.of("merge", "skip"), List.of("achievementText"));

        assertTrue(proof.startsWith("v2."));
        assertTrue(service.verifyWork(proof,
                7L, "req-1", "work-1", value, "direct", "field_conflict", 99L,
                List.of("merge", "skip"), List.of("achievementText")));
        assertFalse(service.verifyWork(proof,
                8L, "req-1", "work-1", value, "direct", "field_conflict", 99L,
                List.of("merge", "skip"), List.of("achievementText")));
        assertFalse(service.verifyWork(proof,
                7L, "req-1", "work-1", value, "direct", "field_conflict", 100L,
                List.of("merge", "skip"), List.of("achievementText")));
        assertFalse(service.verifyWork(proof,
                7L, "req-1", "work-1", value, "direct", "field_conflict", 99L,
                List.of("skip"), List.of("achievementText")));
        assertFalse(service.verifyWork(proof,
                7L, "req-1", "work-1", value, "direct", "field_conflict", 99L,
                List.of("merge", "skip"), List.of("description")));
    }

    @Test
    void workValueCanonicalHasNoFieldOrListDelimiterCollision() {
        ProfileImportWorkProofValue first = workValue("作品|角色", "名", List.of("甲,乙", "丙"));
        ProfileImportWorkProofValue second = workValue("作品", "角色|名", List.of("甲", "乙,丙"));

        assertNotEquals(first.canonical(), second.canonical());
        String proof = service.issueWork(
                7L, "req-1", "work-1", first, "direct", "new", null, List.of("create"), List.of());
        assertFalse(service.verifyWork(proof,
                7L, "req-1", "work-1", second, "direct", "new", null, List.of("create"), List.of()));
    }

    @Test
    void legacyV1ProofIsRejected() {
        assertFalse(service.verifyProfile(
                "YXJlZ3VsYXItdjEtaG1hYw", 7L, "req-1", "gender-1", "gender", "female", "direct", false));
    }

    private ProfileImportWorkProofValue workValue(
            String projectName, String roleName, List<String> collaborators) {
        return new ProfileImportWorkProofValue(
                projectName, roleName, "aired", "series", "supporting", 2025, 7,
                "平台", "sync", collaborators, "成绩", "描述");
    }
}
