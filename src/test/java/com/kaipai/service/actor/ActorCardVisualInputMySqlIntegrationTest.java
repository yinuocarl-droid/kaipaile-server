package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardVisualInputMapper;
import com.kaipai.model.actor.card.entity.ActorCardVisualInput;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/** Real V003 replay and mapper guards against an isolated disposable MySQL only. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActorCardVisualInputMySqlIntegrationTest {

    private static DataSource database;
    private MySQLContainer<?> mysql;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private ActorCardMapper cardMapper;
    private ActorCardVisualInputMapper mapper;
    private PlatformTransactionManager transactions;

    @BeforeAll
    void start() throws Exception {
        assertEquals(true, DockerClientFactory.instance().isDockerAvailable(),
                "isolated Docker MySQL required; this integration gate must not silently skip");
        mysql = new MySQLContainer<>("mysql:8.0.36")
                .withDatabaseName("actor_card_visual_input_isolated_test");
        mysql.start();
        database = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        jdbc = new JdbcTemplate(database);
        jdbc.execute("""
                CREATE TABLE actor_card (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  deleted TINYINT NOT NULL DEFAULT 0,
                  expanded_image_url VARCHAR(1024) NULL
                )
                """);
        jdbc.update("INSERT INTO actor_card(id,user_id,deleted) VALUES (11,7,0)");
        migrate("db/migration/V20260509_033__ai_profile_card_generation.sql");
        migrate("db/migration/V20260901_001__actor_card_internal_auto_expand.sql");
        migrate("db/migration/V20261022_003__actor_card_visual_input.sql");
        context = new AnnotationConfigApplicationContext(Config.class);
        cardMapper = context.getBean(ActorCardMapper.class);
        mapper = context.getBean(ActorCardVisualInputMapper.class);
        transactions = context.getBean(PlatformTransactionManager.class);
    }

    @AfterAll
    void stop() {
        if (context != null) {
            context.close();
        }
        if (mysql != null) {
            mysql.stop();
        }
    }

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM actor_card_visual_input");
        jdbc.update("DELETE FROM actor_ai_profile_card_task");
    }

    @Test
    void v003CanRunTwiceWithoutDestroyingTheFrozenBackgroundSnapshot() throws Exception {
        insertVisual("person_background_ai", "current", 1L,
                "https://managed.test/background-v1.png");

        migrate("db/migration/V20261022_003__actor_card_visual_input.sql");
        migrate("db/migration/V20261022_003__actor_card_visual_input.sql");

        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'actor_card_visual_input'
                  AND COLUMN_NAME = 'background_image_url'
                """, Integer.class));
        ActorCardVisualInput stored = mapper.selectByCardId(11L);
        assertEquals("https://managed.test/background-v1.png", stored.getBackgroundImageUrl());
        assertEquals("current", stored.getInputFingerprint());
    }

    @Test
    void updateInputCasHasExactlyOneWinnerForTheSameRevision() {
        insertVisual("person_ai", "before", 1L, null);
        ActorCardVisualInput first = update("first", 2L);
        ActorCardVisualInput second = update("second", 2L);

        assertEquals(1, mapper.updateInputCas(first, 1L));
        assertEquals(0, mapper.updateInputCas(second, 1L));
        assertEquals("first", mapper.selectByCardId(11L).getInputFingerprint());
        assertEquals(2L, mapper.selectByCardId(11L).getRevision());
    }

    @Test
    void parentCardLockSerializesTwoConcurrentFirstWritersWithoutDeadlock() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var write = (java.util.concurrent.Callable<String>) () -> {
                start.await(10, TimeUnit.SECONDS);
                return new TransactionTemplate(transactions).execute(status -> {
                    assertNotNull(cardMapper.selectOwnedForUpdate(7L, 11L));
                    ActorCardVisualInput existing = mapper.selectByCardIdForUpdate(11L);
                    if (existing != null) {
                        return "existing";
                    }
                    insertVisual("person_ai", "initial", 1L, null);
                    return "inserted";
                });
            };
            var first = pool.submit(write);
            var second = pool.submit(write);
            start.countDown();

            assertEquals(Set.of("inserted", "existing"),
                    Set.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM actor_card_visual_input WHERE card_id=11", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void generatedResultAndTaskAttachmentRequireTheCurrentFingerprint() {
        insertVisual("person_ai", "current", 1L, null);

        assertEquals(0, mapper.applyResultIfCurrent(
                7L, 11L, "stale", "stale-task", "https://managed.test/stale.png"));
        assertEquals(1, mapper.applyResultIfCurrent(
                7L, 11L, "current", "current-task", "https://managed.test/current.png"));
        assertEquals(0, mapper.attachTaskIfCurrent(7L, 11L, "stale", "later-stale-task"));
        assertEquals(1, mapper.attachTaskIfCurrent(7L, 11L, "current", "later-current-task"));

        ActorCardVisualInput stored = mapper.selectByCardId(11L);
        assertEquals("https://managed.test/current.png", stored.getGeneratedImageUrl());
        assertEquals("current", stored.getGeneratedFingerprint());
        assertEquals("later-current-task", stored.getLatestTaskId());
    }

    @Test
    void existingActorCardTaskUniqueKeyRejectsDuplicateInputIdentity() {
        insertTask("task-1");

        assertThrows(DuplicateKeyException.class, () -> insertTask("task-2"));
    }

    private void insertVisual(String mode, String fingerprint, long revision, String backgroundUrl) {
        jdbc.update("""
                INSERT INTO actor_card_visual_input
                  (card_id, user_id, generation_mode, person_source_type, person_asset_id,
                   background_id, background_image_url, revision, input_fingerprint,
                   generation_config_version)
                VALUES (11, 7, ?, 'asset', 51, ?, ?, ?, ?, 'actor-card-visual-v1')
                """, mode, backgroundUrl == null ? null : 31L, backgroundUrl, revision, fingerprint);
    }

    private ActorCardVisualInput update(String fingerprint, long revision) {
        ActorCardVisualInput value = new ActorCardVisualInput();
        value.setCardId(11L);
        value.setUserId(7L);
        value.setGenerationMode("person_ai");
        value.setPersonSourceType("asset");
        value.setPersonAssetId(51L);
        value.setRevision(revision);
        value.setInputFingerprint(fingerprint);
        value.setGenerationConfigVersion("actor-card-visual-v1");
        return value;
    }

    private void insertTask(String taskId) {
        jdbc.update("""
                INSERT INTO actor_ai_profile_card_task
                  (task_id, user_id, actor_profile_id, actor_card_id,
                   template_scene_code, style_code, source_image_url,
                   provider_code, generation_mode, source_fingerprint, deleted)
                VALUES (?, 7, 9, 11, 'classic', 'classic', NULL,
                        'openai', 'person_ai', 'current', 0)
                """, taskId);
    }

    private void migrate(String resource) throws Exception {
        try (var connection = database.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(resource));
        }
    }

    @Configuration
    static class Config {
        @Bean
        DataSource dataSource() {
            return database;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
            bean.setDataSource(dataSource);
            var configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(ActorCardMapper.class);
            configuration.addMapper(ActorCardVisualInputMapper.class);
            bean.setConfiguration(configuration);
            return bean.getObject();
        }

        @Bean
        ActorCardVisualInputMapper mapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(ActorCardVisualInputMapper.class);
        }

        @Bean
        ActorCardMapper cardMapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(ActorCardMapper.class);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
