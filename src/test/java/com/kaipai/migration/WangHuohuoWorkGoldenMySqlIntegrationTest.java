package com.kaipai.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.CareerProfileMigrationRunner;
import com.kaipai.common.handler.MetaObjectHandlerConfig;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.actor.ActorProfileRepresentativeWorkMapper;
import com.kaipai.mapper.card.ShareCardWorkMapper;
import com.kaipai.model.actor.dto.ActorWorkQueryDTO;
import com.kaipai.service.actor.ActorWorkService;
import com.kaipai.service.actor.impl.ActorWorkServiceImpl;
import com.kaipai.service.actor.support.ActorWorkDeduplicationSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.stream.Stream;

@SpringJUnitConfig(WangHuohuoWorkGoldenMySqlIntegrationTest.TestConfiguration.class)
class WangHuohuoWorkGoldenMySqlIntegrationTest {

    private static final long PROFILE_ID = 88001L;
    private static final long USER_ID = 88101L;
    private static final String PRIMARY_BATCH = "wang-huohuo-main";
    private static final String FRESH_BATCH = "wang-huohuo-fresh";
    private static final long BASELINE_WORK_ID = 87901L;
    private static final long SOFT_DELETED_BATCH_WORK_ID = 87902L;
    private static final long SECOND_BASELINE_WORK_ID = 87903L;
    private static final long FORGED_BATCH_WORK_ID = 87904L;
    private static final long NONZERO_BASELINE_VERSION = 17L;
    private static final CareerProfileMySqlTestSupport DATABASE = startDatabase();
    private static final List<ExpectedWork> EXPECTED_WORKS = expectedWorks();

    private final ActorWorkService actorWorkService;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private CareerProfileMigrationRunner runner;

