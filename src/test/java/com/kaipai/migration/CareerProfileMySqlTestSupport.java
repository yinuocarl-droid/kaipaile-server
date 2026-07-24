package com.kaipai.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real MySQL 8 fixture for the 00-199 profile migration gates. */
final class CareerProfileMySqlTestSupport implements AutoCloseable {

    private static final String[] MIGRATIONS = {
        "V20260723_001__career_profile_domain_foundation.sql",
        "V20260723_002__actor_media_asset_relations.sql",
        "V20260723_003__share_card_favorite.sql"
    };

    private final MySQLContainer<?> mysql;
    private final DriverManagerDataSource dataSource;
    private final JdbcTemplate jdbc;

    private CareerProfileMySqlTestSupport(String databaseName) throws Exception {
        mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName(databaseName)
                .withUsername("kaipai_test")
                .withPassword("kaipai_test")
                .withCommand("--log-bin-trust-function-creators=1");

        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        jdbc = new JdbcTemplate(dataSource);

        initializeOrStop(
                () -> {
                    mysql.start();
                    dataSource.setUrl(withMultiQueries(mysql.getJdbcUrl()));
                    dataSource.setUsername(mysql.getUsername());
                    dataSource.setPassword(mysql.getPassword());
                    try (Connection connection = dataSource.getConnection()) {
                        createLegacySchema(connection);
                        for (String migration : MIGRATIONS) {
                            executeMigration(connection, migration);
                        }
                    }
                },
                mysql::stop);
    }

    static CareerProfileMySqlTestSupport start(String databaseName) throws Exception {
        return new CareerProfileMySqlTestSupport(databaseName);
    }

