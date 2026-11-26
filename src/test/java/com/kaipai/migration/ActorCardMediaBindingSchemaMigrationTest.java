package com.kaipai.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ActorCardMediaBindingSchemaMigrationTest {

    private static final String MIGRATION = "V20260818_001__actor_card_media_binding.sql";
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");

    @Test
    void createsBindingTablesGeneratedColumnAndIndexes() throws Exception {
        try (Connection connection = MigrationTestDatabase.apply(MIGRATION)) {
            assertTable(connection, "actor_card_media_set");
            assertTable(connection, "actor_card_media_item");
            assertColumn(connection, "actor_card_media_set", "card_id");
            assertColumn(connection, "actor_card_media_set", "card_work_id");
            assertColumn(connection, "actor_card_media_set", "usage_code");
            assertColumn(connection, "actor_card_media_item", "asset_id");
            assertColumn(connection, "actor_card_media_item", "legacy_url");
            assertGeneratedColumn(connection, "actor_card_media_item", "active_asset_id");
            assertIndex(connection, "actor_card_media_set", "idx_actor_card_media_set_card_usage");
            assertIndex(connection, "actor_card_media_set", "idx_actor_card_media_set_work");
            assertIndex(connection, "actor_card_media_item", "uk_actor_card_media_item_active_asset");
            assertIndex(connection, "actor_card_media_item", "idx_actor_card_media_item_set_order");
            assertIndex(connection, "actor_card_media_item", "idx_actor_card_media_item_asset");
        }
    }

    @Test
    void migrationIsRepeatableAndDoesNotPersistAccessUrls() throws IOException {
        String sql = Files.readString(MIGRATION_DIR.resolve(MIGRATION), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("create table if not exists `actor_card_media_set`"));
        assertTrue(sql.contains("create table if not exists `actor_card_media_item`"));
        assertTrue(sql.contains("generated always as"));
        assertTrue(sql.contains("chk_actor_card_media_set_scope"));
        assertTrue(sql.contains("chk_actor_card_media_item_source"));
        assertFalse(sql.contains("drop table"));
        assertFalse(sql.contains("drop column"));
        assertFalse(sql.contains("access_url"));
        assertFalse(sql.contains("signed_url"));

        for (String line : Files.readAllLines(MIGRATION_DIR.resolve(MIGRATION), StandardCharsets.UTF_8)) {
            long semicolonCount = line.chars().filter(character -> character == ';').count();
            assertTrue(semicolonCount <= 1,
                    "repository migration runner requires one statement terminator per line: " + line);
        }
    }

    private static void assertTable(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, tableName, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return;
                }
            }
        }
        throw new AssertionError("table must exist: " + tableName);
    }

    private static void assertColumn(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            while (columns.next()) {
                if (tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return;
                }
            }
        }
        throw new AssertionError("column must exist: " + tableName + "." + columnName);
    }

    private static void assertGeneratedColumn(Connection connection, String tableName, String columnName)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            while (columns.next()) {
                if (!tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        || !columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    continue;
                }
                assertTrue("YES".equalsIgnoreCase(columns.getString("IS_GENERATEDCOLUMN")),
                        "column must be generated: " + tableName + "." + columnName);
                return;
            }
        }
        throw new AssertionError("generated column must exist: " + tableName + "." + columnName);
    }

    private static void assertIndex(Connection connection, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        throw new AssertionError("index must exist: " + tableName + "." + indexName);
    }
}
