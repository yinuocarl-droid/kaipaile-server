package com.kaipai.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.CareerProfileMigrationRunner;
import com.kaipai.service.actor.support.ActorWorkDeduplicationSupport;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.AbstractDataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CareerProfileMigrationRunnerTest {

    private static final long PROFILE_ID = 7001L;
    private static final long USER_ID = 7101L;
    private static final String EMPTY_WORKS_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String EMPTY_BASELINE_SHA256 =
            "sha256:a9e7f2b14d3f04dd923c9e94490f807bb833cc831dfaefc10d538e5620440a3e";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CareerProfileMySqlTestSupport database;
    private CareerProfileMigrationRunner runner;

    @BeforeAll
    void startMySql() throws Exception {
        database = CareerProfileMySqlTestSupport.start("career_profile_runner_test");
        runner = new CareerProfileMigrationRunner(database.dataSource(), objectMapper);
    }

    @BeforeEach
    void resetDatabase() {
        database.resetData();
    }

    @AfterAll
    void stopMySql() {
        database.close();
    }

    @Test
    void inspectParsesRealExtendedFieldAndReportsOnlyMalformedUserIds() {
        database.insertProfile(PROFILE_ID, USER_ID, "{\"languages\":[\"粤语\"]}", 0L);
        database.insertProfile(
                PROFILE_ID + 1,
                USER_ID + 1,
                "not-json https://private.example.invalid/raw-profile",
                0L);

        CareerProfileMigrationRunner.InspectionReport report = runner.inspect();

        assertEquals(2L, report.profileCount());
        assertEquals(Set.of(USER_ID + 1), report.malformedExtendedFieldUserIds());
        assertFalse(report.toString().contains("private.example.invalid"));
        assertFalse(report.toString().contains("not-json"));
    }

    @Test
    void dryRunPlansAgainstMySqlWithoutMutatingAssetsRelationsOrWorks() {
        database.insertProfile(PROFILE_ID, USER_ID, "{}", 0L);
        database.insertReadyPhotoAndProfileRelation(USER_ID, PROFILE_ID);
        Counts before = queryCounts();

        CareerProfileMigrationRunner.DryRunReport report = runner.dryRun(USER_ID);

        Counts after = queryCounts();
        assertTrue(report.profileFound());
        assertEquals(29, report.plannedCreates());
        assertEquals(0, report.plannedSkips());
        assertEquals(report.beforeCounts(), report.afterCounts());
        assertEquals(before, after);
        assertEquals(
                new Counts(1L, 1L, 0L),
                after,
                "dry-run must leave real actor_media_asset, relation and active work rows unchanged");
    }

    @Test
    void baselineResourceContainsOnlyRecoverableNonSensitiveFields() throws Exception {
        CareerProfileMigrationRunner.Baseline baseline = runner.loadBaseline();

        assertEquals(1, baseline.schemaVersion());
        assertEquals(1L, baseline.profileCount());
        assertEquals(0L, baseline.activeWorkCount());
        assertEquals(0L, baseline.workLibraryVersion());
        assertEquals(EMPTY_WORKS_SHA256, baseline.activeWorksHash());
        assertEquals(EMPTY_BASELINE_SHA256, baseline.baselineHash());

        try (InputStream input = getClass().getResourceAsStream(
                "/profile-migration/wang-huohuo-baseline.json")) {
            JsonNode root = objectMapper.readTree(input);
            Set<String> fieldNames = new java.util.LinkedHashSet<>();
            root.fieldNames().forEachRemaining(fieldNames::add);
            assertEquals(
                    Set.of(
                            "schemaVersion",
                            "profileCount",
                            "activeWorkCount",
                            "workLibraryVersion",
                            "activeWorksHash",
                            "baselineHash"),
                    fieldNames);
            String serialized = root.toString().toLowerCase(java.util.Locale.ROOT);
            assertFalse(serialized.contains("http"));
            assertFalse(serialized.contains("phone"));
            assertFalse(serialized.contains("token"));
            assertFalse(serialized.contains("credential"));
        }
    }

    @Test
    void goldenResourceDeclaresTheFixedCategoryContract() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/profile-migration/wang-huohuo-works-golden.json")) {
            JsonNode root = objectMapper.readTree(input);
            assertEquals(29, root.path("works").size());
            assertEquals(14, root.path("categoryCounts").path("aired").asInt());
            assertEquals(6, root.path("categoryCounts").path("upcoming").asInt());
            assertEquals(3, root.path("categoryCounts").path("stage").asInt());
            assertEquals(6, root.path("categoryCounts").path("horizontal").asInt());
            assertEquals(4, root.path("categoryCounts").size());
        }
    }

    @Test
    void snapshotUsesOneRepeatableReadViewAcrossProfileAndWorks() {
        database.insertProfile(PROFILE_ID, USER_ID, "{}", 0L);
        AtomicBoolean writerCommitted = new AtomicBoolean();
        DataSource interleavingDataSource = new InterleavingDataSource(
                database.dataSource(),
                () -> {
                    insertInterleavingWorkAndAdvanceVersion();
                    writerCommitted.set(true);
                });
        CareerProfileMigrationRunner interleavedRunner =
                new CareerProfileMigrationRunner(interleavingDataSource, objectMapper);

        CareerProfileMigrationRunner.BaselineSnapshot snapshot =
                interleavedRunner.snapshotBaseline(USER_ID);

        assertTrue(writerCommitted.get(), "the second connection must commit during snapshot");
        boolean entirelyBefore = snapshot.baseline().workLibraryVersion() == 0L
                && snapshot.baseline().activeWorkCount() == 0L
                && snapshot.activeWorkIds().isEmpty();
        boolean entirelyAfter = snapshot.baseline().workLibraryVersion() == 1L
                && snapshot.baseline().activeWorkCount() == 1L
                && snapshot.activeWorkIds().equals(List.of(PROFILE_ID + 100L));
        assertTrue(
                entirelyBefore || entirelyAfter,
                () -> "snapshot mixed profile/version and work states: " + snapshot);
        assertEquals(1L, database.jdbc().queryForObject(
                "SELECT work_library_version FROM actor_profile WHERE actor_profile_id = ?",
                Long.class,
                PROFILE_ID));
        assertEquals(1L, database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM actor_experience WHERE user_id = ? AND deleted = 0",
                Long.class,
                USER_ID));
    }

    @Test
    void migrationSqlLoadsFromClasspathResource() throws Exception {
        String sql = CareerProfileMySqlTestSupport.readMigrationSql(
                "V20260723_001__career_profile_domain_foundation.sql");

        assertTrue(sql.contains("actor_profile_representative_work"));
        assertTrue(sql.contains("work_library_version"));
    }

    @Test
    void initializationFailureStopsTheStartedContainerResource() {
        AtomicBoolean stopped = new AtomicBoolean();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> CareerProfileMySqlTestSupport.initializeOrStop(
                        () -> {
                            throw new IllegalStateException("forced schema initialization failure");
                        },
                        () -> stopped.set(true)));

        assertEquals("forced schema initialization failure", failure.getMessage());
        assertTrue(stopped.get());
    }

    private void insertInterleavingWorkAndAdvanceVersion() {
        String projectName = "快照并发作品";
        String roleName = "并发角色";
        String normalizedProject = ActorWorkDeduplicationSupport.normalizeName(projectName);
        String normalizedRole = ActorWorkDeduplicationSupport.normalizeName(roleName);
        String dedupeKey = ActorWorkDeduplicationSupport.dedupeKey(projectName, roleName);
        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                            """
                            INSERT INTO actor_experience (
                              experience_id, user_id, actor_profile_id, drama_name, role_name,
                              normalized_drama_name, normalized_role_name, dedupe_key,
                              source_type, publish_status, work_type_code, rid, version, deleted
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'manual', 'aired',
                              'short_drama', 'snapshot:writer:001', 0, 0)
                            """);
                    PreparedStatement update = connection.prepareStatement(
                            """
                            UPDATE actor_profile
                            SET work_library_version = work_library_version + 1
                            WHERE actor_profile_id = ? AND deleted = 0
                            """)) {
                insert.setLong(1, PROFILE_ID + 100L);
                insert.setLong(2, USER_ID);
                insert.setLong(3, PROFILE_ID);
                insert.setString(4, projectName);
                insert.setString(5, roleName);
                insert.setString(6, normalizedProject);
                insert.setString(7, normalizedRole);
                insert.setString(8, dedupeKey);
                assertEquals(1, insert.executeUpdate());
                update.setLong(1, PROFILE_ID);
                assertEquals(1, update.executeUpdate());
                connection.commit();
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        } catch (Exception error) {
            throw new IllegalStateException("interleaving writer failed", error);
        }
    }

    private Counts queryCounts() {
        Long assets = database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM actor_media_asset WHERE deleted = 0", Long.class);
        Long relations = database.jdbc().queryForObject(
                """
                SELECT
                  (SELECT COUNT(*) FROM actor_profile_asset WHERE deleted = 0)
                  + (SELECT COUNT(*) FROM actor_work_asset WHERE deleted = 0)
                  + (SELECT COUNT(*) FROM share_card_asset WHERE deleted = 0)
                """,
                Long.class);
        Long works = database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM actor_experience WHERE deleted = 0", Long.class);
        return new Counts(assets, relations, works);
    }

    private record Counts(long mediaAssets, long relations, long activeWorks) {
    }

    private static final class InterleavingDataSource extends AbstractDataSource {
        private final DataSource delegate;
        private final Runnable beforeWorksQuery;
        private final AtomicBoolean triggered = new AtomicBoolean();

        private InterleavingDataSource(DataSource delegate, Runnable beforeWorksQuery) {
            this.delegate = delegate;
            this.beforeWorksQuery = beforeWorksQuery;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return intercept(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return intercept(delegate.getConnection(username, password));
        }

        private Connection intercept(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> {
                        if ("prepareStatement".equals(method.getName())
                                && arguments != null
                                && arguments.length > 0
                                && arguments[0] instanceof String sql
                                && sql.contains("FROM actor_experience")
                                && triggered.compareAndSet(false, true)) {
                            beforeWorksQuery.run();
                        }
                        try {
                            return method.invoke(connection, arguments);
                        } catch (InvocationTargetException error) {
                            throw error.getTargetException();
                        }
                    });
        }
    }
}
