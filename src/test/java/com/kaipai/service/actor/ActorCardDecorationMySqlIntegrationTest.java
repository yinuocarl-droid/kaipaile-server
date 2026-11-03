package com.kaipai.service.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardDecorationMapper;
import com.kaipai.model.actor.card.dto.ActorCardDecorationDTO.*;
import com.kaipai.model.system.entity.AdminOperationLog;
import com.kaipai.service.system.AdminOperationLogService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import javax.sql.DataSource;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Uses only a disposable MySQL container, never a configured application database. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActorCardDecorationMySqlIntegrationTest {
    private MySQLContainer<?> mysql;
    private AnnotationConfigApplicationContext context;
    private ActorCardDecorationService service;
    private JdbcTemplate jdbc;
    private static DataSource database;
    private static final AtomicBoolean FAIL_AUDIT = new AtomicBoolean();

    @BeforeAll void start() throws Exception {
        assertTrue(DockerClientFactory.instance().isDockerAvailable(), "isolated Docker MySQL required; this integration gate must not silently skip");
        mysql = new MySQLContainer<>("mysql:8.0.36").withDatabaseName("decoration_isolated_test");
        mysql.start();
        database = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE card_scene_template (template_id BIGINT PRIMARY KEY, deleted INT NOT NULL DEFAULT 0)");
        jdbc.execute("INSERT INTO card_scene_template(template_id) VALUES (7), (9)");
        jdbc.execute("CREATE TABLE decoration_test_audit (id BIGINT AUTO_INCREMENT PRIMARY KEY, target_id BIGINT)");
        migrate();
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(ActorCardDecorationService.class);
        assertTrue(AopUtils.isAopProxy(service));
    }
    private void migrate() throws Exception {
        try (var c = database.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V20261022_001__actor_card_decoration_versions.sql"));
        }
    }
    @AfterAll void stop() { if (context != null) context.close(); if (mysql != null) mysql.stop(); }
    @BeforeEach void reset() {
        FAIL_AUDIT.set(false);
        jdbc.update("UPDATE card_scene_template SET deleted = 0");
        jdbc.update("DELETE FROM actor_card_decoration_version");
        jdbc.update("DELETE FROM actor_card_decoration_draft");
        jdbc.update("DELETE FROM decoration_test_audit");
    }
    private ObjectNode config(int radius) {
        var c = service.load(7L).config().deepCopy();
        ((ObjectNode) c).putObject("defaults").put("radius", radius);
        return (ObjectNode) c;
    }
    @Test void logicallyDeletedTemplateCannotLoadOrMutateDecoration() {
        jdbc.update("UPDATE card_scene_template SET deleted = 1 WHERE template_id = 9");
        assertEquals(404, assertThrows(BizException.class, () -> service.load(9L)).getCode());
        assertEquals(404, assertThrows(BizException.class, () -> service.save(9L, new SaveReq(0L, config(8)))).getCode());
    }
    @Test void migrationCanRunTwiceWithoutChangingDraftOrSnapshot() throws Exception {
        var saved = service.save(7L, new SaveReq(0L, config(32)));
        service.publish(7L, new ChangeReq(saved.revision(), "首版"));
        migrate(); migrate();
        assertEquals(2, service.load(7L).revision());
        assertEquals(32, service.version(7L, 1).config().at("/defaults/radius").asInt());
        assertEquals(1, service.versions(7L).size());
    }
    @Test void realMapperPersistsSparseDraftAndImmutableMonotonicVersions() {
        var saved = service.save(7L, new SaveReq(0L, config(32)));
        assertEquals(32, saved.preview().at("/modules/2/style/radius").asInt());
        assertFalse(saved.config().path("modules").get(2).has("style"));
        var v1 = service.publish(7L, new ChangeReq(saved.revision(), "首版"));
        var changed = service.save(7L, new SaveReq(v1.revision(), config(0)));
        var v2 = service.publish(7L, new ChangeReq(changed.revision(), "直角"));
        var rolled = service.activate(7L, 1, new ChangeReq(v2.revision(), "切回"));
        assertEquals(0, rolled.config().at("/defaults/radius").asInt());
        assertEquals(32, service.version(7L, 1).config().at("/defaults/radius").asInt());
        assertEquals(0, service.version(7L, 2).config().at("/defaults/radius").asInt());
        var next = service.publish(7L, new ChangeReq(rolled.revision(), "再发草稿"));
        assertEquals(3, next.activeVersion());
        assertThrows(BizException.class, () -> service.version(9L, 1));
    }
    @Test void failedRequiredAuditRollsBackDraftInitializationAndAuditWrite() {
        FAIL_AUDIT.set(true);
        assertThrows(IllegalStateException.class, () -> service.save(7L, new SaveReq(0L, config(8))));
        assertEquals(0, service.load(7L).revision());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM actor_card_decoration_draft", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM decoration_test_audit", Integer.class));
    }
    @Test void failedPublicationAuditRollsBackSnapshotAndHeadTogether() {
        var saved = service.save(7L, new SaveReq(0L, config(8)));
        FAIL_AUDIT.set(true);
        assertThrows(IllegalStateException.class, () -> service.publish(7L, new ChangeReq(saved.revision(), "不能发布")));
        assertEquals(1, service.load(7L).revision()); assertNull(service.load(7L).activeVersion());
        assertEquals(0, service.versions(7L).size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM decoration_test_audit", Integer.class));
    }
    @Test void concurrentRevisionWritesHaveOneWinnerAndVersionNumbersAreUnique() throws Exception {
        var saved = service.save(7L, new SaveReq(0L, config(8)));
        var c = config(16); var gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> task = () -> { gate.await(10, TimeUnit.SECONDS); try { service.save(7L, new SaveReq(saved.revision(), c)); return true; } catch (BizException ex) { assertEquals(409, ex.getCode()); return false; } };
            var a = pool.submit(task); var b = pool.submit(task); gate.countDown();
            assertNotEquals(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        var draft = service.load(7L); assertEquals(2, draft.revision());
        service.publish(7L, new ChangeReq(draft.revision(), "唯一版本"));
        assertThrows(DuplicateKeyException.class, () -> jdbc.update("INSERT INTO actor_card_decoration_version (template_id, version_no, schema_json, checksum, published_by, publish_note) SELECT template_id, version_no, schema_json, checksum, published_by, publish_note FROM actor_card_decoration_version"));
    }

    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() { return database; }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory factory(DataSource ds) throws Exception {
            var bean = new SqlSessionFactoryBean(); bean.setDataSource(ds);
            var configuration = new org.apache.ibatis.session.Configuration(); configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(ActorCardDecorationMapper.class); bean.setConfiguration(configuration); return bean.getObject();
        }
        @Bean ActorCardDecorationMapper mapper(SqlSessionFactory factory) { return new SqlSessionTemplate(factory).getMapper(ActorCardDecorationMapper.class); }
        @Bean ObjectMapper json() { return new ObjectMapper(); }
        @Bean AdminAuthContext auth() { var auth = mock(AdminAuthContext.class); when(auth.getCurrentAdminUserId()).thenReturn(8L); return auth; }
        @Bean AdminOperationLogService auditStore(DataSource ds) {
            var store = mock(AdminOperationLogService.class);
            when(store.save(any(AdminOperationLog.class))).thenAnswer(invocation -> { new JdbcTemplate(ds).update("INSERT INTO decoration_test_audit(target_id) VALUES (?)", ((AdminOperationLog) invocation.getArgument(0)).getTargetId()); return !FAIL_AUDIT.get(); });
            return store;
        }
        @Bean AdminOperationLogger audit(AdminOperationLogService store, AdminAuthContext auth, ObjectMapper json) { return new AdminOperationLogger(store, auth, json); }
        @Bean ActorCardDecorationService service(ActorCardDecorationMapper mapper, ObjectMapper json, AdminAuthContext auth, AdminOperationLogger audit) { return new ActorCardDecorationService(mapper, json, auth, audit); }
    }
}
