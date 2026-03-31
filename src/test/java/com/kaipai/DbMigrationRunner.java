package com.kaipai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    private static final List<String> BASELINE_TABLES = Arrays.asList(
            "identity_verification",
            "invite_code",
            "referral_record",
            "referral_policy",
            "membership_product",
            "membership_account",
            "membership_change_log",
            "payment_order",
            "payment_transaction",
            "refund_order",
            "refund_operate_log",
            "card_scene_template",
            "actor_card_config",
            "actor_share_preference",
            "fortune_report",
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

        throw new IllegalArgumentException("Unsupported mode: " + args[0]);
    }

    private static void inspect() throws SQLException {
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
        }
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
