package com.kaipai.migration;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

final class MigrationTestDatabase {

    private static final DockerImageName MYSQL_8 = DockerImageName.parse("mysql:8.0.36");
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");

    private MigrationTestDatabase() {
    }

    static Connection apply(String... migrationNames) throws IOException, SQLException {
        MySQLContainer<?> container = new MySQLContainer<>(MYSQL_8)
                .withDatabaseName("kaipai_schema_test")
                .withUsername("kaipai_test")
                .withPassword("kaipai_test");

        Connection connection = null;
        try {
            container.start();
            connection = DriverManager.getConnection(
                    withMultiQueries(container.getJdbcUrl()),
                    container.getUsername(),
                    container.getPassword());
            createPreMigrationTables(connection);
            for (String migrationName : migrationNames) {
                executeMigration(connection, migrationName);
            }
            return stopContainerOnClose(connection, container);
        } catch (IOException | SQLException | RuntimeException error) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            try {
                container.stop();
            } catch (Exception cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    private static String withMultiQueries(String jdbcUrl) {
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "allowMultiQueries=true";
    }

    private static void createPreMigrationTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE actor_profile (
                      profile_id BIGINT NOT NULL AUTO_INCREMENT,
                      PRIMARY KEY (profile_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("""
                    CREATE TABLE actor_experience (
                      experience_id BIGINT NOT NULL AUTO_INCREMENT,
                      user_id BIGINT NOT NULL,
                      deleted TINYINT NOT NULL DEFAULT 0,
                      PRIMARY KEY (experience_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    private static void executeMigration(Connection connection, String migrationName) throws IOException, SQLException {
        Path migration = MIGRATION_DIR.resolve(migrationName);
        if (!Files.isRegularFile(migration)) {
            throw new IOException("migration is missing: " + migration);
        }

        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        try (Statement statement = connection.createStatement()) {
            drainResults(statement, statement.execute(sql));
        }
    }

    private static void drainResults(Statement statement, boolean hasResultSet) throws SQLException {
        boolean currentIsResultSet = hasResultSet;
        while (currentIsResultSet || statement.getUpdateCount() != -1) {
            if (currentIsResultSet) {
                try (ResultSet ignored = statement.getResultSet()) {
                    // Migration statements do not consume row data, but every result must be closed.
                }
            }
            currentIsResultSet = statement.getMoreResults();
        }
    }

    private static Connection stopContainerOnClose(Connection connection, MySQLContainer<?> container) {
        AtomicBoolean closed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if ("close".equals(method.getName())) {
                        if (closed.compareAndSet(false, true)) {
                            try {
                                connection.close();
                            } catch (SQLException | RuntimeException error) {
                                try {
                                    container.stop();
                                } catch (RuntimeException cleanupError) {
                                    error.addSuppressed(cleanupError);
                                }
                                throw error;
                            }
                            container.stop();
                        }
                        return null;
                    }
                    try {
                        return method.invoke(connection, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getTargetException();
                    }
                });
    }
}
