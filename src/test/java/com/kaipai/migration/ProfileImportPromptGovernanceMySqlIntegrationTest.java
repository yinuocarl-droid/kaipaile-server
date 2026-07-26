package com.kaipai.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.handler.MetaObjectHandlerConfig;
import com.kaipai.mapper.ai.AiProfileImportConfigMapper;
import com.kaipai.mapper.ai.AiProfileImportConfigAuditMapper;
import com.kaipai.mapper.ai.AiProfileImportPromptAuditMapper;
import com.kaipai.mapper.ai.AiProfileImportRequestAuditMapper;
import com.kaipai.mapper.ai.AiProfileImportPromptTemplateMapper;
import com.kaipai.mapper.ai.AiProfileImportPromptVersionMapper;
import com.kaipai.mapper.system.AdminOperationLogMapper;
import com.kaipai.model.ai.dto.ProfileImportPromptCreateDraftReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptRestoreReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptTestResultRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptUpdateDraftReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptVersionActionReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPublicConfigUpdateDTO;
import com.kaipai.model.ai.entity.AiProfileImportPromptAudit;
import com.kaipai.model.ai.entity.AiProfileImportRequestAudit;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import com.kaipai.model.system.entity.AdminOperationLog;
import com.kaipai.service.ai.AiProviderSecretCryptoService;
import com.kaipai.service.ai.ProfileImportConfigService;
import com.kaipai.service.ai.ProfileImportConnectionTester;
import com.kaipai.service.ai.ProfileImportPromptManagementService;
import com.kaipai.service.ai.ProfileImportPromptRuntimeResolver;
import com.kaipai.service.ai.ProfileImportPromptTester;
import com.kaipai.service.ai.ProfileImportRuntimeConfig;
import com.kaipai.service.ai.impl.AiProviderSecretCryptoServiceImpl;
import com.kaipai.service.ai.impl.ProfileImportConfigServiceImpl;
import com.kaipai.service.ai.impl.ProfileImportPromptManagementServiceImpl;
import com.kaipai.service.ai.impl.ProfileImportPromptRuntimeResolverImpl;
import com.kaipai.service.ai.profileimport.ProfileImportPromptContract;
import com.kaipai.service.ai.profileimport.ProfileImportPromptFixtureCatalog;
import com.kaipai.service.ai.profileimport.ProfileImportPromptPolicy;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRenderer;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRuntime;
import com.kaipai.service.system.AdminOperationLogService;
import com.kaipai.service.system.impl.AdminOperationLogServiceImpl;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringJUnitConfig(ProfileImportPromptGovernanceMySqlIntegrationTest.TestConfiguration.class)
@TestPropertySource(properties =
        "AI_PROVIDER_CONFIG_MASTER_KEY="
                + "7d9b5cb04627cf7af7f880cc452eea634e674fc1bfcbe11f27d9d9a9b0ae3a61")
class ProfileImportPromptGovernanceMySqlIntegrationTest {

    private static final String V001 =
            "V20260726_001__ai_profile_import_prompt_template_governance.sql";
    private static final String V002 =
            "V20260726_002__ai_profile_import_prompt_permission_alignment.sql";
    private static final String TEST_API_KEY = "test-only-deepseek-api-key";
    private static final String CONTENT_HASH_DOMAIN = "profile-import-prompt-content-v1";
    private static final String PROMPT_BODY_SENTINEL = "PRIVATE_PROMPT_BODY_SENTINEL_200";
    private static final String CHANGE_SUMMARY_SENTINEL = "PRIVATE_CHANGE_SUMMARY_SENTINEL_200";
    private static final String USER_RAW_SENTINEL = "PRIVATE_USER_RAW_SENTINEL_200";
    private static final String MODEL_RESPONSE_SENTINEL = "PRIVATE_MODEL_RESPONSE_SENTINEL_200";
    private static final String FREE_REASON_SENTINEL = "PRIVATE_FREE_REASON_SENTINEL_200";
    private static final List<String> PROMPT_PERMISSIONS = List.of(
            "action.system.ai-profile-import.template-read",
            "action.system.ai-profile-import.template-update",
            "action.system.ai-profile-import.template-test",
            "action.system.ai-profile-import.template-publish",
            "action.system.ai-profile-import.template-restore");
    private static final String[] MIGRATIONS = {
        "V20260331_001__platform_admin_baseline.sql",
        "V20260331_002__platform_admin_governance_alignment.sql",
        "V20260723_004__ai_profile_import_governance.sql",
        "V20260724_001__ai_profile_import_request_scene.sql",
        V001
    };
    private static final PromptGovernanceDatabase DATABASE = startDatabase();

    private final JdbcTemplate jdbc;
    private final JdbcTemplate lockObserverJdbc;
    private final AiProfileImportPromptTemplateMapper templateMapper;
    private final AiProfileImportPromptVersionMapper versionMapper;
    private final AiProfileImportConfigMapper configMapper;
    private final ProfileImportPromptRenderer renderer;
    private final ProfileImportPromptManagementService managementService;
    private final ProfileImportConfigService configService;
    private final AiProviderSecretCryptoService crypto;
    private final PromptGovernanceFaults faults;
    private final ControlledFixturePromptTester tester;
    private final TransactionTemplate transactionTemplate;
    private final ProfileImportPromptRuntimeResolver runtimeResolver;
    private final AiProfileImportRequestAuditMapper requestAuditMapper;
    private final ProfileImportPromptFixtureCatalog fixtureCatalog;
    private final ObjectMapper objectMapper;

    @Autowired
    ProfileImportPromptGovernanceMySqlIntegrationTest(
            @Qualifier("jdbcTemplate") JdbcTemplate jdbc,
            @Qualifier("lockObserverJdbcTemplate") JdbcTemplate lockObserverJdbc,
            AiProfileImportPromptTemplateMapper templateMapper,
            AiProfileImportPromptVersionMapper versionMapper,
            AiProfileImportConfigMapper configMapper,
            ProfileImportPromptRenderer renderer,
            ProfileImportPromptManagementService managementService,
            ProfileImportConfigService configService,
            AiProviderSecretCryptoService crypto,
            PromptGovernanceFaults faults,
            ControlledFixturePromptTester tester,
            TransactionTemplate transactionTemplate,
            ProfileImportPromptRuntimeResolver runtimeResolver,
            AiProfileImportRequestAuditMapper requestAuditMapper,
            ProfileImportPromptFixtureCatalog fixtureCatalog,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.lockObserverJdbc = lockObserverJdbc;
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.configMapper = configMapper;
        this.renderer = renderer;
        this.managementService = managementService;
        this.configService = configService;
        this.crypto = crypto;
        this.faults = faults;
        this.tester = tester;
        this.transactionTemplate = transactionTemplate;
        this.runtimeResolver = runtimeResolver;
        this.requestAuditMapper = requestAuditMapper;
        this.fixtureCatalog = fixtureCatalog;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void resetBootstrapAndConfigFixtures() throws Exception {
        SecurityContextHolder.clearContext();
        faults.reset();
        tester.reset();
        jdbc.execute("DROP TABLE IF EXISTS assert_ai_profile_import_prompt_bootstrap");
        jdbc.update("DELETE FROM admin_operation_log");
        jdbc.update("DELETE FROM ai_profile_import_prompt_audit");
        jdbc.update("DELETE FROM ai_profile_import_request_audit");
        jdbc.update("DELETE FROM ai_profile_import_config_audit");
        jdbc.update("UPDATE ai_profile_import_prompt_template "
                + "SET active_version_id=NULL, draft_version_id=NULL");
        jdbc.update("DELETE FROM ai_profile_import_prompt_version");
        jdbc.update("DELETE FROM ai_profile_import_prompt_template");
        jdbc.update("DELETE FROM ai_profile_import_config WHERE provider_code='deepseek'");
        executeSql(bootstrapBlock(v001Sql()));
        String ciphertext = crypto.encrypt("{\"apiKey\":\"" + TEST_API_KEY + "\"}");
        jdbc.update("INSERT INTO ai_profile_import_config "
                + "(provider_code, display_name, enabled, endpoint, model_name, "
                + "connect_timeout_ms, read_timeout_ms, max_input_chars, max_output_tokens, "
                + "per_user_daily_limit, secret_config_ciphertext, secret_mask_json, "
                + "last_test_status, last_test_message, last_test_at, version) VALUES "
                + "('deepseek', 'DeepSeek test', 1, "
                + "'https://api.deepseek.com/chat/completions', 'deepseek-chat', "
                + "5000, 60000, 20000, 8000, 20, ?, '****-key', "
                + "'success', 'fixture ready', CURRENT_TIMESTAMP, 17)",
                ciphertext);
        installAdmin(73L);
    }

    @AfterEach
    void clearThreadSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @Test
    void bootstrapDraftCannotBeAbandonedUntilAnActiveReleaseExists() {
        assertTrue(AopUtils.isAopProxy(managementService));
        assertTrue(AopUtils.isAopProxy(configService));
        AiProfileImportPromptTemplate template = loadTemplate("full_profile");
        AiProfileImportPromptVersion draft = loadVersion(
                template.getTemplateId(), template.getDraftVersionId());

        BizException error = assertThrows(BizException.class,
                () -> managementService.abandonDraft(
                        73L,
                        draft.getPromptVersionId(),
                        actionRequest("DRAFT_INVALID", template, draft)));

        assertEquals(46022, error.getCode());
        AiProfileImportPromptTemplate stored = loadTemplate("full_profile");
        AiProfileImportPromptVersion storedDraft = loadVersion(
                stored.getTemplateId(), stored.getDraftVersionId());
        assertNull(stored.getActiveVersionId());
        assertEquals(draft.getPromptVersionId(), stored.getDraftVersionId());
        assertEquals("draft", storedDraft.getLifecycleStatus());
        assertEquals(0, count("ai_profile_import_prompt_audit"));
        assertEquals(0, count("admin_operation_log"));
    }

    @Test
    void publishPersistsImmutableBindingAndMovesBothPointersAtomically() {
        TestedDraft fixture = successfullyTestDraft("full_profile");
        AiProfileImportPromptVersion before = fixture.version();
        String systemBody = before.getSystemPromptBody();
        String repairBody = before.getRepairPromptBody();
        String schemaVersion = before.getSchemaVersion();
        String contractVersion = before.getContractVersion();
        ExpectedTestBinding expected = expectedTestBinding(fixture.template(), before);

        managementService.publish(
                73L,
                before.getPromptVersionId(),
                actionRequest("INITIAL_RELEASE", fixture.template(), before));

        AiProfileImportPromptTemplate storedTemplate = loadTemplate("full_profile");
        AiProfileImportPromptVersion released = loadVersion(
                storedTemplate.getTemplateId(), storedTemplate.getActiveVersionId());
        assertEquals(before.getPromptVersionId(), storedTemplate.getActiveVersionId());
        assertNull(storedTemplate.getDraftVersionId());
        assertEquals("released", released.getLifecycleStatus());
        assertEquals(systemBody, released.getSystemPromptBody());
        assertEquals(repairBody, released.getRepairPromptBody());
        assertEquals(schemaVersion, released.getSchemaVersion());
        assertEquals(contractVersion, released.getContractVersion());
        assertEquals(expected.contentSha256(), released.getContentSha256());
        assertEquals(expected.contentSha256(), released.getTestedContentSha256());
        assertEquals(expected.runtimeSha256(), released.getTestedRuntimeSha256());
        assertEquals(expected.fixtureCode(), released.getTestFixtureCode());
        assertEquals(expected.fixtureVersion(), released.getTestFixtureVersion());
        assertEquals(expected.fixtureSha256(), released.getTestFixtureSha256());
        assertEquals(expected.modelName(), released.getTestedModelName());
        assertEquals(expected.configVersion(), released.getTestedConfigVersion());
        assertEquals(73L, released.getReleasedBy());
        assertNotNull(released.getReleasedAt());

        Map<String, Object> audit = jdbc.queryForMap(
                "SELECT content_sha256, runtime_sha256, schema_version, contract_version, "
                        + "fixture_code, fixture_version, fixture_sha256, model_name, "
                        + "config_version, test_operator_id, tested_at, reason_code "
                        + "FROM ai_profile_import_prompt_audit WHERE action_code='publish'");
        assertEquals(expected.contentSha256(), audit.get("content_sha256"));
        assertEquals(expected.runtimeSha256(), audit.get("runtime_sha256"));
        assertEquals(schemaVersion, audit.get("schema_version"));
        assertEquals(contractVersion, audit.get("contract_version"));
        assertEquals(expected.fixtureCode(), audit.get("fixture_code"));
        assertEquals(expected.fixtureVersion(), audit.get("fixture_version"));
        assertEquals(expected.fixtureSha256(), audit.get("fixture_sha256"));
        assertEquals(expected.modelName(), audit.get("model_name"));
        assertEquals(expected.configVersion(), audit.get("config_version"));
        assertEquals(73L, audit.get("test_operator_id"));
        assertNotNull(audit.get("tested_at"));
        assertEquals("INITIAL_RELEASE", audit.get("reason_code"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log "
                        + "WHERE module_code='ai-profile-import' "
                        + "AND operation_code='prompt-publish'",
                Integer.class));
    }

    @Test
    void specializedAuditInsertZeroRollsBackReleaseAndPointers() {
        TestedDraft fixture = successfullyTestDraft("full_profile");
        int auditCountBefore = count("ai_profile_import_prompt_audit");
        faults.promptAuditMode.set(FaultMode.RETURN_ZERO);

        assertThrows(IllegalStateException.class,
                () -> managementService.publish(
                        73L,
                        fixture.version().getPromptVersionId(),
                        actionRequest("INITIAL_RELEASE", fixture.template(), fixture.version())));

        assertDraftStillPublishable(fixture, auditCountBefore);
        assertEquals(0, count("admin_operation_log"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit WHERE action_code='publish'",
                Integer.class));

        faults.reset();
        PublishedHistory restoreHistory = publishV1AndV2(
                "works_only", "\n恢复专用审计故障夹具。", "sanitized restore audit fault");
        assertRestoreFaultRollsBack(
                restoreHistory, FaultTarget.PROMPT_AUDIT, FaultMode.RETURN_ZERO);
        assertRestoreFaultRollsBack(
                restoreHistory, FaultTarget.PROMPT_AUDIT, FaultMode.THROW);
    }

    @Test
    void requiredGlobalAuditFalseRollsBackReleaseAndPointers() {
        TestedDraft fixture = successfullyTestDraft("full_profile");
        int auditCountBefore = count("ai_profile_import_prompt_audit");
        faults.adminLogMode.set(FaultMode.RETURN_ZERO);

        assertThrows(IllegalStateException.class,
                () -> managementService.publish(
                        73L,
                        fixture.version().getPromptVersionId(),
                        actionRequest("INITIAL_RELEASE", fixture.template(), fixture.version())));

        assertDraftStillPublishable(fixture, auditCountBefore);
        assertEquals(0, count("admin_operation_log"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit WHERE action_code='publish'",
                Integer.class));

        faults.reset();
        TestedDraft throwingFixture = successfullyTestDraft("works_only");
        int auditCountBeforeThrow = count("ai_profile_import_prompt_audit");
        faults.adminLogMode.set(FaultMode.THROW);
        assertThrows(IllegalStateException.class,
                () -> managementService.publish(
                        73L,
                        throwingFixture.version().getPromptVersionId(),
                        actionRequest(
                                "INITIAL_RELEASE",
                                throwingFixture.template(),
                                throwingFixture.version())));
        assertDraftStillPublishable(throwingFixture, auditCountBeforeThrow);
        assertEquals(0, count("admin_operation_log"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit WHERE action_code='publish'",
                Integer.class));

        faults.reset();
        PublishedHistory restoreHistory = publishV1AndV2(
                "full_profile", "\n恢复全局日志故障夹具。", "sanitized restore log fault");
        assertRestoreFaultRollsBack(
                restoreHistory, FaultTarget.ADMIN_LOG, FaultMode.RETURN_ZERO);
        assertRestoreFaultRollsBack(
                restoreHistory, FaultTarget.ADMIN_LOG, FaultMode.THROW);
    }

    @Test
    void draftUpdateAuditZeroOrThrowRollsBackBodyAndHash() {
        assertDraftUpdateAuditFailureRollsBack("full_profile", FaultMode.RETURN_ZERO);
        faults.reset();
        assertDraftUpdateAuditFailureRollsBack("works_only", FaultMode.THROW);
    }

    @Test
    void twoAdministratorsPublishingTheSameDraftHaveOneWinner() throws Exception {
        TestedDraft fixture = successfullyTestDraft("full_profile");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PublishOutcome> first = executor.submit(() -> publishAsAdmin(
                    73L, fixture, ready, start));
            Future<PublishOutcome> second = executor.submit(() -> publishAsAdmin(
                    74L, fixture, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<PublishOutcome> outcomes = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(PublishOutcome::success).count());
            PublishOutcome loser = outcomes.stream()
                    .filter(outcome -> !outcome.success())
                    .findFirst()
                    .orElseThrow();
            assertTrue(Set.of(46018, 46022).contains(loser.errorCode()));
        } finally {
            start.countDown();
            shutdown(executor);
        }

        AiProfileImportPromptTemplate stored = loadTemplate("full_profile");
        assertEquals(fixture.version().getPromptVersionId(), stored.getActiveVersionId());
        assertNull(stored.getDraftVersionId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_version "
                        + "WHERE template_id=? AND lifecycle_status='released' AND deleted=0",
                Integer.class,
                stored.getTemplateId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit WHERE action_code='publish'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log "
                        + "WHERE operation_code='prompt-publish'",
                Integer.class));
    }