    @Autowired
    WangHuohuoWorkGoldenMySqlIntegrationTest(
            ActorWorkService actorWorkService,
            DataSource dataSource,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.actorWorkService = actorWorkService;
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void seedSyntheticWangHuohuoProfile() {
        DATABASE.resetData();
        DATABASE.insertProfile(PROFILE_ID, USER_ID, "{}", 0L);
        runner = new CareerProfileMigrationRunner(dataSource, objectMapper);
    }

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @Test
    void goldenApplyIsRealIdempotentPageableAndExactlyRestorable() throws Exception {
        CareerProfileMigrationRunner.BaselineSnapshot baseline = runner.snapshotBaseline(USER_ID);
        assertEquals(runner.loadBaseline(), baseline.baseline());
        assertTrue(baseline.activeWorkIds().isEmpty());

        CareerProfileMigrationRunner.ApplyReport first =
                runner.applyGolden(USER_ID, PRIMARY_BATCH);
        assertEquals(29, first.createdCount());
        assertEquals(0, first.skippedCount());
        assertEquals(29, new LinkedHashSet<>(first.createdExperienceIds()).size());

        CareerProfileMigrationRunner.ApplyReport sameBatch =
                runner.applyGolden(USER_ID, PRIMARY_BATCH);
        assertEquals(0, sameBatch.createdCount());
        assertEquals(29, sameBatch.skippedCount());

        CareerProfileMigrationRunner.ApplyReport freshBatch =
                runner.applyGolden(USER_ID, FRESH_BATCH);
        assertEquals(0, freshBatch.createdCount());
        assertEquals(29, freshBatch.skippedCount());
        assertEquals(29L, countActiveWorks());

        assertDatabaseShapeAndEveryExpectedWork();
        assertRealActorWorkServicePagination();

        CareerProfileMigrationRunner.VerificationReport verification = runner.verify(USER_ID);
        assertTrue(verification.passed(), verification::toString);
        assertEquals(29L, verification.activeWorkCount());
        assertEquals(29L, verification.distinctDedupeKeyCount());
        assertEquals(29L, verification.migrationSourceCount());
        assertEquals(
                Map.of("aired", 14L, "upcoming", 6L, "stage", 3L, "horizontal", 6L),
                verification.categoryCounts());
        assertTrue(verification.mismatchedFixtureIds().isEmpty());
        insertSoftDeletedBatchWork();
        assertEquals(1L, countPhysicalWork(SOFT_DELETED_BATCH_WORK_ID));

        CareerProfileMigrationRunner.RestoreReport restored =
                runner.restoreFixture(
                        USER_ID,
                        PRIMARY_BATCH,
                        baseline,
                        baseline.baseline().baselineHash());
        assertEquals(29, restored.removedCount());
        assertEquals(0L, restored.restoredWorkLibraryVersion());

        CareerProfileMigrationRunner.RestoreVerificationReport restoreVerification =
                runner.verifyRestore(USER_ID, baseline, baseline.baseline().baselineHash());
        assertTrue(restoreVerification.passed(), restoreVerification::toString);
        assertEquals(0L, countActiveWorks());
        assertEquals(0L, currentWorkLibraryVersion());
        assertEquals(
                1L,
                countPhysicalWork(SOFT_DELETED_BATCH_WORK_ID),
                "restore must not physically delete an existing soft-deleted row sharing the batch prefix");
    }

    @Test
    void restoreRejectsWrongOrMalformedExpectedHashWithoutAnyDatabaseMutation() {
        CareerProfileMigrationRunner.BaselineSnapshot baseline = runner.snapshotBaseline(USER_ID);
        runner.applyGolden(USER_ID, PRIMARY_BATCH);
        List<Long> activeIdsBefore = queryActiveWorkIds();
        Map<String, Object> profileBefore = queryFullProfileRow();

        assertThrows(
                IllegalStateException.class,
                () -> runner.restoreFixture(
                        USER_ID,
                        PRIMARY_BATCH,
                        baseline,
                        "sha256:" + "0".repeat(64)));
        assertEquals(activeIdsBefore, queryActiveWorkIds());
        assertEquals(profileBefore, queryFullProfileRow());
        assertEquals(29L, countActiveWorks());
        assertEquals(29L, currentWorkLibraryVersion());

        assertThrows(
                IllegalArgumentException.class,
                () -> runner.restoreFixture(
                        USER_ID,
                        PRIMARY_BATCH,
                        baseline,
                        "SHA256:not-a-lowercase-hash"));
        assertEquals(activeIdsBefore, queryActiveWorkIds());
        assertEquals(profileBefore, queryFullProfileRow());
        assertEquals(29L, countActiveWorks());
        assertEquals(29L, currentWorkLibraryVersion());

        CareerProfileMigrationRunner.Baseline tamperedBaseline =
                new CareerProfileMigrationRunner.Baseline(
                        baseline.baseline().schemaVersion(),
                        baseline.baseline().profileCount(),
                        baseline.baseline().activeWorkCount(),
                        baseline.baseline().workLibraryVersion(),
                        baseline.baseline().activeWorksHash(),
                        "sha256:" + "0".repeat(64));
        CareerProfileMigrationRunner.BaselineSnapshot tamperedSnapshot =
                new CareerProfileMigrationRunner.BaselineSnapshot(
                        tamperedBaseline, baseline.activeWorkIds());
        assertThrows(
                IllegalStateException.class,
                () -> runner.restoreFixture(
                        USER_ID,
                        PRIMARY_BATCH,
                        tamperedSnapshot,
                        tamperedBaseline.baselineHash()));
        assertThrows(
                IllegalStateException.class,
                () -> runner.verifyRestore(
                        USER_ID,
                        baseline,
                        "sha256:" + "0".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () -> runner.verifyRestore(USER_ID, baseline, "not-a-hash"));
        assertEquals(activeIdsBefore, queryActiveWorkIds());
        assertEquals(profileBefore, queryFullProfileRow());
        assertEquals(29L, countActiveWorks());
        assertEquals(29L, currentWorkLibraryVersion());
    }

    @Test
    void restorePreservesAnExistingNonEmptyBaselineWorkExactly() {
        DATABASE.resetData();
        DATABASE.insertProfile(PROFILE_ID, USER_ID, "{}", NONZERO_BASELINE_VERSION);
        String projectName = "基线保留作品";
        String roleName = "林澜";
        String normalizedProject = ActorWorkDeduplicationSupport.normalizeName(projectName);
        String normalizedRole = ActorWorkDeduplicationSupport.normalizeName(roleName);
        String dedupeKey = ActorWorkDeduplicationSupport.dedupeKey(projectName, roleName);
        DATABASE.insertBaselineWork(
                BASELINE_WORK_ID,
                USER_ID,
                PROFILE_ID,
                projectName,
                roleName,
                normalizedProject,
                normalizedRole,
                dedupeKey);
        runner = new CareerProfileMigrationRunner(dataSource, objectMapper);

        BaselineWork expectedBaselineWork = new BaselineWork(
                BASELINE_WORK_ID,
                USER_ID,
                PROFILE_ID,
                projectName,
                roleName,
                normalizedProject,
                normalizedRole,
                dedupeKey,
                "manual",
                "aired",
                "film_tv",
                "female_supporting_1",
                2020,
                6,
                "基线平台",
                "dubbed",
                List.of("基线搭档"),
                "基线成绩",
                "基线描述",
                500,
                "{\"baseline\":true}",
                "baseline:manual:001",
                4,
                0);
        assertEquals(expectedBaselineWork, queryBaselineWork());
        Map<String, Object> completeRowBefore = queryFullBaselineWorkRow();

        CareerProfileMigrationRunner.BaselineSnapshot baseline = runner.snapshotBaseline(USER_ID);
        assertEquals(1L, baseline.baseline().activeWorkCount());
        assertEquals(NONZERO_BASELINE_VERSION, baseline.baseline().workLibraryVersion());
        assertEquals(List.of(BASELINE_WORK_ID), baseline.activeWorkIds());

        CareerProfileMigrationRunner.ApplyReport apply =
                runner.applyGolden(USER_ID, PRIMARY_BATCH);
        assertEquals(29, apply.createdCount());
        assertEquals(30L, countActiveWorks());
        assertEquals(NONZERO_BASELINE_VERSION + 29L, currentWorkLibraryVersion());

        CareerProfileMigrationRunner.RestoreReport restored = runner.restoreFixture(
                USER_ID,
                PRIMARY_BATCH,
                baseline,
                baseline.baseline().baselineHash());
        assertEquals(29, restored.removedCount());
        assertEquals(NONZERO_BASELINE_VERSION, restored.restoredWorkLibraryVersion());
        assertEquals(1L, countActiveWorks());
        assertEquals(NONZERO_BASELINE_VERSION, currentWorkLibraryVersion());
        assertEquals(expectedBaselineWork, queryBaselineWork());
        assertEquals(completeRowBefore, queryFullBaselineWorkRow());

        CareerProfileMigrationRunner.RestoreVerificationReport verification =
                runner.verifyRestore(USER_ID, baseline, baseline.baseline().baselineHash());
        assertTrue(verification.passed(), verification::toString);
    }

    @Test
    void restoreRejectsWorkLibraryVersionDriftWithoutChangingAnyRows() {
        CareerProfileMigrationRunner.BaselineSnapshot baseline = runner.snapshotBaseline(USER_ID);
        runner.applyGolden(USER_ID, PRIMARY_BATCH);
        assertEquals(
                1,
                jdbc.update(
                        """
                        UPDATE actor_profile
                        SET work_library_version = work_library_version + 1
                        WHERE actor_profile_id = ? AND deleted = 0
                        """,
                        PROFILE_ID));
        List<Map<String, Object>> worksBefore = queryAllPhysicalWorkRows();
        Map<String, Object> profileBefore = queryFullProfileRow();

        assertThrows(
                IllegalStateException.class,
                () -> runner.restoreFixture(
                        USER_ID,
                        PRIMARY_BATCH,
                        baseline,
                        baseline.baseline().baselineHash()));

        assertEquals(worksBefore, queryAllPhysicalWorkRows());
        assertEquals(profileBefore, queryFullProfileRow());
        assertEquals(29L, countActiveWorks());
        assertEquals(30L, currentWorkLibraryVersion());
    }

    @Test
    void restoreRejectsAnActiveForgedBatchMarkerBeforeAnyWrite() {
        CareerProfileMigrationRunner.BaselineSnapshot baseline = runner.snapshotBaseline(USER_ID);
        runner.applyGolden(USER_ID, PRIMARY_BATCH);
        insertForgedBatchMarkerAndAdvanceVersion();
        List<Map<String, Object>> worksBefore = queryAllPhysicalWorkRows();
        Map<String, Object> profileBefore = queryFullProfileRow();

        assertThrows(
                IllegalStateException.class,
                () -> runner.restoreFixture(
                        USER_ID,
                        PRIMARY_BATCH,
                        baseline,
                        baseline.baseline().baselineHash()));

        assertEquals(worksBefore, queryAllPhysicalWorkRows());
        assertEquals(profileBefore, queryFullProfileRow());
        assertEquals(30L, countActiveWorks());
        assertEquals(1L, countPhysicalWork(FORGED_BATCH_WORK_ID));
        assertEquals(30L, currentWorkLibraryVersion());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSnapshotActiveIds")
    void restoreRejectsInvalidSnapshotIdsBeforeMutation(
            String caseName, List<Long> invalidActiveIds) {
        seedTwoBaselineWorks();
        CareerProfileMigrationRunner.BaselineSnapshot valid = runner.snapshotBaseline(USER_ID);
        assertEquals(2L, valid.baseline().activeWorkCount());
        CareerProfileMigrationRunner.BaselineSnapshot invalid =
                new CareerProfileMigrationRunner.BaselineSnapshot(
                        valid.baseline(), invalidActiveIds);
        List<Map<String, Object>> worksBefore = queryAllPhysicalWorkRows();
        Map<String, Object> profileBefore = queryFullProfileRow();

        assertThrows(
                IllegalArgumentException.class,
                () -> runner.restoreFixture(
                        USER_ID,
                        PRIMARY_BATCH,
                        invalid,
                        valid.baseline().baselineHash()),
                caseName);

        assertEquals(worksBefore, queryAllPhysicalWorkRows());
        assertEquals(profileBefore, queryFullProfileRow());
    }

    private static Stream<Arguments> invalidSnapshotActiveIds() {
        return Stream.of(
                Arguments.of("count mismatch", List.of(BASELINE_WORK_ID)),
                Arguments.of("non-positive id", List.of(0L, SECOND_BASELINE_WORK_ID)),
                Arguments.of(
                        "not ascending", List.of(SECOND_BASELINE_WORK_ID, BASELINE_WORK_ID)),
                Arguments.of("duplicate id", List.of(BASELINE_WORK_ID, BASELINE_WORK_ID)));
    }

    private void assertDatabaseShapeAndEveryExpectedWork() {
        assertEquals(29L, countActiveWorks());
        assertEquals(
                29L,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(DISTINCT experience_id)
                        FROM actor_experience
                        WHERE user_id = ? AND deleted = 0
                        """,
                        Long.class,
                        USER_ID));
        assertEquals(
                29L,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(NULLIF(dedupe_key, ''))
                        FROM actor_experience
                        WHERE user_id = ? AND deleted = 0
                        """,
                        Long.class,
                        USER_ID));
        assertEquals(
                29L,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(DISTINCT dedupe_key)
                        FROM actor_experience
                        WHERE user_id = ? AND deleted = 0
                        """,
                        Long.class,
                        USER_ID));
        assertEquals(
                29L,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM actor_experience
                        WHERE user_id = ? AND deleted = 0 AND source_type = 'migration'
                        """,
                        Long.class,
                        USER_ID));

        Map<String, Long> categoryCounts = jdbc.query(
                        """
                        SELECT publish_status, COUNT(*) AS work_count
                        FROM actor_experience
                        WHERE user_id = ? AND deleted = 0
                        GROUP BY publish_status
                        """,
                        (resultSet, rowNum) -> Map.entry(
                                resultSet.getString("publish_status"),
                                resultSet.getLong("work_count")),
                        USER_ID)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(
                Map.of("aired", 14L, "upcoming", 6L, "stage", 3L, "horizontal", 6L),
                categoryCounts);

        Map<String, ActualWork> actualByProject = jdbc.query(
                        """
                        SELECT experience_id, drama_name, publish_status, work_type_code,
                               role_level_code, role_name, platform, sync_sound_status,
                               collaborators_json, achievement_text, dedupe_key, source_type
                        FROM actor_experience
                        WHERE user_id = ? AND deleted = 0
                        """,
                        (resultSet, rowNum) -> new ActualWork(
                                resultSet.getLong("experience_id"),
                                resultSet.getString("drama_name"),
                                resultSet.getString("publish_status"),
                                resultSet.getString("work_type_code"),
                                resultSet.getString("role_level_code"),
                                resultSet.getString("role_name"),
                                resultSet.getString("platform"),
                                resultSet.getString("sync_sound_status"),
                                readCollaborators(resultSet.getString("collaborators_json")),
                                resultSet.getString("achievement_text"),
                                resultSet.getString("dedupe_key"),
                                resultSet.getString("source_type")),
                        USER_ID)
                .stream()
                .collect(Collectors.toMap(ActualWork::projectName, Function.identity()));

        assertEquals(29, actualByProject.size());
        for (ExpectedWork expected : EXPECTED_WORKS) {
            ActualWork actual = actualByProject.get(expected.projectName());
            assertTrue(actual != null, () -> "missing DB row for " + expected.fixtureId());
            assertEquals(expected.projectName(), actual.projectName(), expected.fixtureId());
            assertEquals(expected.publishStatus(), actual.publishStatus(), expected.fixtureId());
            assertEquals(expected.workTypeCode(), actual.workTypeCode(), expected.fixtureId());
            assertEquals(expected.roleLevelCode(), actual.roleLevelCode(), expected.fixtureId());
            assertEquals(expected.roleName(), actual.roleName(), expected.fixtureId());
            assertEquals(expected.platform(), actual.platform(), expected.fixtureId());
            assertEquals(expected.syncSoundStatus(), actual.syncSoundStatus(), expected.fixtureId());
            assertEquals(expected.collaborators(), actual.collaborators(), expected.fixtureId());
            assertEquals(expected.achievementText(), actual.achievementText(), expected.fixtureId());
            assertTrue(actual.dedupeKey() != null && !actual.dedupeKey().isBlank(), expected.fixtureId());
            assertEquals("migration", actual.sourceType(), expected.fixtureId());
        }
    }

    private void assertRealActorWorkServicePagination() {
        List<Long> experienceIds = new ArrayList<>();
        List<Integer> pageSizes = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            ActorWorkQueryDTO query = new ActorWorkQueryDTO();
            query.setPage(page);
            query.setSize(10);
            var result = actorWorkService.listWorks(USER_ID, query);
            assertEquals(29L, result.getTotal());
            pageSizes.add(result.getList().size());
            result.getList().forEach(work -> experienceIds.add(work.getExperienceId()));
        }

        assertEquals(List.of(10, 10, 9), pageSizes);
        assertEquals(29, experienceIds.size());
        assertEquals(29, new LinkedHashSet<>(experienceIds).size());
    }

