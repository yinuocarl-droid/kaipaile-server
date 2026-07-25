package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.handler.MetaObjectHandlerConfig;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.mapper.actor.ActorMediaAssetPageMapper;
import com.kaipai.mapper.actor.ActorProfileAssetMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.actor.ActorWorkAssetMapper;
import com.kaipai.mapper.card.ShareCardAssetMapper;
import com.kaipai.model.actor.dto.ActorAssetBindingDTO;
import com.kaipai.model.actor.dto.ActorWorkAssetRespDTO;
import com.kaipai.model.actor.dto.ActorWorkAssetsReplaceDTO;
import com.kaipai.service.actor.ActorMediaAssetService;
import com.kaipai.service.actor.ActorPrivatePdfProcessor;
import com.kaipai.service.actor.PrivateActorMediaStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringJUnitConfig(ActorWorkAssetReplacementMySqlIntegrationTest.TestConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActorWorkAssetReplacementMySqlIntegrationTest {

    private static final long USER_ID = 7L;
    private static final long PROFILE_ID = 9L;
    private static final long WORK_ID = 12L;
    private static final long OLD_PHOTO_ID = 80L;
    private static final long NEW_PHOTO_ID = 82L;
    private static final long FAILING_VIDEO_ID = 83L;
    private static final long CONCURRENT_USER_ID = 107L;
    private static final long CONCURRENT_PROFILE_ID = 109L;
    private static final long CONCURRENT_WORK_ID = 112L;
    private static final long CONCURRENT_PHOTO_ID = 182L;
    private static final String CONCURRENT_INSERT_GATE = "kaipai_work_asset_insert_182";
    private static final long COLLECTION_USER_ID = 207L;
    private static final long COLLECTION_PROFILE_ID = 209L;
    private static final long COLLECTION_WORK_ID = 212L;
    private static final long COLLECTION_PHOTO_A_ID = 282L;
    private static final long COLLECTION_PHOTO_B_ID = 283L;
    private static final String COLLECTION_INSERT_GATE = "kaipai_work_asset_insert_282";
    private static final long SNAPSHOT_USER_ID = 307L;
    private static final long SNAPSHOT_PROFILE_ID = 309L;
    private static final long SNAPSHOT_WORK_ID = 312L;
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");
    private static final List<String> MIGRATIONS = List.of(
            "V20260723_001__career_profile_domain_foundation.sql",
            "V20260723_002__actor_media_asset_relations.sql",
            "V20260723_003__share_card_favorite.sql");
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("kaipai_work_asset_rollback_test")
            .withUsername("kaipai_test")
            .withPassword("kaipai_test")
            .withCommand("--log-bin-trust-function-creators=1");

    static {
        MYSQL.start();
    }

    private final ActorMediaAssetService transactionalService;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;

    @Autowired
    ActorWorkAssetReplacementMySqlIntegrationTest(
            ActorMediaAssetService transactionalService,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            DataSource dataSource) {
        this.transactionalService = transactionalService;
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
        this.dataSource = dataSource;
    }

    @BeforeAll
    void initializeDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            createLegacyTables(connection);
            for (String migration : MIGRATIONS) {
                executeMigration(connection, migration);
            }
        }
        seedRollbackScenario();
        seedConcurrentDeleteScenario();
        seedConcurrentReplacementScenario();
        seedSnapshotScenario();
        installSecondInsertFailureTrigger();
    }

    @AfterAll
    static void stopMySql() {
        MYSQL.stop();
    }

    @Test
    void secondInsertFailureRollsBackOldRelationFirstInsertAndVersion() {
        assertTrue(AopUtils.isAopProxy(transactionalService),
                "replaceWorkAssets must run through a Spring transactional proxy");
        assertEquals(ActorMediaAssetServiceImpl.class, AopUtils.getTargetClass(transactionalService));

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> transactionalService.replaceWorkAssets(
                        USER_ID,
                        WORK_ID,
                        bindings(
                                binding(NEW_PHOTO_ID, "still", 1),
                                binding(FAILING_VIDEO_ID, "clip", 1))));

        String databaseFailureMessage = failure.getMostSpecificCause().getMessage();
        assertTrue(databaseFailureMessage.contains("forced second insert failure after still insert"),
                () -> "the database must reject clip only after the still insert succeeds; actual: "
                        + databaseFailureMessage);

        inNewTransaction(() -> {
            assertEquals(List.of(new ActiveBinding(OLD_PHOTO_ID, "still", 1)),
                    queryActiveBindings(),
                    "the logical delete of the old active relation must roll back");
            assertEquals(0L, countRelationsForAsset(NEW_PHOTO_ID),
                    "the successfully inserted still relation must roll back");
            assertEquals(0L, countRelationsForAsset(FAILING_VIDEO_ID),
                    "the rejected clip relation must not remain");
            assertEquals(7L, queryWorkLibraryVersion(),
                    "the work-library version must remain unchanged");
            assertEquals(1L, countSuccessfulStillInsertProbes(),
                    "the non-transactional trigger probe proves the still insert completed before clip failed");
        });
    }

    @Test
    void deleteWaitsForInFlightReplaceAndThenRejectsReferencedAsset() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> replace = executor.submit(() -> transactionalService.replaceWorkAssets(
                    CONCURRENT_USER_ID,
                    CONCURRENT_WORK_ID,
                    bindings(binding(CONCURRENT_PHOTO_ID, "still", 1))));
            awaitConcurrentInsertGate();

            Future<Throwable> delete = executor.submit(() -> {
                try {
                    transactionalService.delete(CONCURRENT_USER_ID, CONCURRENT_PHOTO_ID);
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            });

            replace.get(15, TimeUnit.SECONDS);
            Throwable deleteFailure = delete.get(15, TimeUnit.SECONDS);
            assertTrue(deleteFailure instanceof BizException,
                    () -> "delete must reject the newly referenced asset; actual: " + deleteFailure);
            assertEquals(46014, ((BizException) deleteFailure).getCode());

            inNewTransaction(() -> {
                assertEquals(0, queryAssetDeleted(CONCURRENT_PHOTO_ID),
                        "the referenced asset must remain active");
                assertEquals(1L, countActiveRelations(CONCURRENT_WORK_ID, CONCURRENT_PHOTO_ID),
                        "the successful replacement relation must remain active");
                assertEquals(1L, countInsertProbes(CONCURRENT_PHOTO_ID),
                        "the non-transactional probe must record the in-flight relation insert");
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void laterCompleteSetReplacementDoesNotMergeWithInFlightReplacement() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> replacementA = executor.submit(() -> inReadCommittedTransaction(
                    () -> transactionalService.replaceWorkAssets(
                            COLLECTION_USER_ID,
                            COLLECTION_WORK_ID,
                            bindings(binding(COLLECTION_PHOTO_A_ID, "still", 1)))));
            awaitNamedGate(COLLECTION_INSERT_GATE);

            Future<?> replacementB = executor.submit(() -> inReadCommittedTransaction(
                    () -> transactionalService.replaceWorkAssets(
                            COLLECTION_USER_ID,
                            COLLECTION_WORK_ID,
                            bindings(binding(COLLECTION_PHOTO_B_ID, "still", 1)))));

            replacementA.get(20, TimeUnit.SECONDS);
            replacementB.get(20, TimeUnit.SECONDS);

            inNewTransaction(() -> {
                assertEquals(List.of(new ActiveBinding(COLLECTION_PHOTO_B_ID, "still", 1)),
                        queryActiveBindings(COLLECTION_WORK_ID),
                        "the later complete-set request must replace, not merge with, the in-flight set");
                assertEquals(22L, queryWorkLibraryVersion(COLLECTION_PROFILE_ID),
                        "both effective complete-set replacements must increment the version once");
                assertEquals(1L, countInsertProbes(COLLECTION_PHOTO_A_ID),
                        "the non-transactional probe must prove replacement A was in flight before B started");
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ownedReadyPhotoLockRequiresAnExistingWriteTransaction() {
        assertThrows(IllegalTransactionStateException.class,
                () -> transactionalService.requireOwnedReadyPhoto(USER_ID, OLD_PHOTO_ID));
    }

    @Test
    void workAssetSnapshotFiltersDeletedAndForeignRowsOrdersAndDoesNotChangeVersion() {
        long versionBefore = queryWorkLibraryVersion(SNAPSHOT_PROFILE_ID);

        List<ActorWorkAssetRespDTO> assets = transactionalService.workAssets(
                SNAPSHOT_USER_ID, SNAPSHOT_WORK_ID);

        assertEquals(List.of(381L, 382L, 383L, 384L),
                assets.stream().map(ActorWorkAssetRespDTO::getAssetId).toList());
        assertEquals(List.of("still", "still", "still", "clip"),
                assets.stream().map(ActorWorkAssetRespDTO::getUsageCode).toList());
        assertEquals(List.of(1, 2, 2, 1),
                assets.stream().map(ActorWorkAssetRespDTO::getSortNo).toList());
        assertEquals(List.of("photo", "photo", "photo", "video"),
                assets.stream().map(ActorWorkAssetRespDTO::getMediaType).toList());
        assertEquals("work_still", assets.get(0).getCategoryCode());
        assertEquals("scene-01.jpg", assets.get(0).getOriginalName());
        assertEquals(null, assets.get(1).getCategoryCode());
        assertEquals(null, assets.get(1).getOriginalName());
        assertEquals(List.of("ready", "processing", "ready", "ready"),
                assets.stream().map(ActorWorkAssetRespDTO::getProcessStatus).toList());
        assertEquals(versionBefore, queryWorkLibraryVersion(SNAPSHOT_PROFILE_ID));
    }

    private void seedRollbackScenario() {
        jdbc.update("""
                INSERT INTO actor_profile (actor_profile_id, user_id, work_library_version, deleted)
                VALUES (?, ?, 7, 0)
                """, PROFILE_ID, USER_ID);
        jdbc.update("""
                INSERT INTO actor_experience (experience_id, user_id, actor_profile_id, drama_name, deleted)
                VALUES (?, ?, ?, 'rollback proof work', 0)
                """, WORK_ID, USER_ID, PROFILE_ID);
        insertReadyAsset(OLD_PHOTO_ID, "photo");
        insertReadyAsset(NEW_PHOTO_ID, "photo");
        insertReadyAsset(FAILING_VIDEO_ID, "video");
        jdbc.update("""
                INSERT INTO actor_work_asset (experience_id, asset_id, usage_code, sort_no, deleted)
                VALUES (?, ?, 'still', 1, 0)
                """, WORK_ID, OLD_PHOTO_ID);
    }

    private void seedConcurrentDeleteScenario() {
        jdbc.update("""
                INSERT INTO actor_profile (actor_profile_id, user_id, work_library_version, deleted)
                VALUES (?, ?, 3, 0)
                """, CONCURRENT_PROFILE_ID, CONCURRENT_USER_ID);
        jdbc.update("""
                INSERT INTO actor_experience (experience_id, user_id, actor_profile_id, drama_name, deleted)
                VALUES (?, ?, ?, 'concurrent delete proof work', 0)
                """, CONCURRENT_WORK_ID, CONCURRENT_USER_ID, CONCURRENT_PROFILE_ID);
        insertReadyAsset(CONCURRENT_PHOTO_ID, CONCURRENT_USER_ID, "photo");
    }

    private void seedConcurrentReplacementScenario() {
        jdbc.update("""
                INSERT INTO actor_profile (actor_profile_id, user_id, work_library_version, deleted)
                VALUES (?, ?, 20, 0)
                """, COLLECTION_PROFILE_ID, COLLECTION_USER_ID);
        jdbc.update("""
                INSERT INTO actor_experience (experience_id, user_id, actor_profile_id, drama_name, deleted)
                VALUES (?, ?, ?, 'complete set serialization proof work', 0)
                """, COLLECTION_WORK_ID, COLLECTION_USER_ID, COLLECTION_PROFILE_ID);
        insertReadyAsset(COLLECTION_PHOTO_A_ID, COLLECTION_USER_ID, "photo");
        insertReadyAsset(COLLECTION_PHOTO_B_ID, COLLECTION_USER_ID, "photo");
    }

    private void seedSnapshotScenario() {
        jdbc.update("""
                INSERT INTO actor_profile (actor_profile_id, user_id, work_library_version, deleted)
                VALUES (?, ?, 31, 0)
                """, SNAPSHOT_PROFILE_ID, SNAPSHOT_USER_ID);
        jdbc.update("""
                INSERT INTO actor_experience (experience_id, user_id, actor_profile_id, drama_name, deleted)
                VALUES (?, ?, ?, 'snapshot proof work', 0)
                """, SNAPSHOT_WORK_ID, SNAPSHOT_USER_ID, SNAPSHOT_PROFILE_ID);
        insertSnapshotAsset(381L, SNAPSHOT_USER_ID, "photo", "work_still", "scene-01.jpg", "ready", 0);
        insertSnapshotAsset(382L, SNAPSHOT_USER_ID, "photo", null, null, "processing", 0);
        insertSnapshotAsset(383L, SNAPSHOT_USER_ID, "photo", "behind_scene", "scene-03.jpg", "ready", 0);
        insertSnapshotAsset(384L, SNAPSHOT_USER_ID, "video", "work_clip", "clip-01.mp4", "ready", 0);
        insertSnapshotAsset(385L, SNAPSHOT_USER_ID, "photo", "deleted_relation", "hidden-01.jpg", "ready", 0);
        insertSnapshotAsset(386L, SNAPSHOT_USER_ID, "photo", "deleted_asset", "hidden-02.jpg", "ready", 1);
        insertSnapshotAsset(387L, SNAPSHOT_USER_ID + 1, "photo", "foreign", "hidden-03.jpg", "ready", 0);
        insertSnapshotRelation(381L, "still", 1, 0);
        insertSnapshotRelation(383L, "still", 2, 0);
        insertSnapshotRelation(382L, "still", 2, 0);
        insertSnapshotRelation(384L, "clip", 1, 0);
        insertSnapshotRelation(385L, "still", 3, 1);
        insertSnapshotRelation(386L, "still", 4, 0);
        insertSnapshotRelation(387L, "still", 5, 0);
    }

    private void insertSnapshotAsset(
            long assetId,
            long userId,
            String mediaType,
            String categoryCode,
            String originalName,
            String processStatus,
            int deleted) {
        jdbc.update("""
                INSERT INTO actor_media_asset (
                    asset_id, user_id, media_type, category_code, storage_provider, bucket_code,
                    object_key, original_name, process_status, source_type, deleted)
                VALUES (?, ?, ?, ?, 'cos', 'private-test', ?, ?, ?, 'upload', ?)
                """, assetId, userId, mediaType, categoryCode, "actor/" + userId + "/" + assetId,
                originalName, processStatus, deleted);
    }

    private void insertSnapshotRelation(long assetId, String usageCode, int sortNo, int deleted) {
        jdbc.update("""
                INSERT INTO actor_work_asset (experience_id, asset_id, usage_code, sort_no, deleted)
                VALUES (?, ?, ?, ?, ?)
                """, SNAPSHOT_WORK_ID, assetId, usageCode, sortNo, deleted);
    }

    private void insertReadyAsset(long assetId, String mediaType) {
        insertReadyAsset(assetId, USER_ID, mediaType);
    }

    private void insertReadyAsset(long assetId, long userId, String mediaType) {
        jdbc.update("""
                INSERT INTO actor_media_asset (
                    asset_id, user_id, media_type, storage_provider, bucket_code, object_key,
                    process_status, source_type, deleted)
                VALUES (?, ?, ?, 'cos', 'private-test', ?, 'ready', 'upload', 0)
                """, assetId, userId, mediaType, "actor/" + userId + "/" + assetId);
    }

    private void installSecondInsertFailureTrigger() {
        jdbc.execute("""
                CREATE TABLE work_asset_insert_probe (
                    asset_id BIGINT NOT NULL,
                    connection_id BIGINT NOT NULL,
                    PRIMARY KEY (asset_id)
                ) ENGINE=MyISAM
                """);
        jdbc.execute("""
                CREATE TRIGGER pause_first_complete_set_insert
                BEFORE INSERT ON actor_work_asset FOR EACH ROW
                BEGIN
                  IF NEW.asset_id = 282 THEN
                    INSERT INTO work_asset_insert_probe (asset_id, connection_id)
                    VALUES (NEW.asset_id, CONNECTION_ID());
                    DO GET_LOCK('kaipai_work_asset_insert_282', 0);
                    DO SLEEP(5);
                    DO RELEASE_LOCK('kaipai_work_asset_insert_282');
                  END IF;
                END
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_selected_work_asset_insert
                AFTER INSERT ON actor_work_asset FOR EACH ROW
                BEGIN
                  IF NEW.asset_id = 82 THEN
                    INSERT INTO work_asset_insert_probe (asset_id, connection_id)
                    VALUES (NEW.asset_id, CONNECTION_ID());
                  ELSEIF NEW.asset_id = 83 THEN
                    IF (SELECT COUNT(*) FROM work_asset_insert_probe WHERE asset_id = 82) <> 1 THEN
                      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'clip insert reached before still insert';
                    END IF;
                    SIGNAL SQLSTATE '45000'
                      SET MESSAGE_TEXT = 'forced second insert failure after still insert';
                  ELSEIF NEW.asset_id = 182 THEN
                    INSERT INTO work_asset_insert_probe (asset_id, connection_id)
                    VALUES (NEW.asset_id, CONNECTION_ID());
                    DO GET_LOCK('kaipai_work_asset_insert_182', 0);
                    DO SLEEP(5);
                    DO RELEASE_LOCK('kaipai_work_asset_insert_182');
                  END IF;
                END
                """);
    }

    private List<ActiveBinding> queryActiveBindings() {
        return queryActiveBindings(WORK_ID);
    }

    private List<ActiveBinding> queryActiveBindings(long experienceId) {
        return jdbc.query("""
                        SELECT asset_id, usage_code, sort_no
                        FROM actor_work_asset
                        WHERE experience_id = ? AND deleted = 0
                        ORDER BY usage_code, sort_no, asset_id
                        """,
                (resultSet, rowNum) -> new ActiveBinding(
                        resultSet.getLong("asset_id"),
                        resultSet.getString("usage_code"),
                        resultSet.getInt("sort_no")),
                experienceId);
    }

    private long countRelationsForAsset(long assetId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM actor_work_asset
                WHERE experience_id = ? AND asset_id = ?
                """, Long.class, WORK_ID, assetId);
    }

    private long queryWorkLibraryVersion() {
        return queryWorkLibraryVersion(PROFILE_ID);
    }

    private long queryWorkLibraryVersion(long profileId) {
        return jdbc.queryForObject("""
                SELECT work_library_version FROM actor_profile WHERE actor_profile_id = ?
                """, Long.class, profileId);
    }

    private long countSuccessfulStillInsertProbes() {
        return countInsertProbes(NEW_PHOTO_ID);
    }

    private long countInsertProbes(long assetId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM work_asset_insert_probe WHERE asset_id = ?
                """, Long.class, assetId);
    }

    private void awaitConcurrentInsertGate() throws InterruptedException {
        awaitNamedGate(CONCURRENT_INSERT_GATE);
    }

    private void awaitNamedGate(String gateName) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long ownerConnectionId = jdbc.queryForObject(
                    "SELECT IS_USED_LOCK(?)",
                    Long.class,
                    gateName);
            if (ownerConnectionId != null) {
                return;
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("timed out waiting for concurrent insert gate");
    }

    private int queryAssetDeleted(long assetId) {
        return jdbc.queryForObject("""
                SELECT deleted FROM actor_media_asset WHERE asset_id = ?
                """, Integer.class, assetId);
    }

    private long countActiveRelations(long experienceId, long assetId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM actor_work_asset
                WHERE experience_id = ? AND asset_id = ? AND deleted = 0
                """, Long.class, experienceId, assetId);
    }

    private void inNewTransaction(Runnable assertions) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> assertions.run());
    }

    private void inReadCommittedTransaction(Runnable work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.executeWithoutResult(status -> work.run());
    }

    private static ActorAssetBindingDTO binding(long assetId, String usageCode, int sortNo) {
        ActorAssetBindingDTO binding = new ActorAssetBindingDTO();
        binding.setAssetId(assetId);
        binding.setUsageCode(usageCode);
        binding.setSortNo(sortNo);
        return binding;
    }

    private static ActorWorkAssetsReplaceDTO bindings(ActorAssetBindingDTO... bindings) {
        ActorWorkAssetsReplaceDTO request = new ActorWorkAssetsReplaceDTO();
        request.setBindings(List.of(bindings));
        return request;
    }

    private static void createLegacyTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
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
                      photo_urls JSON DEFAULT NULL,
                      experience_desc TEXT DEFAULT NULL,
                      is_certified TINYINT DEFAULT NULL,
                      is_open_apply TINYINT DEFAULT NULL,
                      profile_status INT DEFAULT NULL,
                      sort_no INT DEFAULT NULL,
                      extended_field JSON DEFAULT NULL,
                      version INT NOT NULL DEFAULT 0,
                      deleted TINYINT NOT NULL DEFAULT 0,
                      rid VARCHAR(64) DEFAULT NULL,
                      create_user_id BIGINT DEFAULT NULL,
                      create_user_name VARCHAR(64) DEFAULT '',
                      create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      update_user_id BIGINT DEFAULT NULL,
                      update_user_name VARCHAR(64) DEFAULT '',
                      last_update DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (actor_profile_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("""
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
                      extended_field JSON DEFAULT NULL,
                      version INT NOT NULL DEFAULT 0,
                      deleted TINYINT NOT NULL DEFAULT 0,
                      rid VARCHAR(64) DEFAULT NULL,
                      create_user_id BIGINT DEFAULT NULL,
                      create_user_name VARCHAR(64) DEFAULT '',
                      create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      update_user_id BIGINT DEFAULT NULL,
                      update_user_name VARCHAR(64) DEFAULT '',
                      last_update DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (experience_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    private static void executeMigration(Connection connection, String migrationName)
            throws IOException, SQLException {
        String sql = Files.readString(MIGRATION_DIR.resolve(migrationName), StandardCharsets.UTF_8);
        try (Statement statement = connection.createStatement()) {
            boolean resultSet = statement.execute(sql);
            while (resultSet || statement.getUpdateCount() != -1) {
                if (resultSet) {
                    try (ResultSet ignored = statement.getResultSet()) {
                        // Every result produced by the multi-statement migration must be closed.
                    }
                }
                resultSet = statement.getMoreResults();
            }
        }
    }

    private record ActiveBinding(long assetId, String usageCode, int sortNo) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan(basePackages = {"com.kaipai.mapper.actor", "com.kaipai.mapper.card"})
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            String jdbcUrl = MYSQL.getJdbcUrl();
            dataSource.setUrl(jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "allowMultiQueries=true");
            dataSource.setUsername(MYSQL.getUsername());
            dataSource.setPassword(MYSQL.getPassword());
            return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(
                DataSource dataSource,
                MetaObjectHandlerConfig metaObjectHandler) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(metaObjectHandler));
            return factory.getObject();
        }

        @Bean
        MetaObjectHandlerConfig metaObjectHandler() {
            return new MetaObjectHandlerConfig();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        ActorMediaAssetService actorMediaAssetService(
                ActorMediaAssetMapper assetMapper,
                ActorProfileMapper profileMapper,
                ActorProfileAssetMapper profileAssetMapper,
                ActorWorkAssetMapper workAssetMapper,
                ActorExperienceMapper experienceMapper,
                ShareCardAssetMapper shareAssetMapper,
                ActorMediaAssetPageMapper pageMapper,
                PrivateActorMediaStorage storage,
                ActorPrivatePdfProcessor pdfProcessor,
                ActorPdfAssetLifecycleService pdfLifecycle) {
            return new ActorMediaAssetServiceImpl(
                    assetMapper,
                    profileMapper,
                    profileAssetMapper,
                    workAssetMapper,
                    experienceMapper,
                    shareAssetMapper,
                    pageMapper,
                    storage,
                    pdfProcessor,
                    pdfLifecycle);
        }

        @Bean
        ActorPdfAssetLifecycleService actorPdfAssetLifecycleService(
                ActorMediaAssetMapper assetMapper,
                ActorMediaAssetPageMapper pageMapper) {
            return new ActorPdfAssetLifecycleService(assetMapper, pageMapper);
        }

        @Bean
        PrivateActorMediaStorage privateActorMediaStorage() {
            return new PrivateActorMediaStorage() {
                @Override
                public StoredObjectRef store(Long userId, String mediaType, MultipartFile file) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public StoredObjectRef storeGenerated(
                        Long userId,
                        String mediaType,
                        byte[] bytes,
                        String contentType,
                        String extension) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public SignedAccess issueAccessUrl(String bucketCode, String objectKey, java.time.Duration duration) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void delete(String bucketCode, String objectKey) {
                    // Database concurrency is the subject of this test; object deletion has no side effect here.
                }
            };
        }

        @Bean
        ActorPrivatePdfProcessor actorPrivatePdfProcessor() {
            return (userId, file) -> {
                throw new UnsupportedOperationException();
            };
        }
    }
}
