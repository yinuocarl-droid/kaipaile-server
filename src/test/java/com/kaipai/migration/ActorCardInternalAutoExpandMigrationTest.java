package com.kaipai.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class ActorCardInternalAutoExpandMigrationTest {

    private static final String MIGRATION = "V20260901_001__actor_card_internal_auto_expand.sql";

    @Test
    void migrationIsRepeatableAndSupportsStablePrivateAssetTasks() throws Exception {
        try (Connection connection = MigrationTestDatabase.apply(
                "V20260509_033__ai_profile_card_generation.sql",
                "V20260731_001__actor_card_tables.sql",
                MIGRATION,
                MIGRATION)) {
            assertColumn(connection, "actor_ai_profile_card_task", "actor_card_id", true);
            assertColumn(connection, "actor_ai_profile_card_task", "source_asset_id", true);
            assertColumn(connection, "actor_ai_profile_card_task", "source_fingerprint", true);
            assertColumn(connection, "actor_ai_profile_card_task", "source_image_url", true);
            assertColumn(connection, "actor_card", "expanded_source_fingerprint", true);
            assertUniqueIndex(connection, "uk_actor_card_expand_source");

            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO actor_card (user_id) VALUES (7)
                        """);
                statement.executeUpdate("""
                        INSERT INTO actor_ai_profile_card_task (
                          task_id, user_id, actor_profile_id, actor_card_id,
                          source_asset_id, source_fingerprint,
                          template_scene_code, style_code, source_image_url,
                          provider_code, generation_mode, status
                        ) VALUES (
                          'acexp_asset_1', 7, -1, 1,
                          51, REPEAT('a', 64),
                          'expand_image', 'expand_image', NULL,
                          'mock', 'expand_image', 'pending'
                        )
                        """);
                assertThrows(SQLException.class, () -> statement.executeUpdate("""
                        INSERT INTO actor_ai_profile_card_task (
                          task_id, user_id, actor_profile_id, actor_card_id,
                          source_asset_id, source_fingerprint,
                          template_scene_code, style_code, source_image_url,
                          provider_code, generation_mode, status
                        ) VALUES (
                          'acexp_asset_2', 7, -1, 1,
                          51, REPEAT('a', 64),
                          'expand_image', 'expand_image', NULL,
                          'mock', 'expand_image', 'pending'
                        )
                        """));
            }
        }
    }

    private void assertColumn(
            Connection connection,
            String table,
            String column,
            boolean nullable) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            assertEquals(true, columns.next(), table + "." + column + " is missing");
            assertEquals(nullable ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls,
                    columns.getInt("NULLABLE"));
            assertFalse(columns.next(), table + "." + column + " is duplicated");
        }
    }

    private void assertUniqueIndex(Connection connection, String indexName) throws SQLException {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, "actor_ai_profile_card_task", true, false)) {
            int columns = 0;
            while (indexes.next()) {
                if (indexName.equals(indexes.getString("INDEX_NAME"))) {
                    assertFalse(indexes.getBoolean("NON_UNIQUE"));
                    columns++;
                }
            }
            assertEquals(4, columns);
        }
    }
}