    @Test
    void concurrentDraftSaveMakesTheOldTestUnpublishable() throws Exception {
        TestedDraft fixture = successfullyTestDraft("full_profile");
        ProfileImportPromptUpdateDraftReqDTO update = editedDraftRequest(fixture.version());
        CountDownLatch saveApplied = new CountDownLatch(1);
        CountDownLatch allowSaveCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> save = executor.submit(() -> {
                installAdmin(74L);
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        managementService.updateDraft(
                                74L, fixture.version().getPromptVersionId(), update);
                        saveApplied.countDown();
                        awaitLatch(allowSaveCommit);
                    });
                    return null;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertTrue(saveApplied.await(10, TimeUnit.SECONDS));
            Future<PublishOutcome> publish = executor.submit(() -> {
                installAdmin(73L);
                try {
                    return publishOutcome(73L, fixture);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            LockWaitObservation lockWait = awaitMySqlLockWait(
                    "ai_profile_import_prompt_template");
            assertEquals("LOCK WAIT", lockWait.trxState());
            assertEquals("kaipai_test", lockWait.processlistUser());
            assertTrue(lockWait.trxQuery().toLowerCase()
                    .contains("ai_profile_import_prompt_template"));
            allowSaveCommit.countDown();
            save.get(10, TimeUnit.SECONDS);
            PublishOutcome outcome = publish.get(10, TimeUnit.SECONDS);
            assertFalse(outcome.success());
            assertEquals(46018, outcome.errorCode());
        } finally {
            allowSaveCommit.countDown();
            shutdown(executor);
        }

        AiProfileImportPromptTemplate storedTemplate = loadTemplate("full_profile");
        AiProfileImportPromptVersion stored = loadVersion(
                storedTemplate.getTemplateId(), storedTemplate.getDraftVersionId());
        assertNull(storedTemplate.getActiveVersionId());
        assertEquals(update.getSystemPromptBody(), stored.getSystemPromptBody());
        assertEquals("stale", stored.getTestStatus());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit "
                        + "WHERE action_code='draft_update'",
                Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit WHERE action_code='publish'",
                Integer.class));
    }

    @Test
    void concurrentTestWritebackCannotOverwriteChangedContent() throws Exception {
        AiProfileImportPromptTemplate template = loadTemplate("full_profile");
        AiProfileImportPromptVersion draft = loadVersion(
                template.getTemplateId(), template.getDraftVersionId());
        ProfileImportPromptUpdateDraftReqDTO update = editedDraftRequest(draft);
        TestGate gate = tester.pauseNextExecution();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ProfileImportPromptTestResultRespDTO> test = executor.submit(() -> {
            installAdmin(73L);
            try {
                return managementService.test(73L, draft.getPromptVersionId());
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        try {
            assertTrue(gate.entered().await(10, TimeUnit.SECONDS));
            managementService.updateDraft(73L, draft.getPromptVersionId(), update);
            gate.release().countDown();
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> test.get(10, TimeUnit.SECONDS));
            assertEquals(46021, bizCode(failure.getCause()));
        } finally {
            gate.release().countDown();
            shutdown(executor);
        }

        AiProfileImportPromptVersion stored = loadVersion(
                template.getTemplateId(), draft.getPromptVersionId());
        assertEquals(update.getSystemPromptBody(), stored.getSystemPromptBody());
        assertEquals("stale", stored.getTestStatus());
        assertNull(stored.getTestedRuntimeSha256());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit WHERE action_code='test'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit "
                        + "WHERE action_code='draft_update'",
                Integer.class));
    }

    @Test
    void concurrentConfigUpdateMakesTheOldBindingUnpublishable() throws Exception {
        TestedDraft fixture = successfullyTestDraft("full_profile");
        ProfileImportPublicConfigUpdateDTO update = publicConfigUpdate("deepseek-chat-v18");
        CountDownLatch configApplied = new CountDownLatch(1);
        CountDownLatch allowConfigCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> config = executor.submit(() -> {
                installAdmin(74L);
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        configService.savePublicConfig(74L, update);
                        configApplied.countDown();
                        awaitLatch(allowConfigCommit);
                    });
                    return null;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertTrue(configApplied.await(10, TimeUnit.SECONDS));
            Future<PublishOutcome> publish = executor.submit(() -> {
                installAdmin(73L);
                try {
                    return publishOutcome(73L, fixture);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            LockWaitObservation lockWait = awaitMySqlLockWait(
                    "ai_profile_import_config");
            assertEquals("LOCK WAIT", lockWait.trxState());
            assertEquals("kaipai_test", lockWait.processlistUser());
            assertTrue(lockWait.trxQuery().toLowerCase()
                    .contains("ai_profile_import_config"));
            allowConfigCommit.countDown();
            config.get(10, TimeUnit.SECONDS);
            PublishOutcome outcome = publish.get(10, TimeUnit.SECONDS);
            assertFalse(outcome.success());
            assertTrue(Set.of(46018, 46021, 46022).contains(outcome.errorCode()));
        } finally {
            allowConfigCommit.countDown();
            shutdown(executor);
        }

        Map<String, Object> config = jdbc.queryForMap(
                "SELECT model_name, enabled, last_test_status, version "
                        + "FROM ai_profile_import_config WHERE provider_code='deepseek'");
        assertEquals("deepseek-chat-v18", config.get("model_name"));
        assertEquals(0, ((Number) config.get("enabled")).intValue());
        assertEquals(18, config.get("version"));
        assertDraftStillPublishable(fixture, 1);
        assertEquals(1, count("ai_profile_import_config_audit"));
        assertEquals(0, count("admin_operation_log"));
    }

    @Test
    void releasedRetestDoesNotChangeOriginalPublishAudit() {
        TestedDraft fixture = successfullyTestDraft("full_profile");
        managementService.publish(
                73L,
                fixture.version().getPromptVersionId(),
                actionRequest("INITIAL_RELEASE", fixture.template(), fixture.version()));
        AiProfileImportPromptVersion released = loadVersion(
                fixture.template().getTemplateId(), fixture.version().getPromptVersionId());
        int releasedVersionBeforeRetest = released.getVersion();
        Map<String, Object> publishBefore = publishAuditSnapshot();

        ProfileImportPromptTestResultRespDTO retested = managementService.test(
                73L, released.getPromptVersionId());

        AiProfileImportPromptVersion refreshed = loadVersion(
                fixture.template().getTemplateId(), released.getPromptVersionId());
        assertEquals("success", retested.getStatus());
        assertEquals(releasedVersionBeforeRetest + 1, refreshed.getVersion());
        assertEquals("released", refreshed.getLifecycleStatus());
        assertEquals(publishBefore, publishAuditSnapshot());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit WHERE action_code='publish'",
                Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit WHERE action_code='test'",
                Integer.class));
    }

