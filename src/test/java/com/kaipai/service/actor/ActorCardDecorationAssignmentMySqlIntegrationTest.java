package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardDecorationAssignmentMapper;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.BindingSaveReq;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.UpgradeReq;
import com.kaipai.model.system.entity.AdminOperationLog;
import com.kaipai.service.system.AdminOperationLogService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/** Real mapper, transaction and repeatable-DDL gate against disposable MySQL only. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActorCardDecorationAssignmentMySqlIntegrationTest {
    private static DataSource database;
    private static final AtomicBoolean FAIL_ADMIN_AUDIT = new AtomicBoolean();
    private static final AtomicBoolean FAIL_ASSIGNMENT_AUDIT = new AtomicBoolean();
    private static final AtomicBoolean COORDINATE_EMPTY_ASSIGNMENT = new AtomicBoolean();
    private static final AtomicBoolean COORDINATE_EMPTY_BINDING = new AtomicBoolean();
    private static volatile CountDownLatch emptyAssignmentReaders;
    private static volatile CountDownLatch emptyBindingReaders;
    private MySQLContainer<?> mysql;
    private AnnotationConfigApplicationContext context;
    private ActorCardDecorationAssignmentService service;
    private JdbcTemplate jdbc;

    @BeforeAll
    void start() throws Exception {
        assertTrue(DockerClientFactory.instance().isDockerAvailable(),
                "isolated Docker MySQL required; this integration gate must not silently skip");
        mysql = new MySQLContainer<>("mysql:8.0.36").withDatabaseName("decoration_assignment_isolated_test");
        mysql.start();
        database = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE card_scene_template (template_id BIGINT PRIMARY KEY, deleted INT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE actor_card (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, deleted INT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE actor_card_background (id BIGINT PRIMARY KEY, enabled INT NOT NULL)");
        jdbc.execute("CREATE TABLE decoration_assignment_test_admin_audit (id BIGINT AUTO_INCREMENT PRIMARY KEY, target_id BIGINT)");
        jdbc.update("INSERT INTO card_scene_template(template_id) VALUES (1), (2)");
        jdbc.update("INSERT INTO actor_card(id,user_id) VALUES (11,7), (12,7), (13,8)");
        jdbc.update("INSERT INTO actor_card_background(id,enabled) VALUES (55,1), (56,0)");
        migrate("db/migration/V20261022_001__actor_card_decoration_versions.sql");
        migrate("db/migration/V20261022_002__actor_card_decoration_assignments.sql");
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(ActorCardDecorationAssignmentService.class);
        assertTrue(AopUtils.isAopProxy(service));
    }

    @AfterAll
    void stop() {
        if (context != null) context.close();
        if (mysql != null) mysql.stop();
    }

    @BeforeEach
    void reset() {
        FAIL_ADMIN_AUDIT.set(false);
        FAIL_ASSIGNMENT_AUDIT.set(false);
        COORDINATE_EMPTY_ASSIGNMENT.set(false);
        COORDINATE_EMPTY_BINDING.set(false);
        emptyAssignmentReaders = null;
        emptyBindingReaders = null;
        jdbc.update("DELETE FROM actor_card_decoration_assignment_audit");
        jdbc.update("DELETE FROM actor_card_decoration_assignment");
        jdbc.update("DELETE FROM actor_card_decoration_binding");
        jdbc.update("DELETE FROM actor_card_decoration_version");
        jdbc.update("DELETE FROM decoration_assignment_test_admin_audit");
        insertVersion(101L, 1L, 1, 16);
        insertVersion(202L, 2L, 3, 40);
    }

    @Test
    void migrationCanRunTwiceWithoutChangingBindingsAssignmentsOrAudits() throws Exception {
        service.saveDefaultBinding(new BindingSaveReq(0L, 1L, 101L, "默认"));
        service.selectForMode(7L, 11L, "person_ai", null);
        int beforeAudit = jdbc.queryForObject("SELECT COUNT(*) FROM actor_card_decoration_assignment_audit", Integer.class);

        migrate("db/migration/V20261022_002__actor_card_decoration_assignments.sql");
        migrate("db/migration/V20261022_002__actor_card_decoration_assignments.sql");

        assertEquals(101L, service.resolve(11L).versionId());
        assertEquals(1L, service.getDefaultBinding().revision());
        assertEquals(beforeAudit, jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_card_decoration_assignment_audit", Integer.class));
    }

    @Test
    void realSqlLocksExistingCardWhileNewCardUsesTheLaterDefaultBinding() {
        var first = service.saveDefaultBinding(new BindingSaveReq(0L, 1L, 101L, "v1"));
        assertEquals(101L, service.selectForMode(7L, 11L, "person_ai", null).versionId());
        service.saveDefaultBinding(new BindingSaveReq(first.revision(), 2L, 202L, "v2"));

        assertEquals(101L, service.selectForMode(7L, 11L, "person_ai", null).versionId());
        assertEquals(202L, service.selectForMode(7L, 12L, "person_ai", null).versionId());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM actor_card_decoration_assignment_audit", Integer.class));
    }

    @Test
    void concurrentFirstAssignmentSerializesOnTheOwnedCardWithoutDeadlock() throws Exception {
        service.saveDefaultBinding(new BindingSaveReq(0L, 1L, 101L, "默认"));
        emptyAssignmentReaders = new CountDownLatch(2);
        COORDINATE_EMPTY_ASSIGNMENT.set(true);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Long> first = workers.submit(() -> {
                start.await();
                return service.selectForMode(7L, 11L, "person_ai", null).versionId();
            });
            Future<Long> second = workers.submit(() -> {
                start.await();
                return service.selectForMode(7L, 11L, "person_ai", null).versionId();
            });
            start.countDown();

            assertEquals(101L, first.get(10, TimeUnit.SECONDS));
            assertEquals(101L, second.get(10, TimeUnit.SECONDS));
        } finally {
            COORDINATE_EMPTY_ASSIGNMENT.set(false);
            workers.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_card_decoration_assignment WHERE card_id=11", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_card_decoration_assignment_audit WHERE card_id=11", Integer.class));
    }

    @Test
    void concurrentFirstDefaultBindingReturnsOneSuccessAndOneConflictWithoutDeadlock() throws Exception {
        emptyBindingReaders = new CountDownLatch(2);
        COORDINATE_EMPTY_BINDING.set(true);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Integer> outcomes = Collections.synchronizedList(new ArrayList<>());
            Future<?> first = workers.submit(() -> saveInitialDefaultBinding(start, outcomes));
            Future<?> second = workers.submit(() -> saveInitialDefaultBinding(start, outcomes));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            assertEquals(List.of(200, 409), outcomes.stream().sorted().toList());
        } finally {
            COORDINATE_EMPTY_BINDING.set(false);
            workers.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_card_decoration_binding WHERE source_type='default' AND source_id=0",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM decoration_assignment_test_admin_audit", Integer.class));
    }

    @Test
    void ownerUpgradeIsCasAndARepeatedStaleConfirmationCannotOverwrite() {
        var first = service.saveDefaultBinding(new BindingSaveReq(0L, 1L, 101L, "v1"));
        service.selectForMode(7L, 11L, "person_ai", null);
        service.saveDefaultBinding(new BindingSaveReq(first.revision(), 2L, 202L, "v2"));
        var preview = service.previewUpgrade(7L, 11L);

        assertEquals(101L, preview.expectedVersionId());
        assertTrue(preview.upgradeAvailable());
        assertEquals(202L, service.confirmUpgrade(7L, 11L, new UpgradeReq(101L, 202L)).versionId());
        assertEquals(409, assertThrows(BizException.class,
                () -> service.confirmUpgrade(7L, 11L, new UpgradeReq(101L, 202L))).getCode());
        assertEquals(202L, service.resolve(11L).versionId());
    }

    @Test
    void requiredAdminAuditFailureRollsBackTheBindingMutation() {
        FAIL_ADMIN_AUDIT.set(true);
        assertThrows(IllegalStateException.class,
                () -> service.saveDefaultBinding(new BindingSaveReq(0L, 1L, 101L, "不能落库")));
        assertEquals(0, service.getDefaultBinding().revision());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM actor_card_decoration_binding", Integer.class));
    }

    @Test
    void requiredAssignmentAuditFailureRollsBackTheCardLock() {
        service.saveDefaultBinding(new BindingSaveReq(0L, 1L, 101L, "默认"));
        FAIL_ASSIGNMENT_AUDIT.set(true);
        try {
            assertThrows(BizException.class,
                    () -> service.selectForMode(7L, 11L, "person_ai", null));
        } finally {
            FAIL_ASSIGNMENT_AUDIT.set(false);
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM actor_card_decoration_assignment", Integer.class));
        assertNull(service.resolve(11L));
    }

    @Test
    void ownershipAndEnabledBackgroundAreEnforcedByRealQueries() {
        service.saveDefaultBinding(new BindingSaveReq(0L, 1L, 101L, "默认"));
        assertEquals(404, assertThrows(BizException.class,
                () -> service.selectForMode(7L, 13L, "person_ai", null)).getCode());
        assertEquals(404, assertThrows(BizException.class,
                () -> service.saveBackgroundBinding(56L,
                        new BindingSaveReq(0L, 1L, 101L, "禁用背景"))).getCode());
    }

    @Test
    void backgroundReadinessUsesRealBatchQueriesWithoutMutatingCards() {
        jdbc.update("INSERT INTO card_scene_template(template_id,deleted) VALUES (3,1) "
                + "ON DUPLICATE KEY UPDATE deleted=VALUES(deleted)");
        jdbc.update("INSERT INTO actor_card_background(id,enabled) VALUES (57,1) "
                + "ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)");
        insertVersion(303L, 3L, 1, 24);
        jdbc.update("""
                INSERT INTO actor_card_decoration_binding
                  (source_type, source_id, revision, template_id, version_id, template_version, updated_by)
                VALUES ('background',55,1,1,101,1,8), ('background',57,1,3,303,1,8)
                """);
        var cardsBefore = jdbc.queryForList("SELECT * FROM actor_card ORDER BY id");

        var readiness = service.backgroundReadiness(List.of(55L, 56L, 57L));

        assertEquals(List.of(55L, 56L, 57L), readiness.keySet().stream().toList());
        assertTrue(readiness.get(55L).ready());
        assertNull(readiness.get(55L).unavailableReason());
        assertFalse(readiness.get(56L).ready());
        assertEquals("当前背景尚未绑定已发布装修版本", readiness.get(56L).unavailableReason());
        assertFalse(readiness.get(57L).ready());
        assertEquals("当前背景绑定的装修版本不可用", readiness.get(57L).unavailableReason());
        assertEquals(cardsBefore, jdbc.queryForList("SELECT * FROM actor_card ORDER BY id"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM actor_card_decoration_assignment", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_card_decoration_assignment_audit", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM decoration_assignment_test_admin_audit", Integer.class));
    }

    private void insertVersion(Long id, Long templateId, int versionNo, int radius) {
        jdbc.update("""
                INSERT INTO actor_card_decoration_version
                  (id, template_id, version_no, schema_json, checksum, published_by, publish_note)
                VALUES (?, ?, ?, ?, REPEAT('a',64), 8, 'test')
                """, id, templateId, versionNo,
                "{\"schemaVersion\":1,\"defaults\":{\"radius\":" + radius
                        + "},\"modules\":[{\"id\":\"photos\",\"type\":\"photos\"}]}");
    }

    private void saveInitialDefaultBinding(CountDownLatch start, List<Integer> outcomes) {
        try {
            start.await();
            service.saveDefaultBinding(new BindingSaveReq(0L, 1L, 101L, "并发初始化"));
            outcomes.add(200);
        } catch (BizException error) {
            outcomes.add(error.getCode());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private void migrate(String resource) throws Exception {
        try (var connection = database.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(resource));
        }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() { return database; }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
            bean.setDataSource(ds);
            var configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(ActorCardDecorationAssignmentMapper.class);
            bean.setConfiguration(configuration);
            return bean.getObject();
        }
        @Bean ActorCardDecorationAssignmentMapper mapper(SqlSessionFactory factory) {
            ActorCardDecorationAssignmentMapper real =
                    new SqlSessionTemplate(factory).getMapper(ActorCardDecorationAssignmentMapper.class);
            return (ActorCardDecorationAssignmentMapper) Proxy.newProxyInstance(
                    ActorCardDecorationAssignmentMapper.class.getClassLoader(),
                    new Class<?>[] {ActorCardDecorationAssignmentMapper.class},
                    (proxy, method, args) -> {
                        if ("insertAssignmentAudit".equals(method.getName())
                                && FAIL_ASSIGNMENT_AUDIT.get()) {
                            return 0;
                        }
                        try {
                            Object result = method.invoke(real, args);
                            if ("assignmentForUpdate".equals(method.getName())
                                    && result == null
                                    && COORDINATE_EMPTY_ASSIGNMENT.get()) {
                                awaitCompetingEmptyRead(emptyAssignmentReaders);
                            }
                            if ("bindingForUpdate".equals(method.getName())
                                    && result == null
                                    && COORDINATE_EMPTY_BINDING.get()) {
                                awaitCompetingEmptyRead(emptyBindingReaders);
                            }
                            return result;
                        } catch (InvocationTargetException ex) {
                            throw ex.getCause();
                        }
                    });
        }

        private static void awaitCompetingEmptyRead(CountDownLatch readers) {
            if (readers == null) {
                return;
            }
            readers.countDown();
            try {
                readers.await(1500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        }
        @Bean ObjectMapper json() { return new ObjectMapper(); }
        @Bean AdminAuthContext auth() {
            AdminAuthContext auth = mock(AdminAuthContext.class);
            when(auth.getCurrentAdminUserId()).thenReturn(8L);
            return auth;
        }
        @Bean AdminOperationLogService auditStore(DataSource ds) {
            AdminOperationLogService store = mock(AdminOperationLogService.class);
            when(store.save(any(AdminOperationLog.class))).thenAnswer(invocation -> {
                new JdbcTemplate(ds).update("INSERT INTO decoration_assignment_test_admin_audit(target_id) VALUES (?)",
                        ((AdminOperationLog) invocation.getArgument(0)).getTargetId());
                return !FAIL_ADMIN_AUDIT.get();
            });
            return store;
        }
        @Bean AdminOperationLogger audit(AdminOperationLogService store, AdminAuthContext auth, ObjectMapper json) {
            return new AdminOperationLogger(store, auth, json);
        }
        @Bean ActorCardDecorationAssignmentService service(ActorCardDecorationAssignmentMapper mapper,
                                                           ObjectMapper json, AdminAuthContext auth,
                                                           AdminOperationLogger audit) {
            return new ActorCardDecorationAssignmentService(mapper, json, auth, audit);
        }
    }
}
