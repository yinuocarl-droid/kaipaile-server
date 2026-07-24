package com.kaipai.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CareerProfileSchemaMigrationTest {

    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");
    private static final List<TableColumns> REQUIRED_COLUMNS = List.of(
            columns("actor_profile",
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
                    "work_library_version"),
            columns("actor_experience",
                    "publish_status",
                    "work_type_code",
                    "role_level_code",
                    "sync_sound_status",
                    "collaborators_json",
                    "achievement_text",
                    "normalized_drama_name",
                    "normalized_role_name",
                    "dedupe_key",
                    "source_type"),
            columns("actor_profile_representative_work",
                    "relation_id",
                    "actor_profile_id",
                    "experience_id",
                    "sort_no",
                    "active_experience_id",
                    "active_sort_no"),
            columns("actor_media_asset",
                    "asset_id",
                    "user_id",
                    "media_type",
                    "category_code",
                    "storage_provider",
                    "bucket_code",
                    "object_key",
                    "thumbnail_object_key",
                    "original_name",
                    "mime_type",
                    "size_bytes",
                    "duration_ms",
                    "page_count",
                    "process_status",
                    "failure_code",
                    "failure_message",
                    "source_type"),
            columns("actor_media_asset_page",
                    "page_id",
                    "asset_id",
                    "page_no",
                    "image_object_key",
                    "process_status",
                    "active_page_no"),
            columns("actor_profile_asset",
                    "relation_id",
                    "actor_profile_id",
                    "asset_id",
                    "usage_code",
                    "sort_no",
                    "active_asset_id"),
            columns("actor_work_asset",
                    "relation_id",
                    "experience_id",
                    "asset_id",
                    "usage_code",
                    "sort_no",
                    "active_asset_id"),
            columns("share_card_work",
                    "relation_id",
                    "share_card_id",
                    "experience_id",
                    "sort_no",
                    "active_experience_id",
                    "active_sort_no"),
            columns("share_card_asset",
                    "relation_id",
                    "share_card_id",
                    "asset_id",
                    "usage_code",
                    "sort_no",
                    "active_asset_id"),
            columns("share_card_favorite",
                    "favorite_id",
                    "user_id",
                    "share_card_id",
                    "active_share_card_id"));
    private static final List<TableColumns> REQUIRED_GENERATED_COLUMNS = List.of(
            columns("actor_profile_representative_work", "active_experience_id", "active_sort_no"),
            columns("actor_media_asset_page", "active_page_no"),
            columns("actor_profile_asset", "active_asset_id"),
            columns("actor_work_asset", "active_asset_id"),
            columns("share_card_work", "active_experience_id", "active_sort_no"),
            columns("share_card_asset", "active_asset_id"),
            columns("share_card_favorite", "active_share_card_id"));
    private static final List<IndexExpectation> REQUIRED_INDEXES = List.of(
            uniqueIndex("actor_profile_representative_work", "uk_profile_representative_active_work",
                    "actor_profile_id", "active_experience_id"),
            uniqueIndex("actor_profile_representative_work", "uk_profile_representative_active_sort",
                    "actor_profile_id", "active_sort_no"),
            queryIndex("actor_profile_representative_work", "idx_profile_representative_active",
                    "actor_profile_id", "deleted", "sort_no"),
            queryIndex("actor_profile_representative_work", "idx_profile_representative_experience",
                    "experience_id", "deleted"),
            uniqueIndex("actor_media_asset", "uk_actor_media_asset_object",
                    "storage_provider", "bucket_code", "object_key"),
            queryIndex("actor_media_asset", "idx_actor_media_asset_user_media_status",
                    "user_id", "media_type", "process_status", "deleted", "create_time"),
            queryIndex("actor_media_asset", "idx_actor_media_asset_owner_category",
                    "user_id", "category_code", "deleted", "create_time"),
            uniqueIndex("actor_media_asset_page", "uk_actor_media_asset_page_active",
                    "asset_id", "active_page_no"),
            queryIndex("actor_media_asset_page", "idx_actor_media_asset_page_order",
                    "asset_id", "deleted", "page_no"),
            uniqueIndex("actor_profile_asset", "uk_actor_profile_asset_active",
                    "actor_profile_id", "usage_code", "active_asset_id"),
            queryIndex("actor_profile_asset", "idx_actor_profile_asset_order",
                    "actor_profile_id", "usage_code", "deleted", "sort_no"),
            queryIndex("actor_profile_asset", "idx_actor_profile_asset_asset", "asset_id", "deleted"),
            uniqueIndex("actor_work_asset", "uk_actor_work_asset_active",
                    "experience_id", "usage_code", "active_asset_id"),
            queryIndex("actor_work_asset", "idx_actor_work_asset_order",
                    "experience_id", "usage_code", "deleted", "sort_no"),
            queryIndex("actor_work_asset", "idx_actor_work_asset_asset", "asset_id", "deleted"),
            uniqueIndex("share_card_work", "uk_share_card_work_active",
                    "share_card_id", "active_experience_id"),
            uniqueIndex("share_card_work", "uk_share_card_work_sort", "share_card_id", "active_sort_no"),
            queryIndex("share_card_work", "idx_share_card_work_order", "share_card_id", "deleted", "sort_no"),
            queryIndex("share_card_work", "idx_share_card_work_experience", "experience_id", "deleted"),
            uniqueIndex("share_card_asset", "uk_share_card_asset_active",
                    "share_card_id", "usage_code", "active_asset_id"),
            queryIndex("share_card_asset", "idx_share_card_asset_order",
                    "share_card_id", "usage_code", "deleted", "sort_no"),
            queryIndex("share_card_asset", "idx_share_card_asset_asset", "asset_id", "deleted"),
            uniqueIndex("share_card_favorite", "uk_share_card_favorite_user_active_card",
                    "user_id", "active_share_card_id"),
            queryIndex("share_card_favorite", "idx_share_card_favorite_user_created",
                    "user_id", "deleted", "create_time"),
            queryIndex("share_card_favorite", "idx_share_card_favorite_card", "share_card_id", "deleted"));

    @Test
    void schemaContainsProfileWorkAssetAndFavoriteFoundation() throws Exception {
        try (Connection connection = MigrationTestDatabase.apply(
                "V20260723_001__career_profile_domain_foundation.sql",
                "V20260723_002__actor_media_asset_relations.sql",
                "V20260723_003__share_card_favorite.sql")) {
            assertMetadataLookupsRequireExactNames(connection);
            for (TableColumns table : REQUIRED_COLUMNS) {
                assertTable(connection, table.tableName());
                for (String columnName : table.columnNames()) {
                    assertColumn(connection, table.tableName(), columnName);
                }
            }
            for (TableColumns table : REQUIRED_GENERATED_COLUMNS) {
                for (String columnName : table.columnNames()) {
                    assertGeneratedColumn(connection, table.tableName(), columnName);
                }
            }
            for (IndexExpectation index : REQUIRED_INDEXES) {
                assertIndex(connection, index);
            }
            assertIndexMissing(connection, "actor_experience", "uk_actor_experience_user_active_dedupe");
        }
    }

    private static void assertMetadataLookupsRequireExactNames(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE metadataXpatternXtable (
                      probeXcolumn INT,
                      probeXgenerated INT GENERATED ALWAYS AS (probeXcolumn + 1) STORED
                    ) ENGINE=InnoDB
                    """);
        }

        assertThrows(AssertionError.class,
                () -> assertTable(connection, "metadata_pattern_table"),
                "underscore patterns must not satisfy an exact table lookup");
        assertThrows(AssertionError.class,
                () -> assertColumn(connection, "metadataXpatternXtable", "probe_column"),
                "underscore patterns must not satisfy an exact column lookup");
        assertThrows(AssertionError.class,
                () -> assertGeneratedColumn(connection, "metadataXpatternXtable", "probe_generated"),
                "underscore patterns must not satisfy an exact generated-column lookup");
    }

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

    private static void assertTable(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        boolean found = false;
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, tableName, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found, "table must exist: " + tableName);
    }

    private static void assertColumn(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        boolean found = false;
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            while (columns.next()) {
                if (tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found, "column must exist: " + tableName + "." + columnName);
    }

    private static void assertGeneratedColumn(Connection connection, String tableName, String columnName)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        boolean found = false;
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            while (columns.next()) {
                if (!tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        || !columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    continue;
                }
                found = true;
                assertNotNull(columns.getString("IS_GENERATEDCOLUMN"),
                        "driver must expose generated-column metadata for " + tableName + "." + columnName);
                assertTrue("YES".equalsIgnoreCase(columns.getString("IS_GENERATEDCOLUMN")),
                        "column must be generated: " + tableName + "." + columnName);
                break;
            }
        }
        assertTrue(found, "generated column must exist: " + tableName + "." + columnName);
    }

    private static void assertIndex(Connection connection, IndexExpectation expected) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        List<IndexColumn> actualColumns = new ArrayList<>();
        Boolean actualNonUnique = null;
        try (ResultSet indexes = metadata.getIndexInfo(
                connection.getCatalog(), null, expected.tableName(), false, false)) {
            while (indexes.next()) {
                if (!expected.indexName().equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    continue;
                }
                boolean rowNonUnique = indexes.getBoolean("NON_UNIQUE");
                if (actualNonUnique == null) {
                    actualNonUnique = rowNonUnique;
                } else {
                    assertEquals(actualNonUnique, rowNonUnique,
                            "index uniqueness metadata must be consistent: " + expected.qualifiedName());
                }
                String columnName = indexes.getString("COLUMN_NAME");
                assertNotNull(columnName, "index column must be named: " + expected.qualifiedName());
                actualColumns.add(new IndexColumn(indexes.getShort("ORDINAL_POSITION"),
                        columnName.toLowerCase(Locale.ROOT)));
            }
        }

        assertFalse(actualColumns.isEmpty(), "index must exist: " + expected.qualifiedName());
        actualColumns.sort(Comparator.comparingInt(IndexColumn::ordinalPosition));
        assertEquals(expected.columnNames(),
                actualColumns.stream().map(IndexColumn::columnName).toList(),
                "index columns must match in order: " + expected.qualifiedName());
        assertEquals(expected.unique(), !actualNonUnique,
                "index uniqueness must match: " + expected.qualifiedName());
    }

    private static void assertIndexMissing(Connection connection, String tableName, String indexName)
            throws SQLException {
        assertFalse(hasIndex(connection, tableName, indexName),
                "index must not exist before normalized work backfill: " + tableName + "." + indexName);
    }

    private static boolean hasIndex(Connection connection, String tableName, String indexName)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(
                connection.getCatalog(), null, tableName, false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static TableColumns columns(String tableName, String... columnNames) {
        return new TableColumns(tableName, List.of(columnNames));
    }

    private static IndexExpectation uniqueIndex(String tableName, String indexName, String... columnNames) {
        return new IndexExpectation(tableName, indexName, true, List.of(columnNames));
    }

    private static IndexExpectation queryIndex(String tableName, String indexName, String... columnNames) {
        return new IndexExpectation(tableName, indexName, false, List.of(columnNames));
    }

    private record TableColumns(String tableName, List<String> columnNames) {
    }

    private record IndexExpectation(
            String tableName,
            String indexName,
            boolean unique,
            List<String> columnNames) {

        private String qualifiedName() {
            return tableName + "." + indexName;
        }
    }

    private record IndexColumn(int ordinalPosition, String columnName) {
    }
}