    static String readMigrationSql(String migrationName) throws IOException {
        String resourcePath = "/db/migration/" + migrationName;
        try (InputStream input =
                CareerProfileMySqlTestSupport.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("migration classpath resource is missing: " + migrationName);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void initializeOrStop(ThrowingInitializer initializer, Runnable stop)
            throws Exception {
        try {
            initializer.run();
        } catch (Exception | Error error) {
            try {
                stop.run();
            } catch (RuntimeException | Error cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    DataSource dataSource() {
        return dataSource;
    }

    JdbcTemplate jdbc() {
        return jdbc;
    }

    void resetData() {
        jdbc.update("DELETE FROM share_card_favorite");
        jdbc.update("DELETE FROM share_card_asset");
        jdbc.update("DELETE FROM share_card_work");
        jdbc.update("DELETE FROM actor_work_asset");
        jdbc.update("DELETE FROM actor_profile_asset");
        jdbc.update("DELETE FROM actor_media_asset_page");
        jdbc.update("DELETE FROM actor_media_asset");
        jdbc.update("DELETE FROM actor_profile_representative_work");
        jdbc.update("DELETE FROM actor_experience");
        jdbc.update("DELETE FROM actor_profile");
    }

    void insertProfile(long profileId, long userId, String extendedField, long workLibraryVersion) {
        jdbc.update(
                """
                INSERT INTO actor_profile (
                  actor_profile_id, user_id, nick_name, profile_status, extended_field,
                  work_library_version, version, deleted
                ) VALUES (?, ?, '王火火', 3, ?, ?, 0, 0)
                """,
                profileId,
                userId,
                extendedField,
                workLibraryVersion);
    }

    void insertReadyPhotoAndProfileRelation(long userId, long profileId) {
        jdbc.update(
                """
                INSERT INTO actor_media_asset (
                  asset_id, user_id, media_type, category_code, storage_provider,
                  bucket_code, object_key, process_status, source_type, deleted
                ) VALUES (9001, ?, 'photo', 'portrait', 'cos', 'private-test',
                  'profiles/test/9001.jpg', 'ready', 'migration', 0)
                """,
                userId);
        jdbc.update(
                """
                INSERT INTO actor_profile_asset (
                  relation_id, actor_profile_id, asset_id, usage_code, sort_no, deleted
                ) VALUES (9101, ?, 9001, 'public_photo', 1, 0)
                """,
                profileId);
    }

    void insertBaselineWork(
            long experienceId,
            long userId,
            long profileId,
            String projectName,
            String roleName,
            String normalizedProjectName,
            String normalizedRoleName,
            String dedupeKey) {
        jdbc.update(
                """
                INSERT INTO actor_experience (
                  experience_id, user_id, actor_profile_id, drama_name, role_name,
                  normalized_drama_name, normalized_role_name, dedupe_key, source_type,
                  publish_status, work_type_code, role_level_code, shoot_year, shoot_month,
                  platform, sync_sound_status, collaborators_json, achievement_text,
                  role_desc, sort_no, extended_field, rid, version, deleted
                ) VALUES (
                  ?, ?, ?, ?, ?, ?, ?, ?, 'manual', 'aired', 'film_tv',
                  'female_supporting_1', 2020, 6, '基线平台', 'dubbed',
                  JSON_ARRAY('基线搭档'), '基线成绩', '基线描述', 500,
                  '{\"baseline\":true}', 'baseline:manual:001', 4, 0
                )
                """,
                experienceId,
                userId,
                profileId,
                projectName,
                roleName,
                normalizedProjectName,
                normalizedRoleName,
                dedupeKey);
    }

    @Override
    public void close() {
        mysql.stop();
    }

    private static String withMultiQueries(String jdbcUrl) {
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "allowMultiQueries=true";
    }

    private static void createLegacySchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE actor_profile (
                      actor_profile_id BIGINT NOT NULL AUTO_INCREMENT,
                      user_id BIGINT NOT NULL,
                      actor_no VARCHAR(64) DEFAULT NULL,
                      nick_name VARCHAR(128) DEFAULT NULL,
                      real_name VARCHAR(128) DEFAULT NULL,
                      gender INT DEFAULT NULL,
                      birthday DATE DEFAULT NULL,
                      birth_hour VARCHAR(32) DEFAULT NULL,
                      age INT DEFAULT NULL,
                      height INT DEFAULT NULL,
                      weight INT DEFAULT NULL,
                      phone VARCHAR(32) DEFAULT NULL,
                      wechat_no VARCHAR(64) DEFAULT NULL,
                      location_province VARCHAR(64) DEFAULT NULL,
                      location_city VARCHAR(64) DEFAULT NULL,
                      avatar_url VARCHAR(1024) DEFAULT NULL,
                      cover_url VARCHAR(1024) DEFAULT NULL,
                      intro TEXT DEFAULT NULL,
                      skill_tag VARCHAR(1024) DEFAULT NULL,
                      style_tag VARCHAR(1024) DEFAULT NULL,
                      video_url VARCHAR(1024) DEFAULT NULL,
                      photo_urls TEXT DEFAULT NULL,
                      experience_desc TEXT DEFAULT NULL,
                      is_certified TINYINT DEFAULT NULL,
                      is_open_apply TINYINT DEFAULT NULL,
                      profile_status INT DEFAULT NULL,
                      sort_no INT DEFAULT NULL,
                      extended_field TEXT DEFAULT NULL,
                      version INT NOT NULL DEFAULT 0,
                      deleted TINYINT NOT NULL DEFAULT 0,
                      rid VARCHAR(64) DEFAULT NULL,
                      create_user_id BIGINT DEFAULT NULL,
                      create_user_name VARCHAR(64) DEFAULT '',
                      create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      update_user_id BIGINT DEFAULT NULL,
                      update_user_name VARCHAR(64) DEFAULT '',
                      last_update DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (actor_profile_id),
                      KEY idx_actor_profile_user_active (user_id, deleted)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute(
                    """
                    CREATE TABLE actor_experience (
                      experience_id BIGINT NOT NULL AUTO_INCREMENT,
                      user_id BIGINT NOT NULL,
                      actor_profile_id BIGINT DEFAULT NULL,
                      drama_name VARCHAR(255) DEFAULT NULL,
                      role_name VARCHAR(255) DEFAULT NULL,
                      drama_type INT DEFAULT NULL,
                      shoot_year INT DEFAULT NULL,
                      shoot_month INT DEFAULT NULL,
                      platform VARCHAR(128) DEFAULT NULL,
                      role_desc TEXT DEFAULT NULL,
                      sort_no INT DEFAULT NULL,
                      extended_field TEXT DEFAULT NULL,
                      version INT NOT NULL DEFAULT 0,
                      deleted TINYINT NOT NULL DEFAULT 0,
                      rid VARCHAR(64) DEFAULT NULL,
                      create_user_id BIGINT DEFAULT NULL,
                      create_user_name VARCHAR(64) DEFAULT '',
                      create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      update_user_id BIGINT DEFAULT NULL,
                      update_user_name VARCHAR(64) DEFAULT '',
                      last_update DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (experience_id),
                      KEY idx_actor_experience_user_active (user_id, deleted)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    private static void executeMigration(Connection connection, String migrationName)
            throws IOException, SQLException {
        String sql = readMigrationSql(migrationName);
        try (Statement statement = connection.createStatement()) {
            boolean resultSet = statement.execute(sql);
            while (resultSet || statement.getUpdateCount() != -1) {
                if (resultSet) {
                    try (ResultSet ignored = statement.getResultSet()) {
                        // Drain every result from the repository's multi-statement migration.
                    }
                }
                resultSet = statement.getMoreResults();
            }
        }
    }

    @FunctionalInterface
    interface ThrowingInitializer {
        void run() throws Exception;
    }
}
