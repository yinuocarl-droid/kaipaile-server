package com.kaipai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Temporary JDBC runner for inspecting and applying SQL migrations against the dev database.
 *
 * Usage:
 * 1. inspect current state:
 *    mvn -q -Dexec.classpathScope=test -Dexec.mainClass=com.kaipai.DbMigrationRunner
 *      -Dexec.args="inspect" org.codehaus.mojo:exec-maven-plugin:3.6.1:java
 * 2. apply baseline/governance:
 *    mvn -q -Dexec.classpathScope=test -Dexec.mainClass=com.kaipai.DbMigrationRunner
 *      -Dexec.args="apply V20260331_001__platform_admin_baseline.sql"
 *      org.codehaus.mojo:exec-maven-plugin:3.6.1:java
 */
public class DbMigrationRunner {

    private static final String DB_URL = "jdbc:mysql://101.43.57.62:3306/kaipai_dev?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "root123456";
    private static final String DB_NAME = "kaipai_dev";
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");
    private static final Path BACKUP_DIR = Path.of("..", ".sce", "backups", "20260425-db-share-card-runtime-before-physical-cleanup");
    private static final String RETIRED_SCENE_COLUMN = "scene" + "_key";
    private static final String RETIRED_OWNER_COLUMN = "owner" + "_user" + "_id";
    private static final String RETIRED_CARD_CONFIG_COLUMN = "latest" + "_config" + "_id";
    private static final String RETIRED_CONTACT_CONFIG_COLUMN = "actor_card" + "_config" + "_id";
    private static final String RETIRED_TEMPLATE_SCENE_CODE = "gen" + "eral";
    private static final String TEMPLATE_SCENE_CODE_CHECK = "chk_card_scene_template_template_scene_code";
    private static final String SHARE_PREFERENCE_ARTIFACT_CHECK = "chk_actor_share_preference_preferred_artifact";
    private static final String REMOVED_RECRUIT_MENU_PERMISSION = "menu." + "recruit";

    private static final List<String> BASELINE_TABLES = Arrays.asList(
            "identity_verification",
            "invite_code",
            "referral_record",
            "referral_policy",
            "capability_product",
            "capability_account",
            "capability_change_log",
            "payment_order",
            "payment_transaction",
            "refund_order",
            "refund_operate_log",
            "card_scene_template",
            "actor_card_config",
            "actor_share_preference",
            "user_entitlement_grant",
            "entitlement_rule",
            "admin_user",
            "admin_role",
            "admin_user_role",
            "admin_operation_log",
            "template_publish_log"
    );

    private static final List<ColumnCheck> GOVERNANCE_COLUMNS = Arrays.asList(
            new ColumnCheck("admin_role", "menu_permissions_json"),
            new ColumnCheck("admin_role", "page_permissions_json"),
            new ColumnCheck("admin_role", "action_permissions_json"),
            new ColumnCheck("template_publish_log", "target_type"),
            new ColumnCheck("template_publish_log", "target_code"),
            new ColumnCheck("template_publish_log", "draft_version"),
            new ColumnCheck("template_publish_log", "source_version"),
            new ColumnCheck("template_publish_log", "target_version"),
            new ColumnCheck("template_publish_log", "diff_summary_json")
    );

    private static final List<ColumnCheck> SHARE_CARD_REQUIRED_COLUMNS = Arrays.asList(
            new ColumnCheck("card_scene_template", "template_scene_code"),
            new ColumnCheck("actor_card_config", "share_card_id"),
            new ColumnCheck("actor_share_preference", "share_card_id"),
            new ColumnCheck("user_share_card", "template_id"),
            new ColumnCheck("share_card_contact_request", "share_card_id"),
            new ColumnCheck("share_card_view_history", "share_card_id")
    );

    private static final List<ColumnCheck> SHARE_CARD_RETIRED_COLUMNS = Arrays.asList(
            new ColumnCheck("card_scene_template", RETIRED_SCENE_COLUMN),
            new ColumnCheck("actor_card_config", "user_id"),
            new ColumnCheck("actor_card_config", "actor_profile_id"),
            new ColumnCheck("actor_card_config", RETIRED_SCENE_COLUMN),
            new ColumnCheck("actor_card_config", "template_id"),
            new ColumnCheck("actor_share_preference", "user_id"),
            new ColumnCheck("actor_share_preference", RETIRED_SCENE_COLUMN),
            new ColumnCheck("actor_share_preference", "preferred_tone"),
            new ColumnCheck("user_share_card", RETIRED_SCENE_COLUMN),
            new ColumnCheck("user_share_card", RETIRED_CARD_CONFIG_COLUMN),
            new ColumnCheck("share_card_contact_request", RETIRED_OWNER_COLUMN),
            new ColumnCheck("share_card_contact_request", RETIRED_CONTACT_CONFIG_COLUMN),
            new ColumnCheck("share_card_contact_request", RETIRED_SCENE_COLUMN),
            new ColumnCheck("share_card_view_history", RETIRED_OWNER_COLUMN),
            new ColumnCheck("share_card_view_history", RETIRED_SCENE_COLUMN)
    );