    private List<String> readCollaborators(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception error) {
            throw new AssertionError("collaborators_json must be a JSON string array", error);
        }
    }

    private long countActiveWorks() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_experience WHERE user_id = ? AND deleted = 0",
                Long.class,
                USER_ID);
    }

    private long currentWorkLibraryVersion() {
        return jdbc.queryForObject(
                """
                SELECT work_library_version
                FROM actor_profile
                WHERE user_id = ? AND deleted = 0
                """,
                Long.class,
                USER_ID);
    }

    private List<Map<String, Object>> queryAllPhysicalWorkRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.queryForList(
                        """
                        SELECT *
                        FROM actor_experience
                        WHERE user_id = ?
                        ORDER BY experience_id
                        """,
                        USER_ID)
                .forEach(row -> rows.add(new LinkedHashMap<>(row)));
        return List.copyOf(rows);
    }

    private void insertForgedBatchMarkerAndAdvanceVersion() {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (java.sql.PreparedStatement insert = connection.prepareStatement(
                            """
                            INSERT INTO actor_experience (
                              experience_id, user_id, actor_profile_id, drama_name, role_name,
                              normalized_drama_name, normalized_role_name, dedupe_key, source_type,
                              publish_status, work_type_code, rid, version, deleted
                            ) VALUES (
                              ?, ?, ?, '伪造批次作品', '伪造角色', '伪造批次作品', '伪造角色',
                              'forged-batch-marker-dedupe', 'migration', 'aired', 'short_drama',
                              'cp:wang-huohuo-main:NOT-A-FIXTURE', 0, 0
                            )
                            """);
                    java.sql.PreparedStatement update = connection.prepareStatement(
                            """
                            UPDATE actor_profile
                            SET work_library_version = work_library_version + 1
                            WHERE actor_profile_id = ? AND deleted = 0
                            """)) {
                insert.setLong(1, FORGED_BATCH_WORK_ID);
                insert.setLong(2, USER_ID);
                insert.setLong(3, PROFILE_ID);
                assertEquals(1, insert.executeUpdate());
                update.setLong(1, PROFILE_ID);
                assertEquals(1, update.executeUpdate());
                connection.commit();
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        } catch (Exception error) {
            throw new IllegalStateException("failed to seed forged batch marker", error);
        }
    }

    private void seedTwoBaselineWorks() {
        DATABASE.resetData();
        DATABASE.insertProfile(PROFILE_ID, USER_ID, "{}", 2L);
        insertBaselineWork(
                BASELINE_WORK_ID, "非法快照基线一", "角色一");
        insertBaselineWork(
                SECOND_BASELINE_WORK_ID, "非法快照基线二", "角色二");
        runner = new CareerProfileMigrationRunner(dataSource, objectMapper);
    }

    private void insertBaselineWork(long experienceId, String projectName, String roleName) {
        DATABASE.insertBaselineWork(
                experienceId,
                USER_ID,
                PROFILE_ID,
                projectName,
                roleName,
                ActorWorkDeduplicationSupport.normalizeName(projectName),
                ActorWorkDeduplicationSupport.normalizeName(roleName),
                ActorWorkDeduplicationSupport.dedupeKey(projectName, roleName));
    }

    private void insertSoftDeletedBatchWork() {
        jdbc.update(
                """
                INSERT INTO actor_experience (
                  experience_id, user_id, actor_profile_id, drama_name, role_name,
                  normalized_drama_name, normalized_role_name, dedupe_key, source_type,
                  publish_status, work_type_code, rid, version, deleted
                ) VALUES (
                  ?, ?, ?, '软删批次历史行', '历史角色', '软删批次历史行', '历史角色',
                  'soft-deleted-batch-dedupe', 'migration', 'aired', 'short_drama',
                  'cp:wang-huohuo-main:historical-soft-delete', 2, 1
                )
                """,
                SOFT_DELETED_BATCH_WORK_ID,
                USER_ID,
                PROFILE_ID);
    }

    private long countPhysicalWork(long experienceId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_experience WHERE experience_id = ?",
                Long.class,
                experienceId);
    }

    private List<Long> queryActiveWorkIds() {
        return jdbc.queryForList(
                """
                SELECT experience_id
                FROM actor_experience
                WHERE user_id = ? AND deleted = 0
                ORDER BY experience_id
                """,
                Long.class,
                USER_ID);
    }

    private Map<String, Object> queryFullProfileRow() {
        return new LinkedHashMap<>(jdbc.queryForMap(
                "SELECT * FROM actor_profile WHERE actor_profile_id = ?",
                PROFILE_ID));
    }

    private Map<String, Object> queryFullBaselineWorkRow() {
        return new LinkedHashMap<>(jdbc.queryForMap(
                "SELECT * FROM actor_experience WHERE experience_id = ?",
                BASELINE_WORK_ID));
    }

    private BaselineWork queryBaselineWork() {
        return jdbc.queryForObject(
                """
                SELECT experience_id, user_id, actor_profile_id, drama_name, role_name,
                       normalized_drama_name, normalized_role_name, dedupe_key, source_type,
                       publish_status, work_type_code, role_level_code, shoot_year, shoot_month,
                       platform, sync_sound_status, collaborators_json, achievement_text,
                       role_desc, sort_no, extended_field, rid, version, deleted
                FROM actor_experience
                WHERE experience_id = ?
                """,
                (resultSet, rowNum) -> new BaselineWork(
                        resultSet.getLong("experience_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getLong("actor_profile_id"),
                        resultSet.getString("drama_name"),
                        resultSet.getString("role_name"),
                        resultSet.getString("normalized_drama_name"),
                        resultSet.getString("normalized_role_name"),
                        resultSet.getString("dedupe_key"),
                        resultSet.getString("source_type"),
                        resultSet.getString("publish_status"),
                        resultSet.getString("work_type_code"),
                        resultSet.getString("role_level_code"),
                        resultSet.getInt("shoot_year"),
                        resultSet.getInt("shoot_month"),
                        resultSet.getString("platform"),
                        resultSet.getString("sync_sound_status"),
                        readCollaborators(resultSet.getString("collaborators_json")),
                        resultSet.getString("achievement_text"),
                        resultSet.getString("role_desc"),
                        resultSet.getInt("sort_no"),
                        resultSet.getString("extended_field"),
                        resultSet.getString("rid"),
                        resultSet.getInt("version"),
                        resultSet.getInt("deleted")),
                BASELINE_WORK_ID);
    }

    private static CareerProfileMySqlTestSupport startDatabase() {
        try {
            return CareerProfileMySqlTestSupport.start("wang_huohuo_golden_test");
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static List<ExpectedWork> expectedWorks() {
        List<ExpectedWork> works = new ArrayList<>();
        works.add(work("WHH-001", "绝不回头，白爷宠她成瘾", "aired", "short_drama", "female_supporting_2", "程雪", null, "sync", List.of(), "爱奇艺飙升榜No1 虐恋榜No2"));
        works.add(work("WHH-002", "给儿媳买五金被侮辱，我叫停婚事", "aired", "short_drama", "female_supporting_2", "陈晨", null, "sync", List.of(), "剧查查热力榜No.3 抖音短剧榜No.1(热度5052.8w) 红果热度5101w 平台播放量2亿 拼多多爆剧7808.8w"));
        works.add(work("WHH-003", "重生阴夫年少时", "aired", "short_drama", "female_antagonist_1", "黄薇薇", null, null, List.of("朱一未", "白昕怡"), "红果校园热播榜No.3 校园新剧榜No.1 热度4354w"));
        works.add(work("WHH-004", "楝树花开之我本不凡", "aired", "short_drama", "female_antagonist_1", "萧瑶", null, null, List.of(), "（绍兴zf项目） 播放量破亿 全网话题量超百万 20+媒体报道"));
        works.add(work("WHH-005", "都市弃少修仙逆袭路", "aired", "short_drama", "female_lead", "苏以沫", null, null, List.of(), null));
        works.add(work("WHH-006", "太太被读心后，清冷教授坐不住啦", "aired", "short_drama", "female_antagonist_1", "吕薇", null, null, List.of(), null));
        works.add(work("WHH-007", "爱情啊，结果都那样", "aired", "short_drama", "female_antagonist_1", "黄倩倩", null, null, List.of(), null));
        works.add(work("WHH-008", "闺蜜双穿，这次不斗只磕cp", "aired", "short_drama", "female_antagonist_1", "顾雨薇", null, null, List.of(), null));
        works.add(work("WHH-009", "前妻不知道的事", "aired", "short_drama", "female_antagonist_1", "袁莉", null, null, List.of(), null));
        works.add(work("WHH-010", "替身老公，他不干了！", "aired", "short_drama", "female_supporting_2", "秦薇", null, null, List.of(), null));
        works.add(work("WHH-011", "千金她被上司当软柿子捏了", "aired", "short_drama", "female_supporting_2", "任晓婷", null, null, List.of(), null));
        works.add(work("WHH-012", "婚后才心动", "aired", "short_drama", "female_supporting_2", "白婉婷", null, null, List.of(), null));
        works.add(work("WHH-013", "过年攀比，我靠吹牛身价万亿", "aired", "short_drama", "female_supporting_2", "苏晗", null, null, List.of(), null));
        works.add(work("WHH-014", "出狱后，我靠操盘把女儿宠上天", "aired", "short_drama", "female_supporting_2", "杨秘书", null, null, List.of(), null));
        works.add(work("WHH-015", "叶秘书为何也这样？", "upcoming", "horizontal_short_drama", "female_supporting_2", "魏倩倩", null, null, List.of(), null));
        works.add(work("WHH-016", "天道判官：开局罚款五十万", "upcoming", "short_drama", "female_lead", "沈幼楚", null, null, List.of(), null));
        works.add(work("WHH-017", "生个黑娃非说是我的", "upcoming", "short_drama", "female_lead", "沈月", null, null, List.of(), null));
        works.add(work("WHH-018", "透视后粉碎樱国阴谋", "upcoming", "short_drama", "female_lead", "古玩之神", null, null, List.of(), null));
        works.add(work("WHH-019", "离婚后，我们互撕到底", "upcoming", "short_drama", "female_lead", "阮清禾", null, null, List.of(), null));
        works.add(work("WHH-020", "真假千金", "upcoming", "short_drama", "female_lead", "甄新大", null, null, List.of(), null));
        works.add(work("WHH-021", "宝岛一村", "stage", "stage_play", null, "冷如云", null, null, List.of(), null));
        works.add(work("WHH-022", "请你对我说个谎", "stage", "stage_play", null, "白色陶乐斯", null, null, List.of(), null));
        works.add(work("WHH-023", "蝶", "stage", "musical", null, "浪花儿", null, null, List.of(), null));
        works.add(work("WHH-024", "哥哥失踪之谜", "horizontal", "tv_column_drama", null, "周天瑶", "广西卫视", null, List.of(), null));
        works.add(work("WHH-025", "旋转吧，爱情", "horizontal", "film_tv", null, "童年白晓牧", null, null, List.of(), null));
        works.add(work("WHH-026", "飞哥大英雄", "horizontal", "film_tv", null, "菁菁", null, null, List.of("雷佳音"), null));
        works.add(work("WHH-027", "无贼", "horizontal", "film_tv", null, null, null, null, List.of("殷桃"), null));
        works.add(work("WHH-028", "烽火青春", "horizontal", "film_tv", null, null, null, null, List.of(), null));
        works.add(work("WHH-029", "我没忘记你", "horizontal", "micro_film", null, null, null, null, List.of(), null));
        return List.copyOf(works);
    }

    private static ExpectedWork work(
            String fixtureId,
            String projectName,
            String publishStatus,
            String workTypeCode,
            String roleLevelCode,
            String roleName,
            String platform,
            String syncSoundStatus,
            List<String> collaborators,
            String achievementText) {
        return new ExpectedWork(
                fixtureId,
                projectName,
                publishStatus,
                workTypeCode,
                roleLevelCode,
                roleName,
                platform,
                syncSoundStatus,
                collaborators,
                achievementText);
    }

    private record ExpectedWork(
            String fixtureId,
            String projectName,
            String publishStatus,
            String workTypeCode,
            String roleLevelCode,
            String roleName,
            String platform,
            String syncSoundStatus,
            List<String> collaborators,
            String achievementText) {
    }

    private record ActualWork(
            long experienceId,
            String projectName,
            String publishStatus,
            String workTypeCode,
            String roleLevelCode,
            String roleName,
            String platform,
            String syncSoundStatus,
            List<String> collaborators,
            String achievementText,
            String dedupeKey,
            String sourceType) {
    }

    private record BaselineWork(
            long experienceId,
            long userId,
            long actorProfileId,
            String projectName,
            String roleName,
            String normalizedProjectName,
            String normalizedRoleName,
            String dedupeKey,
            String sourceType,
            String publishStatus,
            String workTypeCode,
            String roleLevelCode,
            int shootYear,
            int shootMonth,
            String platform,
            String syncSoundStatus,
            List<String> collaborators,
            String achievementText,
            String description,
            int sortNo,
            String extendedField,
            String rid,
            int version,
            int deleted) {
    }

    @Configuration(proxyBeanMethods = false)
    @MapperScan(basePackages = {"com.kaipai.mapper.actor", "com.kaipai.mapper.card"})
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            return DATABASE.dataSource();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(
                DataSource dataSource,
                MetaObjectHandlerConfig metaObjectHandler,
                MybatisPlusInterceptor interceptor) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(metaObjectHandler));
            factory.setPlugins(interceptor);
            return factory.getObject();
        }

        @Bean
        MetaObjectHandlerConfig metaObjectHandler() {
            return new MetaObjectHandlerConfig();
        }

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            return interceptor;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        ActorWorkService actorWorkService(
                ActorExperienceMapper experienceMapper,
                ActorProfileMapper profileMapper,
                ActorProfileRepresentativeWorkMapper representativeMapper,
                ShareCardWorkMapper shareCardWorkMapper,
                ObjectMapper objectMapper) {
            return new ActorWorkServiceImpl(
                    experienceMapper,
                    profileMapper,
                    representativeMapper,
                    shareCardWorkMapper,
                    objectMapper);
        }
    }
}