    @Test
    void restoreV2ToV1ChangesNewResolutionAndPreservesOldRequestLineage() {
        PublishedHistory history = publishV1AndV2(
                "full_profile", "\n版本二质量调整。", "sanitized v2 adjustment");
        ProfileImportPromptRuntime resolvedV2 = runtimeResolver.resolve("full_profile");
        assertEquals(history.v2().getPromptVersionId(), resolvedV2.promptVersionId());
        persistRequestAudit("before-restore-v2", 901L, resolvedV2);

        AiProfileImportPromptTemplate beforeRestore = loadTemplate("full_profile");
        managementService.restore(
                73L,
                "full_profile",
                history.v1().getPromptVersionId(),
                restoreRequest("QUALITY_REGRESSION", beforeRestore));

        ProfileImportPromptRuntime resolvedV1 = runtimeResolver.resolve("full_profile");
        assertEquals(history.v1().getPromptVersionId(), resolvedV1.promptVersionId());
        assertEquals(1, resolvedV1.versionNo());
        persistRequestAudit("after-restore-v1", 901L, resolvedV1);

        Map<String, Object> oldLineage = requestLineage("before-restore-v2");
        Map<String, Object> newLineage = requestLineage("after-restore-v1");
        assertLineageEquals(resolvedV2, oldLineage);
        assertLineageEquals(resolvedV1, newLineage);
        assertEquals(history.v2().getPromptVersionId(), oldLineage.get("prompt_version_id"));
        assertEquals(history.v1().getPromptVersionId(), newLineage.get("prompt_version_id"));

        AiProfileImportPromptTemplate stored = loadTemplate("full_profile");
        assertEquals(history.v1().getPromptVersionId(), stored.getActiveVersionId());
        assertNull(stored.getDraftVersionId());
        assertEquals("released", loadVersion(
                stored.getTemplateId(), history.v1().getPromptVersionId()).getLifecycleStatus());
        assertEquals("released", loadVersion(
                stored.getTemplateId(), history.v2().getPromptVersionId()).getLifecycleStatus());
        Map<String, Object> restoreAudit = jdbc.queryForMap(
                "SELECT from_version_id, to_version_id, reason_code, runtime_sha256 "
                        + "FROM ai_profile_import_prompt_audit WHERE action_code='restore'");
        assertEquals(history.v2().getPromptVersionId(), restoreAudit.get("from_version_id"));
        assertEquals(history.v1().getPromptVersionId(), restoreAudit.get("to_version_id"));
        assertEquals("QUALITY_REGRESSION", restoreAudit.get("reason_code"));
        assertEquals(resolvedV1.runtimeSha256(), restoreAudit.get("runtime_sha256"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log "
                        + "WHERE operation_code='prompt-restore'",
                Integer.class));
    }

    @Test
    void restoreRejectsCrossTemplateDamagedAndUnsupportedTargets() {
        PublishedHistory full = publishV1AndV2(
                "full_profile", "\n版本二恢复拒绝夹具。", "sanitized restore guard");
        TestedDraft works = successfullyTestDraft("works_only");
        managementService.publish(
                73L,
                works.version().getPromptVersionId(),
                actionRequest("INITIAL_RELEASE", works.template(), works.version()));
        Long expectedActive = full.v2().getPromptVersionId();
        int restoreAuditsBefore = actionCount("restore");

        assertRestoreRejected("full_profile", works.version().getPromptVersionId(), expectedActive);

        String originalHash = full.v1().getContentSha256();
        jdbc.update("UPDATE ai_profile_import_prompt_version SET content_sha256=REPEAT('d',64) "
                        + "WHERE prompt_version_id=?",
                full.v1().getPromptVersionId());
        assertRestoreRejected("full_profile", full.v1().getPromptVersionId(), expectedActive);
        jdbc.update("UPDATE ai_profile_import_prompt_version SET content_sha256=? "
                        + "WHERE prompt_version_id=?",
                originalHash,
                full.v1().getPromptVersionId());

        String unsupportedSchema = "unsupported-schema-v99";
        String unsupportedSchemaHash = independentContentSha256(
                full.template(),
                full.v1(),
                unsupportedSchema,
                full.v1().getContractVersion());
        jdbc.update("UPDATE ai_profile_import_prompt_version "
                        + "SET schema_version=?, contract_version=?, content_sha256=? "
                        + "WHERE prompt_version_id=?",
                unsupportedSchema,
                full.v1().getContractVersion(),
                unsupportedSchemaHash,
                full.v1().getPromptVersionId());
        assertEquals(unsupportedSchemaHash, contentShaByVersionId(
                full.v1().getPromptVersionId()));
        assertRestoreRejected("full_profile", full.v1().getPromptVersionId(), expectedActive);

        String unsupportedContract = "unsupported-contract-v99";
        String unsupportedContractHash = independentContentSha256(
                full.template(),
                full.v1(),
                full.v1().getSchemaVersion(),
                unsupportedContract);
        jdbc.update("UPDATE ai_profile_import_prompt_version "
                        + "SET schema_version=?, contract_version=?, content_sha256=? "
                        + "WHERE prompt_version_id=?",
                full.v1().getSchemaVersion(),
                unsupportedContract,
                unsupportedContractHash,
                full.v1().getPromptVersionId());
        assertEquals(unsupportedContractHash, contentShaByVersionId(
                full.v1().getPromptVersionId()));
        assertRestoreRejected("full_profile", full.v1().getPromptVersionId(), expectedActive);

        assertEquals(restoreAuditsBefore, actionCount("restore"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log "
                        + "WHERE operation_code='prompt-restore'",
                Integer.class));
    }

