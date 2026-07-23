package com.kaipai.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CareerProfileSchemaMigrationTest {

    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");

    @Test
    void profileAndWorkMigrationDeclaresRequiredAdditiveColumns() throws IOException {
        String sql = readMigration("V20260723_001__career_profile_domain_foundation.sql");

        assertContainsAll(sql,
                "actor_profile",
                "avatar_asset_id",
                "current_resume_asset_id",
                "birth_year",
                "birth_month",
                "birth_day",
                "birth_precision",
                "origin_place",
                "school_name",
                "major_name",
                "language_tags_json",
                "specialty_tags_json",
                "role_type_tags_json",
                "professional_ability_tags_json",
                "work_library_version",
                "actor_experience",
                "publish_status",
                "work_type_code",
                "role_level_code",
                "sync_sound_status",
                "collaborators_json",
                "achievement_text",
                "normalized_drama_name",
                "normalized_role_name",
                "dedupe_key",
                "source_type",
                "actor_profile_representative_work");
        assertFalse(sql.contains("uk_actor_experience_user_active_dedupe"),
                "active work dedupe uniqueness must wait for normalized backfill");
        assertAdditiveOnly(sql);
    }

    @Test
    void assetMigrationDeclaresTypedRelationsWithoutPermanentUrls() throws IOException {
        String sql = readMigration("V20260723_002__actor_media_asset_relations.sql");

        assertContainsAll(sql,
                "actor_media_asset",
                "actor_media_asset_page",
                "actor_profile_asset",
                "actor_work_asset",
                "share_card_work",
                "share_card_asset",
                "object_key",
                "thumbnail_object_key",
                "process_status",
                "usage_code",
                "sort_no");
        assertFalse(sql.contains("owner_type"), "typed relations must not use owner_type");
        assertFalse(sql.contains("owner_id"), "typed relations must not use owner_id");
        assertFalse(sql.contains("access_url"), "signed access URLs must not be persisted");
        assertFalse(sql.contains("public_url"), "permanent public URLs must not be persisted");
        assertFalse(sql.contains("object_key(255)"),
                "object identity uniqueness must cover the full persisted key, not a prefix");
        assertAdditiveOnly(sql);
    }

    @Test
    void favoriteMigrationDeclaresActiveUniqueRelationship() throws IOException {
        String sql = readMigration("V20260723_003__share_card_favorite.sql");

        assertContainsAll(sql,
                "share_card_favorite",
                "favorite_id",
                "user_id",
                "share_card_id",
                "active_share_card_id",
                "generated always as",
                "stored",
                "uk_share_card_favorite_user_active_card",
                "idx_share_card_favorite_user_created",
                "idx_share_card_favorite_card");
        assertAdditiveOnly(sql);
    }

    @Test
    void migrationsKeepOneExecutableStatementPerLineForRepositoryRunner() throws IOException {
        assertRunnerCompatibleStatements("V20260723_001__career_profile_domain_foundation.sql");
        assertRunnerCompatibleStatements("V20260723_002__actor_media_asset_relations.sql");
        assertRunnerCompatibleStatements("V20260723_003__share_card_favorite.sql");
    }

    private static String readMigration(String filename) throws IOException {
        Path migration = MIGRATION_DIR.resolve(filename);
        assertTrue(Files.isRegularFile(migration), "migration is missing: " + migration);
        return Files.readString(migration, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
    }

    private static void assertContainsAll(String sql, String... tokens) {
        for (String token : tokens) {
            assertTrue(sql.contains(token.toLowerCase(Locale.ROOT)), "migration must contain: " + token);
        }
    }

    private static void assertAdditiveOnly(String sql) {
        assertFalse(sql.contains("drop table"), "foundation migration must not drop tables");
        assertFalse(sql.contains("drop column"), "foundation migration must not drop columns");
    }

    private static void assertRunnerCompatibleStatements(String filename) throws IOException {
        Path migration = MIGRATION_DIR.resolve(filename);
        for (String line : Files.readAllLines(migration, StandardCharsets.UTF_8)) {
            long semicolonCount = line.chars().filter(character -> character == ';').count();
            assertTrue(semicolonCount <= 1,
                    "repository migration runner requires one statement per line: " + filename + " -> " + line);
        }
    }
}