    private static final List<String> SHARE_CARD_RUNTIME_TABLES = Arrays.asList(
            "card_scene_template",
            "actor_card_config",
            "actor_share_preference",
            "user_share_card",
            "share_card_contact_request",
            "share_card_view_history"
    );

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "inspect".equalsIgnoreCase(args[0])) {
            inspect();
            return;
        }

        if ("apply".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                throw new IllegalArgumentException("apply mode requires a migration filename");
            }
            apply(args[1]);
            return;
        }

        if ("backup-share-card-runtime".equalsIgnoreCase(args[0])) {
            backupShareCardRuntime();
            return;
        }

        throw new IllegalArgumentException("Unsupported mode: " + args[0]);
    }

    private static void inspect() throws SQLException {
        List<String> violations = new ArrayList<>();
        try (Connection connection = openConnection()) {
            System.out.println("== baseline tables ==");
            for (String tableName : BASELINE_TABLES) {
                System.out.printf("%s: %s%n", tableName, tableExists(connection, tableName) ? "EXISTS" : "MISSING");
            }

            System.out.println();
            System.out.println("== governance columns ==");
            for (ColumnCheck check : GOVERNANCE_COLUMNS) {
                System.out.printf("%s.%s: %s%n", check.tableName, check.columnName,
                        columnExists(connection, check.tableName, check.columnName) ? "EXISTS" : "MISSING");
            }

            System.out.println();
            System.out.printf("user.invited_by_user_id: %s%n",
                    columnExists(connection, "user", "invited_by_user_id") ? "EXISTS" : "MISSING");
            System.out.printf("user.valid_invite_count: %s%n",
                    columnExists(connection, "user", "valid_invite_count") ? "EXISTS" : "MISSING");
            System.out.printf("user.register_device_fingerprint: %s%n",
                    columnExists(connection, "user", "register_device_fingerprint") ? "EXISTS" : "MISSING");
            System.out.printf("actor_profile.birth_hour: %s%n",
                    columnExists(connection, "actor_profile", "birth_hour") ? "EXISTS" : "MISSING");

            System.out.println();
            System.out.println("== share-card runtime residue ==");
            System.out.printf("user_share_card total: %d%n",
                    queryForLong(connection, "SELECT COUNT(*) FROM user_share_card", new Object[0]));
            System.out.printf("share_card_view_history share_card_id IS NULL: %d%n",
                    queryForLong(connection, "SELECT COUNT(*) FROM share_card_view_history WHERE deleted = 0 AND share_card_id IS NULL", new Object[0]));
            System.out.printf("share_card_contact_request share_card_id IS NULL: %d%n",
                    queryForLong(connection, "SELECT COUNT(*) FROM share_card_contact_request WHERE deleted = 0 AND share_card_id IS NULL", new Object[0]));
            System.out.printf("actor_share_preference share_card_id IS NULL: %d%n",
                    queryForLong(connection, "SELECT COUNT(*) FROM actor_share_preference WHERE deleted = 0 AND share_card_id IS NULL", new Object[0]));

            System.out.println();
            System.out.println("== share-card runtime structure ==");
            for (ColumnCheck check : SHARE_CARD_REQUIRED_COLUMNS) {
                boolean exists = columnExists(connection, check.tableName, check.columnName);
                System.out.printf("%s.%s required: %s%n", check.tableName, check.columnName, exists ? "EXISTS" : "MISSING");
                if (!exists) {
                    violations.add("required column missing: " + check.tableName + "." + check.columnName);
                }
            }
            for (ColumnCheck check : SHARE_CARD_RETIRED_COLUMNS) {
                boolean exists = columnExists(connection, check.tableName, check.columnName);
                System.out.printf("%s.%s retired: %s%n", check.tableName, check.columnName, exists ? "EXISTS" : "ABSENT");
                if (exists) {
                    violations.add("retired column still exists: " + check.tableName + "." + check.columnName);
                }
            }

            System.out.println();
            System.out.println("== share-card template scene integrity ==");
            if (tableExists(connection, "card_scene_template") && columnExists(connection, "card_scene_template", "template_scene_code")) {
                long invalidTemplateSceneCodeCount = queryForLong(connection, """
                        SELECT COUNT(*)
                        FROM card_scene_template
                        WHERE template_scene_code IS NULL
                           OR TRIM(template_scene_code) = ''
                           OR template_scene_code NOT IN ('classic', 'urban', 'costume', 'commercial', 'artistic')
                        """);
                long retiredTemplateSceneCodeCount = queryForLong(connection,
                        "SELECT COUNT(*) FROM card_scene_template WHERE template_scene_code = ?",
                        RETIRED_TEMPLATE_SCENE_CODE);
                boolean hasTemplateSceneCodeCheck = constraintExists(connection, "card_scene_template", TEMPLATE_SCENE_CODE_CHECK);
                System.out.printf("card_scene_template.template_scene_code invalid: %d%n", invalidTemplateSceneCodeCount);
                System.out.printf("card_scene_template.template_scene_code retired value: %d%n", retiredTemplateSceneCodeCount);
                System.out.printf("%s: %s%n", TEMPLATE_SCENE_CODE_CHECK, hasTemplateSceneCodeCheck ? "EXISTS" : "MISSING");
                if (invalidTemplateSceneCodeCount > 0) {
                    violations.add("invalid template_scene_code rows remain: " + invalidTemplateSceneCodeCount);
                }
                if (retiredTemplateSceneCodeCount > 0) {
                    violations.add("retired template scene code rows remain: " + retiredTemplateSceneCodeCount);
                }
                if (!hasTemplateSceneCodeCheck) {
                    violations.add("template_scene_code check constraint missing: " + TEMPLATE_SCENE_CODE_CHECK);
                }
            } else {
                violations.add("card_scene_template.template_scene_code integrity cannot be inspected");
            }

            System.out.println();
            System.out.println("== share-card preference artifact integrity ==");
            if (tableExists(connection, "actor_share_preference") && columnExists(connection, "actor_share_preference", "preferred_artifact")) {
                long invalidPreferredArtifactCount = queryForLong(connection, """
                        SELECT COUNT(*)
                        FROM actor_share_preference
                        WHERE deleted = 0
                          AND (
                            preferred_artifact IS NULL
                            OR TRIM(preferred_artifact) = ''
                            OR preferred_artifact NOT IN ('miniProgramCard', 'poster')
                          )
                        """);
                long cardsMissingPreferenceCount = queryForLong(connection, """
                        SELECT COUNT(*)
                        FROM user_share_card card
                        LEFT JOIN actor_share_preference pref
                          ON pref.share_card_id = card.share_card_id
                         AND pref.deleted = 0
                        WHERE card.deleted = 0
                          AND card.share_status = 'active'
                          AND pref.preference_id IS NULL
                        """);
                boolean hasPreferredArtifactCheck = constraintExists(connection, "actor_share_preference", SHARE_PREFERENCE_ARTIFACT_CHECK);
                System.out.printf("actor_share_preference.preferred_artifact invalid: %d%n", invalidPreferredArtifactCount);
                System.out.printf("user_share_card active rows missing actor_share_preference: %d%n", cardsMissingPreferenceCount);
                System.out.printf("%s: %s%n", SHARE_PREFERENCE_ARTIFACT_CHECK, hasPreferredArtifactCheck ? "EXISTS" : "MISSING");
                if (invalidPreferredArtifactCount > 0) {
                    violations.add("invalid preferred_artifact rows remain: " + invalidPreferredArtifactCount);
                }
                if (cardsMissingPreferenceCount > 0) {
                    violations.add("active share cards missing preference rows: " + cardsMissingPreferenceCount);
                }
                if (!hasPreferredArtifactCheck) {
                    violations.add("preferred_artifact check constraint missing: " + SHARE_PREFERENCE_ARTIFACT_CHECK);
                }
            } else {
                violations.add("actor_share_preference.preferred_artifact integrity cannot be inspected");
            }

            System.out.println();
            System.out.println("== strict backend contract residue ==");
            boolean inviteRecordExists = tableExists(connection, "invite_record");
            System.out.printf("invite_record table retired: %s%n", inviteRecordExists ? "EXISTS" : "ABSENT");
            if (inviteRecordExists) {
                violations.add("retired invite_record table still exists");
            }

            long removedRecruitMenuRoleCount = queryForLong(connection, """
                    SELECT COUNT(*)
                    FROM admin_role
                    WHERE JSON_SEARCH(COALESCE(menu_permissions_json, JSON_ARRAY()), 'one', ?) IS NOT NULL
                    """, REMOVED_RECRUIT_MENU_PERMISSION);
            System.out.printf("admin_role removed recruit menu permission: %d%n", removedRecruitMenuRoleCount);
            if (removedRecruitMenuRoleCount > 0) {
                violations.add("removed recruit menu permission remains in admin_role: " + removedRecruitMenuRoleCount);
            }

            long templateRemovedArtifactCount = queryForLong(connection, """
                    SELECT COUNT(*)
                    FROM card_scene_template
                    WHERE JSON_CONTAINS_PATH(artifact_preset_json, 'one', '$.shareCard')
                       OR JSON_SEARCH(artifact_preset_json, 'one', 'shareCard') IS NOT NULL
                    """);
            System.out.printf("card_scene_template removed artifact value: %d%n", templateRemovedArtifactCount);
            if (templateRemovedArtifactCount > 0) {
                violations.add("removed artifact values remain in card_scene_template: " + templateRemovedArtifactCount);
            }

            long capabilityContractResidueCount = queryForLong(connection, """
                    SELECT COUNT(*)
                    FROM capability_product
                    WHERE JSON_SEARCH(benefit_config_json, 'one', 'shareCard') IS NOT NULL
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"benefits"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"items"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"capabilityMatrix"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"capabilityCode"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"abilityCode"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"benefitStatus"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"enabledStatus"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"activeStatus"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"isEnabled"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"pageScopes"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"artifactScopes"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"artifacts"%'
                       OR CAST(benefit_config_json AS CHAR) LIKE '%"outputs"%'
                    """);
            System.out.printf("capability_product benefit contract residue: %d%n", capabilityContractResidueCount);
            if (capabilityContractResidueCount > 0) {
                violations.add("capability benefit contract residue remains: " + capabilityContractResidueCount);
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Share-card runtime structure check failed: " + String.join("; ", violations));
        }
    }

    private static void backupShareCardRuntime() throws SQLException, IOException {
        Files.createDirectories(BACKUP_DIR);
        try (Connection connection = openConnection()) {
            for (String tableName : SHARE_CARD_RUNTIME_TABLES) {
                if (!tableExists(connection, tableName)) {
                    continue;
                }
                exportQuery(connection,
                        "SELECT * FROM `" + tableName + "`",
                        BACKUP_DIR.resolve(tableName + ".tsv"));
                exportQuery(connection,
                        """
                                SELECT column_name, column_type, is_nullable, column_key, column_default, extra
                                FROM information_schema.columns
                                WHERE table_schema = ?
                                  AND table_name = ?
                                ORDER BY ordinal_position
                                """,
                        BACKUP_DIR.resolve(tableName + ".schema.tsv"),
                        DB_NAME,
                        tableName);
            }
        }
        Files.writeString(BACKUP_DIR.resolve("README.md"),
                "Share-card runtime table data and schema snapshot before V20260425_010/V20260425_011 physical cleanup.\n",
                StandardCharsets.UTF_8);
        System.out.println("Backup written to " + BACKUP_DIR.toAbsolutePath().normalize());
    }

    private static void apply(String migrationFileName) throws SQLException, IOException {
        Path migrationFile = MIGRATION_DIR.resolve(migrationFileName);
        if (!Files.exists(migrationFile)) {
            throw new IllegalArgumentException("Migration file not found: " + migrationFile);
        }

        List<String> statements = splitStatements(Files.readString(migrationFile, StandardCharsets.UTF_8));
        if (statements.isEmpty()) {
            throw new IllegalStateException("No executable SQL statements found in " + migrationFileName);
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int executed = 0;
                for (String sql : statements) {
                    statement.execute(sql);
                    executed++;
                }
                connection.commit();
                System.out.printf("Applied %d statements from %s%n", executed, migrationFileName);
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = ?
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DB_NAME);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND column_name = ?
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DB_NAME);
            statement.setString(2, tableName);
            statement.setString(3, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean constraintExists(Connection connection, String tableName, String constraintName) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.table_constraints
                WHERE constraint_schema = ?
                  AND table_name = ?
                  AND constraint_name = ?
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DB_NAME);
            statement.setString(2, tableName);
            statement.setString(3, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static long queryForLong(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
                return 0L;
            }
        }
    }

    private static void exportQuery(Connection connection, String sql, Path outputPath, Object... args) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                StringBuilder output = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) {
                        output.append('\t');
                    }
                    output.append(escapeCell(metaData.getColumnLabel(i)));
                }
                output.append('\n');
                while (resultSet.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) {
                            output.append('\t');
                        }
                        Object value = resultSet.getObject(i);
                        output.append(escapeCell(value == null ? "" : String.valueOf(value)));
                    }
                    output.append('\n');
                }
                Files.writeString(outputPath, output.toString(), StandardCharsets.UTF_8);
            }
        }
    }

    private static String escapeCell(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static List<String> splitStatements(String sqlContent) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sqlContent.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                if (sql.endsWith(";")) {
                    sql = sql.substring(0, sql.length() - 1);
                }
                if (!sql.isBlank()) {
                    statements.add(sql);
                }
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    private record ColumnCheck(String tableName, String columnName) {
    }
}