    @Test
    void sixGovernanceActionsHaveSanitizedDedicatedAudits() {
        PublishedHistory history = publishV1AndV2(
                "full_profile", "\n版本二审计覆盖。", "sanitized audit coverage");
        AiProfileImportPromptTemplate activeV2 = loadTemplate("full_profile");
        ProfileImportPromptCreateDraftReqDTO create = new ProfileImportPromptCreateDraftReqDTO();
        create.setExpectedTemplateVersion(activeV2.getVersion());
        managementService.createDraft(73L, "full_profile", create);
        AiProfileImportPromptTemplate withV3 = loadTemplate("full_profile");
        AiProfileImportPromptVersion v3 = loadVersion(
                withV3.getTemplateId(), withV3.getDraftVersionId());
        managementService.abandonDraft(
                73L,
                v3.getPromptVersionId(),
                actionRequest("DRAFT_INVALID", withV3, v3));
        AiProfileImportPromptTemplate withoutDraft = loadTemplate("full_profile");
        managementService.restore(
                73L,
                "full_profile",
                history.v1().getPromptVersionId(),
                restoreRequest("QUALITY_REGRESSION", withoutDraft));

        Set<String> actions = Set.copyOf(jdbc.queryForList(
                "SELECT DISTINCT action_code FROM ai_profile_import_prompt_audit",
                String.class));
        assertEquals(Set.of(
                "draft_create",
                "draft_update",
                "draft_abandon",
                "test",
                "publish",
                "restore"), actions);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT action_code, reason_code, result_status, error_code, message, "
                        + "content_sha256, runtime_sha256, fixture_code, operator_id "
                        + "FROM ai_profile_import_prompt_audit");
        for (Map<String, Object> row : rows) {
            String action = (String) row.get("action_code");
            String reason = (String) row.get("reason_code");
            assertTrue(allowedReasons(action).contains(reason), action + ":" + reason);
            assertEquals("success", row.get("result_status"));
            assertNull(row.get("error_code"));
            assertNull(row.get("message"));
            assertNotNull(row.get("operator_id"));
            assertNotNull(row.get("content_sha256"));
            if (Set.of("test", "publish", "restore").contains(action)) {
                assertNotNull(row.get("runtime_sha256"));
            }
            if (Set.of("test", "publish").contains(action)) {
                assertNotNull(row.get("fixture_code"));
            }
        }
        assertNoForbiddenAuditColumns();
    }

    @Test
    void sensitiveOrFreeReasonIsRejectedBeforeAnyPersistence() {
        PublishedHistory history = publishV1AndV2(
                "full_profile", "\n版本二非法原因验证。", "sanitized reason guard");
        AiProfileImportPromptTemplate activeV2 = loadTemplate("full_profile");
        ProfileImportPromptCreateDraftReqDTO create = new ProfileImportPromptCreateDraftReqDTO();
        create.setExpectedTemplateVersion(activeV2.getVersion());
        managementService.createDraft(73L, "full_profile", create);
        AiProfileImportPromptTemplate withDraft = loadTemplate("full_profile");
        AiProfileImportPromptVersion draft = loadVersion(
                withDraft.getTemplateId(), withDraft.getDraftVersionId());
        Map<String, Object> before = persistenceFingerprint("full_profile");
        List<String> rejected = List.of(
                TEST_API_KEY,
                USER_RAW_SENTINEL,
                draft.getSystemPromptBody(),
                fixtureCatalog.load("full_profile").body(),
                CHANGE_SUMMARY_SENTINEL,
                MODEL_RESPONSE_SENTINEL,
                FREE_REASON_SENTINEL);

        for (String raw : rejected) {
            ProfileImportPromptVersionActionReqDTO publish =
                    actionRequest(raw, withDraft, draft);
            assertRejectedReason(raw, () -> managementService.publish(
                    73L, draft.getPromptVersionId(), publish));

            ProfileImportPromptVersionActionReqDTO abandon =
                    actionRequest(raw, withDraft, draft);
            assertRejectedReason(raw, () -> managementService.abandonDraft(
                    73L, draft.getPromptVersionId(), abandon));

            ProfileImportPromptRestoreReqDTO restore = restoreRequest(raw, withDraft);
            assertRejectedReason(raw, () -> managementService.restore(
                    73L,
                    "full_profile",
                    history.v1().getPromptVersionId(),
                    restore));
        }

        assertEquals(before, persistenceFingerprint("full_profile"));
    }

    @Test
    void newTablesAndAdminOperationLogContainNoForbiddenPayload() throws Exception {
        PublishedHistory history = publishV1AndV2(
                "full_profile",
                "\n" + PROMPT_BODY_SENTINEL,
                CHANGE_SUMMARY_SENTINEL);
        ProfileImportPromptRuntime resolvedV2 = runtimeResolver.resolve("full_profile");
        persistRequestAudit("privacy-before-restore", 902L, resolvedV2);
        AiProfileImportPromptTemplate beforeRestore = loadTemplate("full_profile");
        assertRejectedReason(FREE_REASON_SENTINEL, () -> managementService.restore(
                73L,
                "full_profile",
                history.v1().getPromptVersionId(),
                restoreRequest(FREE_REASON_SENTINEL, beforeRestore)));
        managementService.restore(
                73L,
                "full_profile",
                history.v1().getPromptVersionId(),
                restoreRequest("INCIDENT_ROLLBACK", beforeRestore));
        persistRequestAudit(
                "privacy-after-restore", 902L, runtimeResolver.resolve("full_profile"));

        String storedVersionBody = jdbc.queryForObject(
                "SELECT system_prompt_body FROM ai_profile_import_prompt_version "
                        + "WHERE prompt_version_id=?",
                String.class,
                history.v2().getPromptVersionId());
        String storedChangeSummary = jdbc.queryForObject(
                "SELECT change_summary FROM ai_profile_import_prompt_version "
                        + "WHERE prompt_version_id=?",
                String.class,
                history.v2().getPromptVersionId());
        assertTrue(storedVersionBody.contains(PROMPT_BODY_SENTINEL));
        assertEquals(CHANGE_SUMMARY_SENTINEL, storedChangeSummary);
        assertTrue(columnNames("ai_profile_import_prompt_version")
                .containsAll(Set.of("system_prompt_body", "repair_prompt_body", "change_summary")));
        assertNoForbiddenAuditColumns();
        assertNoForbiddenRequestAuditColumns();
        assertNoUnexpectedVersionSecretColumns();

        List<String> promptAuditProjection = jdbc.queryForList(
                "SELECT CAST(JSON_OBJECT("
                        + "'templateId',template_id,'promptVersionId',prompt_version_id,"
                        + "'actionCode',action_code,'fromVersionId',from_version_id,"
                        + "'toVersionId',to_version_id,'contentSha256',content_sha256,"
                        + "'runtimeSha256',runtime_sha256,'schemaVersion',schema_version,"
                        + "'contractVersion',contract_version,'fixtureCode',fixture_code,"
                        + "'fixtureVersion',fixture_version,'fixtureSha256',fixture_sha256,"
                        + "'modelName',model_name,'configVersion',config_version,"
                        + "'testOperatorId',test_operator_id,'testedAt',tested_at,"
                        + "'operatorId',operator_id,'operatorName',operator_name,"
                        + "'reasonCode',reason_code,'resultStatus',result_status,"
                        + "'errorCode',error_code,'message',message) AS CHAR) "
                        + "FROM ai_profile_import_prompt_audit",
                String.class);
        List<String> requestAuditProjection = jdbc.queryForList(
                "SELECT CAST(JSON_OBJECT("
                        + "'requestId',request_id,'userId',user_id,'configId',config_id,"
                        + "'modelName',model_name,'scene',scene,"
                        + "'promptTemplateCode',prompt_template_code,"
                        + "'promptVersionId',prompt_version_id,"
                        + "'promptVersionNo',prompt_version_no,"
                        + "'promptSchemaVersion',prompt_schema_version,"
                        + "'promptContractVersion',prompt_contract_version,"
                        + "'promptRuntimeSha256',prompt_runtime_sha256,"
                        + "'status',status,'inputLength',input_length,"
                        + "'candidateCount',candidate_count,'workCount',work_count,"
                        + "'conflictCount',conflict_count,'elapsedMs',elapsed_ms,"
                        + "'errorCode',error_code,'profileVersion',profile_version,"
                        + "'workLibraryVersion',work_library_version) AS CHAR) "
                        + "FROM ai_profile_import_request_audit",
                String.class);
        List<Map<String, Object>> operationLogs = jdbc.queryForList(
                "SELECT operation_code, before_snapshot_json, after_snapshot_json, "
                        + "fail_reason, confirm_token, extra_context_json "
                        + "FROM admin_operation_log WHERE module_code='ai-profile-import' "
                        + "AND operation_code IN ('prompt-publish','prompt-restore')");
        assertEquals(3, operationLogs.size());
        Set<String> operationCodes = new java.util.HashSet<>();
        List<String> operationPayloads = new java.util.ArrayList<>();
        Set<String> allowedOperationKeys = Set.of(
                "templateId",
                "promptVersionId",
                "versionNo",
                "scene",
                "contentSha256",
                "runtimeSha256",
                "lifecycleStatus",
                "reasonCode",
                "candidateCount",
                "workCount");
        for (Map<String, Object> log : operationLogs) {
            operationCodes.add((String) log.get("operation_code"));
            assertNull(log.get("before_snapshot_json"));
            assertNull(log.get("after_snapshot_json"));
            assertNull(log.get("fail_reason"));
            assertNull(log.get("confirm_token"));
            String payload = String.valueOf(log.get("extra_context_json"));
            operationPayloads.add(payload);
            Set<String> actualKeys = new java.util.HashSet<>();
            objectMapper.readTree(payload).fieldNames().forEachRemaining(actualKeys::add);
            assertEquals(allowedOperationKeys, actualKeys);
        }
        assertEquals(Set.of("prompt-publish", "prompt-restore"), operationCodes);

        List<String> forbiddenValues = List.of(
                TEST_API_KEY,
                PROMPT_BODY_SENTINEL,
                CHANGE_SUMMARY_SENTINEL,
                FREE_REASON_SENTINEL,
                fixtureCatalog.load("full_profile").body());
        assertPayloadsExclude(promptAuditProjection, forbiddenValues);
        assertPayloadsExclude(requestAuditProjection, forbiddenValues);
        assertPayloadsExclude(operationPayloads, forbiddenValues);
        String ciphertext = jdbc.queryForObject(
                "SELECT secret_config_ciphertext FROM ai_profile_import_config "
                        + "WHERE provider_code='deepseek'",
                String.class);
        assertFalse(ciphertext.contains(TEST_API_KEY));
    }

    @Test
    void phaseASeedsTwoUntestedDraftsWithNoActivePointer() {
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_template WHERE deleted=0",
                Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_version "
                        + "WHERE lifecycle_status='draft' AND test_status='untested' AND deleted=0",
                Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_template "
                        + "WHERE active_version_id IS NOT NULL",
                Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_template t "
                        + "JOIN ai_profile_import_prompt_version v "
                        + "ON v.prompt_version_id=t.draft_version_id "
                        + "WHERE v.template_id<>t.template_id",
                Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema=DATABASE() "
                        + "AND table_name='assert_ai_profile_import_prompt_bootstrap'",
                Integer.class));
        assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8.0.36"));
    }

    @Test
    void databaseRejectsCrossTemplatePointerAndSecondOpenDraft() {
        assertThrows(DataAccessException.class, this::pointFullProfileAtWorksDraft);
        assertThrows(DataAccessException.class, this::insertSecondFullProfileDraft);
    }

    @Test
    void bootstrapAssertionFailsAgainstSemanticallyInvalidRealMySqlState() throws Exception {
        pointFullActivePointerAtItsOwnedDraft();
        try {
            assertThrows(DataAccessException.class,
                    () -> executeSql(assertionBlock(v001Sql())));
        } finally {
            jdbc.execute("DROP TABLE IF EXISTS assert_ai_profile_import_prompt_bootstrap");
        }
    }

    @Test
    void bootstrapBodiesAndHashShapesMatchTheExactSeedContract() {
        assertEquals(expectedLegacyBodyWithTerminalLf(), body("full_profile"));
        assertEquals(expectedLegacyBodyWithTerminalLf()
                        + "当前场景只提取作品；profileCandidates 必须返回空数组，不得生成个人档案候选。\n",
                body("works_only"));
        assertEquals(64, contentSha("full_profile").length());
        assertEquals(64, contentSha("works_only").length());
    }

    @Test
    void javaContentHashEqualsEachStoredBootstrapHash() {
        for (String scene : List.of("full_profile", "works_only")) {
            AiProfileImportPromptTemplate template = loadTemplate(scene);
            AiProfileImportPromptVersion version = loadVersion(
                    template.getTemplateId(), template.getDraftVersionId());
            assertEquals(
                    version.getContentSha256(),
                    renderer.contentSha256(template, version),
                    scene);
        }
    }

    @Test
    @Transactional
    void lockingMappersRecheckTemplateOwnershipInsideTheLock() {
        AiProfileImportPromptTemplate full =
                templateMapper.selectByCodeForUpdate("full_profile");
        AiProfileImportPromptTemplate same =
                templateMapper.selectByIdForUpdate(full.getTemplateId());
        assertEquals(full.getTemplateId(), same.getTemplateId());
        assertNotNull(versionMapper.selectOwnedForUpdate(
                full.getTemplateId(), full.getDraftVersionId()));
        assertNull(versionMapper.selectOwnedForUpdate(
                full.getTemplateId(), draftVersionId("works_only")));
        assertNotNull(configMapper.selectByProviderCodeForUpdate("deepseek"));
    }

    @Test
    void conditionalDraftUpdateMakesSuccessfulTestStaleAndPersistsOperatorMetadata() {
        AiProfileImportPromptTemplate template = loadTemplate("full_profile");
        assertEquals("success", managementService.test(
                73L, template.getDraftVersionId()).getStatus());
        AiProfileImportPromptVersion draft = versionMapper.selectOwnedForUpdate(
                template.getTemplateId(), template.getDraftVersionId());
        Integer expectedVersion = draft.getVersion();
        String editedContentSha = "b".repeat(64);

        draft.setVersionLabel("bootstrap-v1-edited");
        draft.setSystemPromptBody("edited system\n");
        draft.setRepairPromptBody("edited repair");
        draft.setContentSha256(editedContentSha);
        draft.setChangeSummary("quality adjustment");
        draft.setSchemaVersion("admin-must-not-change-schema");
        draft.setContractVersion("admin-must-not-change-contract");
        draft.setUpdateUserId(73L);
        draft.setUpdateUserName("Review Admin");

        assertEquals(1, versionMapper.updateDraftIfExpected(draft, expectedVersion));

        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT version_label, system_prompt_body, repair_prompt_body, "
                        + "content_sha256, change_summary, schema_version, contract_version, "
                        + "test_status, update_user_id, update_user_name "
                        + "FROM ai_profile_import_prompt_version WHERE prompt_version_id=?",
                draft.getPromptVersionId());
        assertEquals("bootstrap-v1-edited", stored.get("version_label"));
        assertEquals("edited system\n", stored.get("system_prompt_body"));
        assertEquals("edited repair", stored.get("repair_prompt_body"));
        assertEquals(editedContentSha, stored.get("content_sha256"));
        assertEquals("quality adjustment", stored.get("change_summary"));
        assertEquals("profile-import-json-v1", stored.get("schema_version"));
        assertEquals("profile-import-contract-v1", stored.get("contract_version"));
        assertEquals("stale", stored.get("test_status"));
        assertEquals(73L, stored.get("update_user_id"));
        assertEquals("Review Admin", stored.get("update_user_name"));
    }

    @Test
    void permissionMigrationIsExecutableIdempotentAndScopesOnlyLiveEligibleRoles()
            throws Exception {
        jdbc.update("DELETE FROM admin_role WHERE role_code IN "
                + "('admin','super_admin','inactive_admin','deleted_admin',"
                + "'invalid_status_admin','custom_system','unrelated')");
        seedRole("admin", 1, 0, "[]", "[\"existing.action\"]");
        seedRole("super_admin", 1, 0, "[]", "[\"super.keep\"]");
        seedRole("inactive_admin", 2, 0, "[\"menu.system\"]", "[]");
        seedRole("invalid_status_admin", 0, 0, "[\"menu.system\"]", "[]");
        seedRole("deleted_admin", 1, 1, "[\"menu.system\"]", "[]");
        seedRole("custom_system", 1, 0, "[\"menu.system\"]", "[\"custom.keep\"]");
        seedRole("unrelated", 1, 0, "[\"menu.dashboard\"]", "[]");

        executeSql(permissionMigrationSql());
        executeSql(permissionMigrationSql());

        assertPermissionsExactlyOnce("admin", "existing.action");
        assertPermissionsExactlyOnce("super_admin", "super.keep");
        assertPermissionsExactlyOnce("custom_system", "custom.keep");
        assertNoPromptPermissions("inactive_admin");
        assertNoPromptPermissions("invalid_status_admin");
        assertNoPromptPermissions("deleted_admin");
        assertNoPromptPermissions("unrelated");
    }

    private TestedDraft successfullyTestDraft(String templateCode) {
        AiProfileImportPromptTemplate template = loadTemplate(templateCode);
        AiProfileImportPromptVersion before = loadVersion(
                template.getTemplateId(), template.getDraftVersionId());
        ExpectedTestBinding expected = expectedTestBinding(template, before);
        ProfileImportPromptTestResultRespDTO result = managementService.test(
                73L, template.getDraftVersionId());
        assertEquals(before.getPromptVersionId(), result.getPromptVersionId());
        assertEquals(expected.contentSha256(), result.getContentSha256());
        assertEquals(expected.runtimeSha256(), result.getRuntimeSha256());
        assertEquals(expected.fixtureCode(), result.getFixtureCode());
        assertEquals(expected.fixtureVersion(), result.getFixtureVersion());
        assertEquals(expected.fixtureSha256(), result.getFixtureSha256());
        assertEquals(expected.modelName(), result.getModelName());
        assertEquals(expected.configVersion(), result.getConfigVersion());
        assertEquals("success", result.getStatus());
        assertEquals("works_only".equals(template.getScene()) ? 0 : 2,
                result.getCandidateCount());
        assertEquals(2, result.getWorkCount());
        assertNull(result.getErrorCode());
        assertEquals(73L, result.getTestedBy());
        assertNotNull(result.getTestedAt());
        AiProfileImportPromptTemplate freshTemplate = loadTemplate(templateCode);
        AiProfileImportPromptVersion tested = loadVersion(
                freshTemplate.getTemplateId(), freshTemplate.getDraftVersionId());
        assertEquals("success", tested.getTestStatus());
        assertEquals(expected.contentSha256(), tested.getContentSha256());
        assertEquals(expected.contentSha256(), tested.getTestedContentSha256());
        assertEquals(expected.runtimeSha256(), tested.getTestedRuntimeSha256());
        assertEquals(expected.fixtureCode(), tested.getTestFixtureCode());
        assertEquals(expected.fixtureVersion(), tested.getTestFixtureVersion());
        assertEquals(expected.fixtureSha256(), tested.getTestFixtureSha256());
        assertEquals(expected.modelName(), tested.getTestedModelName());
        assertEquals(expected.configVersion(), tested.getTestedConfigVersion());
        assertEquals(result.getCandidateCount(), tested.getTestCandidateCount());
        assertEquals(result.getWorkCount(), tested.getTestWorkCount());
        assertEquals(73L, tested.getTestedBy());
        assertNotNull(tested.getTestedAt());
        return new TestedDraft(freshTemplate, tested);
    }

    private ExpectedTestBinding expectedTestBinding(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version) {
        String contentSha256 = renderer.contentSha256(template, version);
        ProfileImportPromptRuntime runtime = renderer.render(template, version);
        ProfileImportPromptFixtureCatalog.Fixture fixture =
                fixtureCatalog.load(template.getScene());
        Map<String, Object> config = jdbc.queryForMap(
                "SELECT model_name, version FROM ai_profile_import_config "
                        + "WHERE provider_code='deepseek' AND deleted=0");
        return new ExpectedTestBinding(
                contentSha256,
                runtime.runtimeSha256(),
                fixture.code(),
                fixture.version(),
                fixture.sha256(),
                (String) config.get("model_name"),
                ((Number) config.get("version")).intValue());
    }

    private static String independentContentSha256(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version,
            String schemaVersion,
            String contractVersion) {
        return independentFramedSha256(List.of(
                CONTENT_HASH_DOMAIN,
                template.getTemplateCode(),
                template.getScene(),
                schemaVersion,
                contractVersion,
                version.getSystemPromptBody(),
                version.getRepairPromptBody()));
    }

    private static String independentFramedSha256(List<String> fields) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                for (String field : fields) {
                    byte[] value = field.replace("\r\n", "\n").replace('\r', '\n')
                            .getBytes(StandardCharsets.UTF_8);
                    output.writeInt(value.length);
                    output.write(value);
                }
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (Exception error) {
            throw new IllegalStateException("independent content hash unavailable", error);
        }
    }

    private String contentShaByVersionId(Long promptVersionId) {
        return jdbc.queryForObject(
                "SELECT content_sha256 FROM ai_profile_import_prompt_version "
                        + "WHERE prompt_version_id=?",
                String.class,
                promptVersionId);
    }

    private PublishedHistory publishV1AndV2(
            String templateCode, String v2BodySuffix, String changeSummary) {
        TestedDraft testedV1 = successfullyTestDraft(templateCode);
        managementService.publish(
                73L,
                testedV1.version().getPromptVersionId(),
                actionRequest("INITIAL_RELEASE", testedV1.template(), testedV1.version()));
        AiProfileImportPromptTemplate activeV1 = loadTemplate(templateCode);
        AiProfileImportPromptVersion releasedV1 = loadVersion(
                activeV1.getTemplateId(), activeV1.getActiveVersionId());

        ProfileImportPromptCreateDraftReqDTO create = new ProfileImportPromptCreateDraftReqDTO();
        create.setExpectedTemplateVersion(activeV1.getVersion());
        managementService.createDraft(73L, templateCode, create);
        AiProfileImportPromptTemplate withV2Draft = loadTemplate(templateCode);
        AiProfileImportPromptVersion v2Draft = loadVersion(
                withV2Draft.getTemplateId(), withV2Draft.getDraftVersionId());
        ProfileImportPromptUpdateDraftReqDTO update =
                new ProfileImportPromptUpdateDraftReqDTO();
        update.setVersionLabel("governed-v2");
        update.setSystemPromptBody(v2Draft.getSystemPromptBody() + v2BodySuffix);
        update.setRepairPromptBody(v2Draft.getRepairPromptBody() + "\n只修复合法 JSON，不改变事实。");
        update.setChangeSummary(changeSummary);
        update.setExpectedVersion(v2Draft.getVersion());
        managementService.updateDraft(73L, v2Draft.getPromptVersionId(), update);

        TestedDraft testedV2 = successfullyTestDraft(templateCode);
        managementService.publish(
                73L,
                testedV2.version().getPromptVersionId(),
                actionRequest("QUALITY_ADJUSTMENT", testedV2.template(), testedV2.version()));
        AiProfileImportPromptTemplate activeV2 = loadTemplate(templateCode);
        AiProfileImportPromptVersion releasedV2 = loadVersion(
                activeV2.getTemplateId(), activeV2.getActiveVersionId());
        return new PublishedHistory(activeV2, releasedV1, releasedV2);
    }

    private void persistRequestAudit(
            String requestId, Long userId, ProfileImportPromptRuntime runtime) {
        Long configId = jdbc.queryForObject(
                "SELECT config_id FROM ai_profile_import_config "
                        + "WHERE provider_code='deepseek' AND deleted=0",
                Long.class);
        AiProfileImportRequestAudit audit = new AiProfileImportRequestAudit();
        audit.setRequestId(requestId);
        audit.setUserId(userId);
        audit.setConfigId(configId);
        audit.setModelName("deepseek-chat");
        audit.setScene(runtime.scene());
        audit.setPromptTemplateCode(runtime.templateCode());
        audit.setPromptVersionId(runtime.promptVersionId());
        audit.setPromptVersionNo(runtime.versionNo());
        audit.setPromptSchemaVersion(runtime.schemaVersion());
        audit.setPromptContractVersion(runtime.contractVersion());
        audit.setPromptRuntimeSha256(runtime.runtimeSha256());
        audit.setStatus("success");
        audit.setInputLength(128);
        audit.setCandidateCount("works_only".equals(runtime.scene()) ? 0 : 2);
        audit.setWorkCount(2);
        audit.setConflictCount(0);
        audit.setElapsedMs(19L);
        audit.setProfileVersion(5L);
        audit.setWorkLibraryVersion(7L);
        audit.setDeleted(0);
        assertEquals(1, requestAuditMapper.insert(audit));
        assertNotNull(audit.getAuditId());
    }

    private Map<String, Object> requestLineage(String requestId) {
        return jdbc.queryForMap(
                "SELECT scene, prompt_template_code, prompt_version_id, prompt_version_no, "
                        + "prompt_schema_version, prompt_contract_version, prompt_runtime_sha256 "
                        + "FROM ai_profile_import_request_audit WHERE request_id=?",
                requestId);
    }

    private void assertLineageEquals(
            ProfileImportPromptRuntime expected, Map<String, Object> actual) {
        assertEquals(expected.scene(), actual.get("scene"));
        assertEquals(expected.templateCode(), actual.get("prompt_template_code"));
        assertEquals(expected.promptVersionId(), actual.get("prompt_version_id"));
        assertEquals(expected.versionNo(), actual.get("prompt_version_no"));
        assertEquals(expected.schemaVersion(), actual.get("prompt_schema_version"));
        assertEquals(expected.contractVersion(), actual.get("prompt_contract_version"));
        assertEquals(expected.runtimeSha256(), actual.get("prompt_runtime_sha256"));
    }

    private ProfileImportPromptRestoreReqDTO restoreRequest(
            String reasonCode, AiProfileImportPromptTemplate template) {
        ProfileImportPromptRestoreReqDTO request = new ProfileImportPromptRestoreReqDTO();
        request.setReasonCode(reasonCode);
        request.setExpectedTemplateVersion(template.getVersion());
        return request;
    }

    private void assertRestoreRejected(
            String templateCode, Long targetVersionId, Long expectedActiveVersionId) {
        AiProfileImportPromptTemplate before = loadTemplate(templateCode);
        int auditCountBefore = actionCount("restore");
        int logCountBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log "
                        + "WHERE operation_code='prompt-restore'",
                Integer.class);
        BizException failure = assertThrows(BizException.class,
                () -> managementService.restore(
                        73L,
                        templateCode,
                        targetVersionId,
                        restoreRequest("QUALITY_REGRESSION", before)));
        assertEquals(46022, failure.getCode());
        AiProfileImportPromptTemplate after = loadTemplate(templateCode);
        assertEquals(expectedActiveVersionId, after.getActiveVersionId());
        assertEquals(before.getDraftVersionId(), after.getDraftVersionId());
        assertEquals(before.getVersion(), after.getVersion());
        assertEquals(auditCountBefore, actionCount("restore"));
        assertEquals(logCountBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log "
                        + "WHERE operation_code='prompt-restore'",
                Integer.class));
    }

    private void assertRestoreFaultRollsBack(
            PublishedHistory history, FaultTarget target, FaultMode mode) {
        faults.reset();
        RestoreTransactionState before = restoreTransactionState(history);
        assertEquals(history.v2().getPromptVersionId(), before.activeVersionId());
        assertNull(before.draftVersionId());
        assertEquals("released", before.v1Lifecycle());
        assertEquals("released", before.v2Lifecycle());
        if (target == FaultTarget.PROMPT_AUDIT) {
            faults.promptAuditMode.set(mode);
        } else {
            faults.adminLogMode.set(mode);
        }

        assertThrows(IllegalStateException.class,
                () -> managementService.restore(
                        73L,
                        history.template().getTemplateCode(),
                        history.v1().getPromptVersionId(),
                        restoreRequest(
                                "INCIDENT_ROLLBACK",
                                loadTemplate(history.template().getTemplateCode()))));

        RestoreTransactionState after = restoreTransactionState(history);
        assertEquals(before, after);
        assertEquals(history.v2().getPromptVersionId(), after.activeVersionId());
        assertNull(after.draftVersionId());
        assertEquals("released", after.v1Lifecycle());
        assertEquals("released", after.v2Lifecycle());
        faults.reset();
    }

    private RestoreTransactionState restoreTransactionState(PublishedHistory history) {
        AiProfileImportPromptTemplate template =
                loadTemplate(history.template().getTemplateCode());
        AiProfileImportPromptVersion v1 = loadVersion(
                template.getTemplateId(), history.v1().getPromptVersionId());
        AiProfileImportPromptVersion v2 = loadVersion(
                template.getTemplateId(), history.v2().getPromptVersionId());
        return new RestoreTransactionState(
                template.getActiveVersionId(),
                template.getDraftVersionId(),
                template.getVersion(),
                v1.getLifecycleStatus(),
                v2.getLifecycleStatus(),
                v1.getVersion(),
                v2.getVersion(),
                actionCount("restore"),
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM admin_operation_log "
                                + "WHERE operation_code='prompt-restore'",
                        Integer.class));
    }

    private int actionCount(String actionCode) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit WHERE action_code=?",
                Integer.class,
                actionCode);
    }

    private static Set<String> allowedReasons(String actionCode) {
        return switch (actionCode) {
            case "draft_create" -> Set.of("DRAFT_CREATED_CURRENT", "DRAFT_CREATED_HISTORY");
            case "draft_update" -> Set.of("DRAFT_UPDATED");
            case "draft_abandon" -> Set.of("DRAFT_SUPERSEDED", "DRAFT_INVALID");
            case "test" -> Set.of("TEST_EXECUTED");
            case "publish" -> Set.of(
                    "INITIAL_RELEASE", "QUALITY_ADJUSTMENT", "CONFIG_ALIGNMENT");
            case "restore" -> Set.of("QUALITY_REGRESSION", "INCIDENT_ROLLBACK");
            default -> Set.of();
        };
    }

    private void assertNoForbiddenAuditColumns() {
        for (String column : columnNames("ai_profile_import_prompt_audit")) {
            assertFalse(hasForbiddenColumnToken(
                    column,
                    Set.of(
                            "body",
                            "raw",
                            "source",
                            "response",
                            "key",
                            "secret",
                            "change_summary",
                            "free_reason",
                            "fixture_body")));
        }
    }

    private void assertNoForbiddenRequestAuditColumns() {
        for (String column : columnNames("ai_profile_import_request_audit")) {
            assertFalse(hasForbiddenColumnToken(
                    column,
                    Set.of("body", "raw", "source", "response", "key", "secret")));
        }
    }

    private void assertNoUnexpectedVersionSecretColumns() {
        Set<String> versionColumns = Set.copyOf(columnNames(
                "ai_profile_import_prompt_version"));
        assertTrue(versionColumns.containsAll(Set.of(
                "system_prompt_body", "repair_prompt_body", "change_summary")));
        for (String column : versionColumns) {
            if (Set.of("system_prompt_body", "repair_prompt_body", "change_summary")
                    .contains(column)) {
                continue;
            }
            assertFalse(hasForbiddenColumnToken(
                    column,
                    Set.of(
                            "body",
                            "raw",
                            "source",
                            "response",
                            "key",
                            "secret",
                            "free_reason",
                            "fixture_body")));
        }
        for (String column : columnNames("ai_profile_import_prompt_template")) {
            assertFalse(hasForbiddenColumnToken(
                    column,
                    Set.of("body", "raw", "source", "response", "key", "secret")));
        }
    }

    private static boolean hasForbiddenColumnToken(
            String columnName, Set<String> forbiddenTokens) {
        String normalized = columnName.toLowerCase();
        return forbiddenTokens.stream().anyMatch(token ->
                normalized.equals(token)
                        || normalized.startsWith(token + "_")
                        || normalized.endsWith("_" + token)
                        || normalized.contains("_" + token + "_"));
    }

    private List<String> columnNames(String tableName) {
        if (!Set.of(
                        "ai_profile_import_prompt_template",
                        "ai_profile_import_prompt_version",
                        "ai_profile_import_prompt_audit",
                        "ai_profile_import_request_audit")
                .contains(tableName)) {
            throw new IllegalArgumentException("unsupported privacy table");
        }
        return jdbc.queryForList(
                "SELECT LOWER(column_name) FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND table_name=? "
                        + "ORDER BY ordinal_position",
                String.class,
                tableName);
    }

    private Map<String, Object> persistenceFingerprint(String templateCode) {
        return jdbc.queryForMap(
                "SELECT t.active_version_id, t.draft_version_id, t.version, "
                        + "(SELECT COUNT(*) FROM ai_profile_import_prompt_version v "
                        + "WHERE v.template_id=t.template_id) AS version_count, "
                        + "(SELECT COALESCE(SUM(v.version),0) "
                        + "FROM ai_profile_import_prompt_version v "
                        + "WHERE v.template_id=t.template_id) AS version_sum, "
                        + "(SELECT COUNT(*) FROM ai_profile_import_prompt_audit) "
                        + "AS prompt_audit_count, "
                        + "(SELECT COUNT(*) FROM ai_profile_import_request_audit) "
                        + "AS request_audit_count, "
                        + "(SELECT COUNT(*) FROM ai_profile_import_config_audit) "
                        + "AS config_audit_count, "
                        + "(SELECT COUNT(*) FROM admin_operation_log) AS operation_log_count "
                        + "FROM ai_profile_import_prompt_template t "
                        + "WHERE t.template_code=? AND t.deleted=0",
                templateCode);
    }

    private static void assertRejectedReason(String raw, Runnable operation) {
        BizException error = assertThrows(BizException.class, operation::run);
        assertEquals(46019, error.getCode());
        assertEquals("Prompt 模板或操作参数无效", error.getMessage());
        assertFalse(error.getMessage().contains(raw));
    }

    private static void assertPayloadsExclude(
            List<String> payloads, List<String> forbiddenValues) {
        for (String payload : payloads) {
            assertNotNull(payload);
            for (String forbidden : forbiddenValues) {
                assertFalse(payload.contains(forbidden));
            }
        }
    }

    private PublishOutcome publishAsAdmin(
            Long adminId,
            TestedDraft fixture,
            CountDownLatch ready,
            CountDownLatch start) {
        installAdmin(adminId);
        try {
            ready.countDown();
            awaitLatch(start);
            return publishOutcome(adminId, fixture);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private PublishOutcome publishOutcome(Long adminId, TestedDraft fixture) {
        try {
            managementService.publish(
                    adminId,
                    fixture.version().getPromptVersionId(),
                    actionRequest("INITIAL_RELEASE", fixture.template(), fixture.version()));
            return new PublishOutcome(true, null);
        } catch (BizException error) {
            return new PublishOutcome(false, error.getCode());
        }
    }

    private ProfileImportPromptUpdateDraftReqDTO editedDraftRequest(
            AiProfileImportPromptVersion draft) {
        ProfileImportPromptUpdateDraftReqDTO request =
                new ProfileImportPromptUpdateDraftReqDTO();
        request.setVersionLabel(draft.getVersionLabel() + " concurrent edit");
        request.setSystemPromptBody(draft.getSystemPromptBody() + "\n并发保存后的有效正文。");
        request.setRepairPromptBody(draft.getRepairPromptBody() + "\n只修复 JSON 格式。");
        request.setChangeSummary("sanitized concurrent adjustment");
        request.setExpectedVersion(draft.getVersion());
        return request;
    }

    private ProfileImportPublicConfigUpdateDTO publicConfigUpdate(String modelName) {
        ProfileImportPublicConfigUpdateDTO request =
                new ProfileImportPublicConfigUpdateDTO();
        request.setEndpoint("https://api.deepseek.com/chat/completions");
        request.setModelName(modelName);
        request.setConnectTimeoutMs(5000);
        request.setReadTimeoutMs(60000);
        request.setMaxInputChars(20000);
        request.setMaxOutputTokens(8000);
        request.setPerUserDailyLimit(20);
        return request;
    }

    private Map<String, Object> publishAuditSnapshot() {
        return jdbc.queryForMap(
                "SELECT prompt_audit_id, template_id, prompt_version_id, action_code, "
                        + "from_version_id, to_version_id, content_sha256, runtime_sha256, "
                        + "schema_version, contract_version, fixture_code, fixture_version, "
                        + "fixture_sha256, model_name, config_version, test_operator_id, "
                        + "tested_at, operator_id, operator_name, reason_code, result_status, "
                        + "error_code, message, create_time FROM ai_profile_import_prompt_audit "
                        + "WHERE action_code='publish'");
    }

    private static int bizCode(Throwable error) {
        if (error instanceof BizException bizException) {
            return bizException.getCode();
        }
        throw new AssertionError("expected BizException but got " + error, error);
    }

    private LockWaitObservation awaitMySqlLockWait(String expectedTable) {
        String processlistUser = jdbc.queryForObject(
                "SELECT SUBSTRING_INDEX(CURRENT_USER(), '@', 1)", String.class);
        String expectedTableLowercase = expectedTable.toLowerCase(java.util.Locale.ROOT);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        LockWaitObservation lastObserved = null;

        while (System.nanoTime() < deadline) {
            List<Map<String, Object>> transactions = lockObserverJdbc.queryForList(
                    "SELECT trx.trx_id, trx.trx_state, trx.trx_mysql_thread_id, "
                            + "trx.trx_query, processlist.USER AS processlist_user "
                            + "FROM information_schema.innodb_trx trx "
                            + "JOIN information_schema.processlist processlist "
                            + "ON processlist.ID = trx.trx_mysql_thread_id "
                            + "WHERE processlist.USER = ?",
                    processlistUser);
            for (Map<String, Object> transaction : transactions) {
                LockWaitObservation observed = new LockWaitObservation(
                        String.valueOf(transaction.get("trx_id")),
                        (String) transaction.get("trx_state"),
                        nullableLong(transaction.get("trx_mysql_thread_id")),
                        (String) transaction.get("trx_query"),
                        (String) transaction.get("processlist_user"));
                lastObserved = observed;
                if ("LOCK WAIT".equals(observed.trxState())
                        && observed.trxQuery() != null
                        && observed.trxQuery().toLowerCase(java.util.Locale.ROOT)
                                .contains(expectedTableLowercase)) {
                    return observed;
                }
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new AssertionError(
                        "interrupted while waiting for MySQL lock wait on " + expectedTable);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }

        throw new AssertionError(
                "timed out waiting for MySQL lock wait on " + expectedTable
                        + "; last observed transaction=" + lastObserved);
    }

    private static Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrency latch timed out");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency latch interrupted", error);
        }
    }

    private static void shutdown(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException error) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private ProfileImportPromptVersionActionReqDTO actionRequest(
            String reasonCode,
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version) {
        ProfileImportPromptVersionActionReqDTO request =
                new ProfileImportPromptVersionActionReqDTO();
        request.setReasonCode(reasonCode);
        request.setExpectedTemplateVersion(template.getVersion());
        request.setExpectedVersion(version.getVersion());
        return request;
    }

    private void assertDraftStillPublishable(TestedDraft fixture, int auditCountBefore) {
        AiProfileImportPromptTemplate template = loadTemplate(fixture.template().getTemplateCode());
        AiProfileImportPromptVersion draft = loadVersion(
                template.getTemplateId(), fixture.version().getPromptVersionId());
        assertNull(template.getActiveVersionId());
        assertEquals(fixture.version().getPromptVersionId(), template.getDraftVersionId());
        assertEquals(fixture.template().getVersion(), template.getVersion());
        assertEquals("draft", draft.getLifecycleStatus());
        assertEquals(fixture.version().getVersion(), draft.getVersion());
        assertEquals("success", draft.getTestStatus());
        assertEquals(auditCountBefore, count("ai_profile_import_prompt_audit"));
    }

    private void assertDraftUpdateAuditFailureRollsBack(
            String templateCode, FaultMode mode) {
        AiProfileImportPromptTemplate template = loadTemplate(templateCode);
        AiProfileImportPromptVersion before = loadVersion(
                template.getTemplateId(), template.getDraftVersionId());
        ProfileImportPromptUpdateDraftReqDTO request =
                new ProfileImportPromptUpdateDraftReqDTO();
        request.setVersionLabel(before.getVersionLabel() + " edited");
        request.setSystemPromptBody(before.getSystemPromptBody() + "\n仅用于事务回滚验证。");
        request.setRepairPromptBody(before.getRepairPromptBody() + "\n保持事实不变。");
        request.setChangeSummary("sanitized quality adjustment");
        request.setExpectedVersion(before.getVersion());
        faults.promptAuditMode.set(mode);

        assertThrows(IllegalStateException.class,
                () -> managementService.updateDraft(
                        73L, before.getPromptVersionId(), request));

        AiProfileImportPromptVersion after = loadVersion(
                template.getTemplateId(), before.getPromptVersionId());
        assertEquals(before.getVersionLabel(), after.getVersionLabel());
        assertEquals(before.getSystemPromptBody(), after.getSystemPromptBody());
        assertEquals(before.getRepairPromptBody(), after.getRepairPromptBody());
        assertEquals(before.getContentSha256(), after.getContentSha256());
        assertEquals(before.getChangeSummary(), after.getChangeSummary());
        assertEquals(before.getVersion(), after.getVersion());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_profile_import_prompt_audit "
                        + "WHERE action_code='draft_update' AND template_id=?",
                Integer.class,
                template.getTemplateId()));
    }

    private int count(String tableName) {
        if (!Set.of(
                        "admin_operation_log",
                        "ai_profile_import_prompt_audit",
                        "ai_profile_import_request_audit",
                        "ai_profile_import_config_audit")
                .contains(tableName)) {
            throw new IllegalArgumentException("unsupported count table");
        }
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private static void installAdmin(Long adminId) {
        AdminAuthenticatedUser admin = AdminAuthenticatedUser.builder()
                .adminUserId(adminId)
                .account("admin-" + adminId)
                .userName("Review Admin " + adminId)
                .roleCodes(Set.of("admin"))
                .permissions(Set.of("action.system.ai-profile-import.template-publish"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, "test", List.of()));
    }

    private void pointFullProfileAtWorksDraft() {
        jdbc.update("UPDATE ai_profile_import_prompt_template "
                        + "SET draft_version_id=? WHERE template_code='full_profile' AND deleted=0",
                draftVersionId("works_only"));
    }

    private void insertSecondFullProfileDraft() {
        jdbc.update("INSERT INTO ai_profile_import_prompt_version "
                        + "(template_id, version_no, version_label, lifecycle_status, "
                        + "system_prompt_body, repair_prompt_body, schema_version, contract_version, "
                        + "content_sha256, test_status) VALUES (?, 2, 'second-draft', 'draft', "
                        + "'system', 'repair', 'profile-import-json-v1', "
                        + "'profile-import-contract-v1', REPEAT('a', 64), 'untested')",
                templateId("full_profile"));
    }

    private void pointFullActivePointerAtItsOwnedDraft() {
        jdbc.update("UPDATE ai_profile_import_prompt_template "
                + "SET active_version_id=draft_version_id "
                + "WHERE template_code='full_profile' AND deleted=0");
    }

    private Long templateId(String templateCode) {
        return jdbc.queryForObject(
                "SELECT template_id FROM ai_profile_import_prompt_template "
                        + "WHERE template_code=? AND deleted=0",
                Long.class,
                templateCode);
    }

    private Long draftVersionId(String templateCode) {
        return jdbc.queryForObject(
                "SELECT draft_version_id FROM ai_profile_import_prompt_template "
                        + "WHERE template_code=? AND deleted=0",
                Long.class,
                templateCode);
    }

    private AiProfileImportPromptTemplate loadTemplate(String scene) {
        return templateMapper.selectByScene(scene);
    }

    private AiProfileImportPromptVersion loadVersion(Long templateId, Long promptVersionId) {
        return versionMapper.selectOwnedDetail(templateId, promptVersionId);
    }

    private String body(String templateCode) {
        return jdbc.queryForObject(
                "SELECT v.system_prompt_body FROM ai_profile_import_prompt_template t "
                        + "JOIN ai_profile_import_prompt_version v "
                        + "ON v.prompt_version_id=t.draft_version_id "
                        + "AND v.template_id=t.template_id "
                        + "WHERE t.template_code=? AND t.deleted=0 AND v.deleted=0",
                String.class,
                templateCode);
    }

    private String contentSha(String templateCode) {
        return jdbc.queryForObject(
                "SELECT v.content_sha256 FROM ai_profile_import_prompt_template t "
                        + "JOIN ai_profile_import_prompt_version v "
                        + "ON v.prompt_version_id=t.draft_version_id "
                        + "AND v.template_id=t.template_id "
                        + "WHERE t.template_code=? AND t.deleted=0 AND v.deleted=0",
                String.class,
                templateCode);
    }

    private String expectedLegacyBodyWithTerminalLf() {
        return """
                你是演员职业资料结构化提取器。只输出合法 JSON 对象，不输出 Markdown 或解释。
                顶层必须包含 profileCandidates、workCandidates、ignoredMediaPlaceholderCount、unmappedSegments、warnings。
                profileCandidates 的 fieldKey 只允许：public_name, gender, age, height, current_city, weight,
                origin_place, school_name, major_name, language_tags, specialty_tags, role_type_tags,
                professional_ability_tags, intro, birth_year, birth_month, birth_day, birth_precision。
                每个档案候选必须包含 candidateId、fieldKey、candidateValue、confidence(0到1)、sourceText、
                sourceType、warning。sourceText 必须逐字来自用户输入，不得改写证据。
                workCandidates 每项必须包含 candidateId、projectName 和 fields。可选扁平字段只允许：roleName,
                publishStatus, workTypeCode, roleLevelCode, shootYear, shootMonth, platform, syncSoundStatus,
                collaborators, achievementText, description。每个非空扁平字段都必须在 fields 中提供
                candidateValue、confidence、sourceText、sourceType、warning，candidateValue 必须与扁平值一致。
                不得补造时间、状态、类型、榜单、热度、播放量、合作演员或数字；原文未给出则返回 null。
                籍贯只能写 origin_place，绝不能写 current_city。2004.9 必须拆为 birth_year=2004、
                birth_month=9、birth_day 不生成、birth_precision=month，不得伪造某月1日。
                只有至少两部不同作品提供一致女性角色证据且没有男性角色反向证据时，才允许生成
                gender=female，并必须标记 sourceType=inferred_from_roles、warning=根据多条作品角色推断，请确认。
                不得依据姓名、头像、院校或专业推断性别。
                [图片]、[视频] 仅计入 ignoredMediaPlaceholderCount，不得创建素材、媒体 URL 或作品。
                sourceType 只允许 explicit、direct、derived_from_birth、inferred_from_roles。
                publishStatus 只允许 aired、upcoming、stage、horizontal、other 或 null。
                workTypeCode 只允许 short_drama、horizontal_short_drama、stage_play、musical、tv_column_drama、
                film_tv、micro_film、horizontal、stage、other 或 null。
                syncSoundStatus 只允许 sync、dubbed、unknown 或 null。
                """;
    }

    private String v001Sql() throws IOException {
        return Files.readString(
                Path.of("src/main/resources/db/migration").resolve(V001),
                StandardCharsets.UTF_8);
    }

    private String permissionMigrationSql() throws IOException {
        return Files.readString(
                Path.of("src/main/resources/db/migration").resolve(V002),
                StandardCharsets.UTF_8);
    }

    private void seedRole(
            String roleCode,
            int status,
            int deleted,
            String menuPermissions,
            String actionPermissions) {
        jdbc.update("INSERT INTO admin_role "
                        + "(role_code, role_name, status, deleted, "
                        + "menu_permissions_json, action_permissions_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                roleCode,
                roleCode,
                status,
                deleted,
                menuPermissions,
                actionPermissions);
    }

    private void assertPermissionsExactlyOnce(String roleCode, String preservedPermission) {
        assertEquals(1, permissionCount(roleCode, preservedPermission));
        for (String permission : PROMPT_PERMISSIONS) {
            assertEquals(1, permissionCount(roleCode, permission), roleCode + ":" + permission);
        }
        assertEquals(
                PROMPT_PERMISSIONS.size(),
                promptPermissionCount(roleCode),
                roleCode + " must contain only the five prompt actions once");
    }

    private void assertNoPromptPermissions(String roleCode) {
        assertEquals(0, promptPermissionCount(roleCode), roleCode);
    }

    private int permissionCount(String roleCode, String permission) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_role r "
                        + "JOIN JSON_TABLE(COALESCE(r.action_permissions_json, JSON_ARRAY()), "
                        + "'$[*]' COLUMNS(permission VARCHAR(128) PATH '$')) p "
                        + "WHERE r.role_code=? AND p.permission=?",
                Integer.class,
                roleCode,
                permission);
    }

    private int promptPermissionCount(String roleCode) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_role r "
                        + "JOIN JSON_TABLE(COALESCE(r.action_permissions_json, JSON_ARRAY()), "
                        + "'$[*]' COLUMNS(permission VARCHAR(128) PATH '$')) p "
                        + "WHERE r.role_code=? "
                        + "AND p.permission LIKE 'action.system.ai-profile-import.template-%'",
                Integer.class,
                roleCode);
    }

    private String bootstrapBlock(String sql) {
        int start = sql.indexOf("SET @prompt_hash_domain");
        if (start < 0) throw new IllegalStateException("V001 bootstrap block is missing");
        return sql.substring(start);
    }

    private String assertionBlock(String sql) {
        int start = sql.indexOf("CREATE TABLE assert_ai_profile_import_prompt_bootstrap");
        if (start < 0) throw new IllegalStateException("V001 assertion block is missing");
        return sql.substring(start);
    }

    private void executeSql(String sql) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            executeSql(connection, sql);
            return null;
        });
    }

    private static void executeSql(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            boolean resultSet = statement.execute(sql);
            while (resultSet || statement.getUpdateCount() != -1) {
                if (resultSet) {
                    try (ResultSet ignored = statement.getResultSet()) {
                        // Drain all results from the multi-statement migration.
                    }
                }
                resultSet = statement.getMoreResults();
            }
        }
    }

    private static PromptGovernanceDatabase startDatabase() {
        try {
            return new PromptGovernanceDatabase();
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan({"com.kaipai.mapper.ai", "com.kaipai.mapper.system"})
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            return DATABASE.dataSource();
        }

        @Bean
        MetaObjectHandlerConfig metaObjectHandlerConfig() {
            return new MetaObjectHandlerConfig();
        }

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            return interceptor;
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
            GlobalConfig globalConfig = new GlobalConfig();
            globalConfig.setMetaObjectHandler(metaObjectHandler);
            factory.setGlobalConfig(globalConfig);
            factory.setPlugins(new Interceptor[] {interceptor});
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean("lockObserverJdbcTemplate")
        JdbcTemplate lockObserverJdbcTemplate() {
            return new JdbcTemplate(DATABASE.lockObserverDataSource());
        }

        @Bean
        TransactionTemplate transactionTemplate(
                PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AiProviderSecretCryptoService aiProviderSecretCryptoService(ObjectMapper objectMapper) {
            return new AiProviderSecretCryptoServiceImpl(objectMapper);
        }

        @Bean
        ProfileImportConnectionTester profileImportConnectionTester() {
            return (config, apiKey) -> {
                // The governance fixture never reaches the DeepSeek network.
            };
        }

        @Bean
        ProfileImportConfigService profileImportConfigService(
                AiProfileImportConfigMapper mapper,
                AiProfileImportConfigAuditMapper auditMapper,
                AiProviderSecretCryptoService crypto,
                ProfileImportConnectionTester tester,
                ObjectMapper objectMapper) {
            return new ProfileImportConfigServiceImpl(
                    mapper, auditMapper, crypto, tester, objectMapper);
        }

        @Bean
        ProfileImportPromptContract profileImportPromptContract() {
            return new ProfileImportPromptContract();
        }

        @Bean
        ProfileImportPromptPolicy profileImportPromptPolicy(
                ProfileImportPromptContract contract) {
            return new ProfileImportPromptPolicy(contract);
        }

        @Bean
        ProfileImportPromptRenderer profileImportPromptRenderer(
                ProfileImportPromptContract contract,
                ProfileImportPromptPolicy policy) {
            return new ProfileImportPromptRenderer(contract, policy);
        }

        @Bean
        ProfileImportPromptFixtureCatalog profileImportPromptFixtureCatalog(
                ResourceLoader resourceLoader) {
            return new ProfileImportPromptFixtureCatalog(resourceLoader);
        }

        @Bean
        ControlledFixturePromptTester profileImportPromptTester(
                ProfileImportPromptRenderer renderer,
                ProfileImportPromptFixtureCatalog fixtureCatalog) {
            return new ControlledFixturePromptTester(renderer, fixtureCatalog);
        }

        @Bean
        AdminAuthContext adminAuthContext() {
            return new AdminAuthContext();
        }

        @Bean
        PromptGovernanceFaults promptGovernanceFaults() {
            return new PromptGovernanceFaults();
        }

        @Bean
        @Primary
        AiProfileImportPromptAuditMapper switchablePromptAuditMapper(
                @Qualifier("aiProfileImportPromptAuditMapper")
                        AiProfileImportPromptAuditMapper delegate,
                PromptGovernanceFaults faults) {
            return new SwitchablePromptAuditMapper(delegate, faults);
        }

        @Bean("realAdminOperationLogService")
        AdminOperationLogService realAdminOperationLogService() {
            return new AdminOperationLogServiceImpl();
        }

        @Bean
        @Primary
        AdminOperationLogService switchableAdminOperationLogService(
                @Qualifier("realAdminOperationLogService") AdminOperationLogService delegate,
                PromptGovernanceFaults faults) {
            return (AdminOperationLogService) Proxy.newProxyInstance(
                    AdminOperationLogService.class.getClassLoader(),
                    new Class<?>[] {AdminOperationLogService.class},
                    (proxy, method, arguments) -> {
                        if ("save".equals(method.getName())
                                && arguments != null
                                && arguments.length == 1
                                && arguments[0] instanceof AdminOperationLog) {
                            FaultMode mode = faults.adminLogMode.get();
                            if (mode == FaultMode.RETURN_ZERO) return false;
                            if (mode == FaultMode.THROW) {
                                throw new IllegalStateException("injected admin log failure");
                            }
                        }
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }

        @Bean
        AdminOperationLogger adminOperationLogger(
                AdminOperationLogService adminOperationLogService,
                AdminAuthContext adminAuthContext,
                ObjectMapper objectMapper) {
            return new AdminOperationLogger(
                    adminOperationLogService, adminAuthContext, objectMapper);
        }

        @Bean
        ProfileImportPromptManagementService profileImportPromptManagementService(
                AiProfileImportPromptTemplateMapper templateMapper,
                AiProfileImportPromptVersionMapper versionMapper,
                AiProfileImportPromptAuditMapper auditMapper,
                AiProfileImportConfigMapper configMapper,
                ProfileImportPromptRenderer renderer,
                ProfileImportPromptTester tester,
                ProfileImportPromptFixtureCatalog fixtureCatalog,
                ProfileImportConfigService configService,
                AdminAuthContext adminAuthContext,
                AdminOperationLogger operationLogger,
                TransactionTemplate transactionTemplate) {
            return new ProfileImportPromptManagementServiceImpl(
                    templateMapper,
                    versionMapper,
                    auditMapper,
                    configMapper,
                    renderer,
                    tester,
                    fixtureCatalog,
                    configService,
                    adminAuthContext,
                    operationLogger,
                    transactionTemplate);
        }

        @Bean
        ProfileImportPromptRuntimeResolver profileImportPromptRuntimeResolver(
                AiProfileImportPromptTemplateMapper templateMapper,
                AiProfileImportPromptVersionMapper versionMapper,
                ProfileImportPromptRenderer renderer) {
            return new ProfileImportPromptRuntimeResolverImpl(
                    templateMapper, versionMapper, renderer);
        }
    }

    private enum FaultMode {
        NORMAL,
        RETURN_ZERO,
        THROW
    }

    private enum FaultTarget {
        PROMPT_AUDIT,
        ADMIN_LOG
    }

    static final class PromptGovernanceFaults {
        private final AtomicReference<FaultMode> promptAuditMode =
                new AtomicReference<>(FaultMode.NORMAL);
        private final AtomicReference<FaultMode> adminLogMode =
                new AtomicReference<>(FaultMode.NORMAL);

        private void reset() {
            promptAuditMode.set(FaultMode.NORMAL);
            adminLogMode.set(FaultMode.NORMAL);
        }
    }

    private static final class SwitchablePromptAuditMapper
            implements AiProfileImportPromptAuditMapper {
        private final AiProfileImportPromptAuditMapper delegate;
        private final PromptGovernanceFaults faults;

        private SwitchablePromptAuditMapper(
                AiProfileImportPromptAuditMapper delegate,
                PromptGovernanceFaults faults) {
            this.delegate = delegate;
            this.faults = faults;
        }

        @Override
        public int insertAudit(AiProfileImportPromptAudit audit) {
            return switch (faults.promptAuditMode.get()) {
                case NORMAL -> delegate.insertAudit(audit);
                case RETURN_ZERO -> 0;
                case THROW -> throw new IllegalStateException("injected prompt audit failure");
            };
        }

        @Override
        public List<AiProfileImportPromptAudit> selectRecent(Integer limit) {
            return delegate.selectRecent(limit);
        }
    }

    static final class ControlledFixturePromptTester implements ProfileImportPromptTester {
        private final ProfileImportPromptRenderer renderer;
        private final ProfileImportPromptFixtureCatalog fixtureCatalog;
        private final AtomicReference<TestGate> nextGate = new AtomicReference<>();

        private ControlledFixturePromptTester(
                ProfileImportPromptRenderer renderer,
                ProfileImportPromptFixtureCatalog fixtureCatalog) {
            this.renderer = renderer;
            this.fixtureCatalog = fixtureCatalog;
        }

        private void reset() {
            nextGate.set(null);
        }

        private TestGate pauseNextExecution() {
            TestGate gate = new TestGate(new CountDownLatch(1), new CountDownLatch(1));
            if (!nextGate.compareAndSet(null, gate)) {
                throw new IllegalStateException("fixture tester gate already armed");
            }
            return gate;
        }

        @Override
        public ProfileImportPromptTestResultRespDTO execute(
                AiProfileImportPromptTemplate template,
                AiProfileImportPromptVersion version,
                ProfileImportRuntimeConfig runtimeConfig) {
            TestGate gate = nextGate.getAndSet(null);
            if (gate != null) {
                gate.entered().countDown();
                try {
                    if (!gate.release().await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("fixture tester gate timed out");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("fixture tester interrupted", error);
                }
            }
            ProfileImportPromptFixtureCatalog.Fixture fixture =
                    fixtureCatalog.load(template.getScene());
            var runtime = renderer.render(template, version);
            ProfileImportPromptTestResultRespDTO result =
                    new ProfileImportPromptTestResultRespDTO();
            result.setPromptVersionId(version.getPromptVersionId());
            result.setContentSha256(version.getContentSha256());
            result.setRuntimeSha256(runtime.runtimeSha256());
            result.setFixtureCode(fixture.code());
            result.setFixtureVersion(fixture.version());
            result.setFixtureSha256(fixture.sha256());
            result.setModelName(runtimeConfig.modelName());
            result.setConfigVersion(runtimeConfig.configVersion());
            result.setStatus("success");
            result.setCandidateCount("works_only".equals(template.getScene()) ? 0 : 2);
            result.setWorkCount(2);
            result.setElapsedMs(12L);
            return result;
        }
    }

    private record TestGate(CountDownLatch entered, CountDownLatch release) {
    }

    private record TestedDraft(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version) {
    }

    private record PublishOutcome(boolean success, Integer errorCode) {
    }

    private record LockWaitObservation(
            String trxId,
            String trxState,
            Long mysqlThreadId,
            String trxQuery,
            String processlistUser) {
    }

    private record PublishedHistory(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion v1,
            AiProfileImportPromptVersion v2) {
    }

    private record ExpectedTestBinding(
            String contentSha256,
            String runtimeSha256,
            String fixtureCode,
            String fixtureVersion,
            String fixtureSha256,
            String modelName,
            Integer configVersion) {
    }

    private record RestoreTransactionState(
            Long activeVersionId,
            Long draftVersionId,
            Integer templateVersion,
            String v1Lifecycle,
            String v2Lifecycle,
            Integer v1Version,
            Integer v2Version,
            Integer restoreAuditCount,
            Integer restoreOperationLogCount) {
    }

    private static final class PromptGovernanceDatabase implements AutoCloseable {
        private final MySQLContainer<?> mysql;
        private final DriverManagerDataSource dataSource;
        private final DriverManagerDataSource lockObserverDataSource;

        private PromptGovernanceDatabase() throws Exception {
            mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                    .withDatabaseName("kaipai_prompt_governance_test")
                    .withUsername("kaipai_test")
                    .withPassword("kaipai_test");
            dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            lockObserverDataSource = new DriverManagerDataSource();
            lockObserverDataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            try {
                mysql.start();
                String jdbcUrl = mysql.getJdbcUrl();
                dataSource.setUrl(jdbcUrl
                        + (jdbcUrl.contains("?") ? "&" : "?")
                        + "allowMultiQueries=true");
                dataSource.setUsername(mysql.getUsername());
                dataSource.setPassword(mysql.getPassword());
                lockObserverDataSource.setUrl(jdbcUrl);
                lockObserverDataSource.setUsername("root");
                lockObserverDataSource.setPassword(mysql.getPassword());
                try (Connection connection = dataSource.getConnection()) {
                    createLegacyPreState(connection);
                    for (String migration : MIGRATIONS) {
                        executeSql(connection, readMigration(migration));
                    }
                }
            } catch (Exception | Error error) {
                mysql.stop();
                throw error;
            }
        }

        private DataSource dataSource() {
            return dataSource;
        }

        private DataSource lockObserverDataSource() {
            return lockObserverDataSource;
        }

        @Override
        public void close() {
            mysql.stop();
        }

        private static void createLegacyPreState(Connection connection) throws SQLException {
            executeSql(connection,
                    "CREATE TABLE user (user_id BIGINT NOT NULL AUTO_INCREMENT, "
                            + "real_auth_status TINYINT NOT NULL DEFAULT 0, "
                            + "PRIMARY KEY (user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                            + "CREATE TABLE actor_profile ("
                            + "profile_id BIGINT NOT NULL AUTO_INCREMENT, "
                            + "user_id BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0, "
                            + "PRIMARY KEY (profile_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
        }

        private static String readMigration(String migration) throws IOException {
            return Files.readString(
                    Path.of("src/main/resources/db/migration").resolve(migration),
                    StandardCharsets.UTF_8);
        }
    }
}
