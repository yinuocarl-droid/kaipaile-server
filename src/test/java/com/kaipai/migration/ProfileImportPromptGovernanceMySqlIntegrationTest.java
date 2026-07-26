package com.kaipai.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.kaipai.mapper.ai.AiProfileImportConfigMapper;
import com.kaipai.mapper.ai.AiProfileImportPromptTemplateMapper;
import com.kaipai.mapper.ai.AiProfileImportPromptVersionMapper;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import com.kaipai.service.ai.profileimport.ProfileImportPromptContract;
import com.kaipai.service.ai.profileimport.ProfileImportPromptPolicy;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRenderer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringJUnitConfig(ProfileImportPromptGovernanceMySqlIntegrationTest.TestConfiguration.class)
class ProfileImportPromptGovernanceMySqlIntegrationTest {

    private static final String V001 =
            "V20260726_001__ai_profile_import_prompt_template_governance.sql";
    private static final String[] MIGRATIONS = {
        "V20260331_001__platform_admin_baseline.sql",
        "V20260331_002__platform_admin_governance_alignment.sql",
        "V20260723_004__ai_profile_import_governance.sql",
        "V20260724_001__ai_profile_import_request_scene.sql",
        V001
    };
    private static final PromptGovernanceDatabase DATABASE = startDatabase();

    private final JdbcTemplate jdbc;
    private final AiProfileImportPromptTemplateMapper templateMapper;
    private final AiProfileImportPromptVersionMapper versionMapper;
    private final AiProfileImportConfigMapper configMapper;
    private final ProfileImportPromptRenderer renderer;

    @Autowired
    ProfileImportPromptGovernanceMySqlIntegrationTest(
            JdbcTemplate jdbc,
            AiProfileImportPromptTemplateMapper templateMapper,
            AiProfileImportPromptVersionMapper versionMapper,
            AiProfileImportConfigMapper configMapper,
            ProfileImportPromptRenderer renderer) {
        this.jdbc = jdbc;
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.configMapper = configMapper;
        this.renderer = renderer;
    }

    @BeforeEach
    void resetBootstrapAndConfigFixtures() throws Exception {
        jdbc.execute("DROP TABLE IF EXISTS assert_ai_profile_import_prompt_bootstrap");
        jdbc.update("UPDATE ai_profile_import_prompt_template "
                + "SET active_version_id=NULL, draft_version_id=NULL");
        jdbc.update("DELETE FROM ai_profile_import_prompt_audit");
        jdbc.update("DELETE FROM ai_profile_import_request_audit");
        jdbc.update("DELETE FROM ai_profile_import_config_audit");
        jdbc.update("DELETE FROM admin_operation_log");
        jdbc.update("DELETE FROM ai_profile_import_prompt_version");
        jdbc.update("DELETE FROM ai_profile_import_prompt_template");
        jdbc.update("DELETE FROM ai_profile_import_config WHERE provider_code='deepseek'");
        executeSql(bootstrapBlock(v001Sql()));
        jdbc.update("INSERT INTO ai_profile_import_config "
                + "(provider_code, display_name, enabled, endpoint, model_name, "
                + "connect_timeout_ms, read_timeout_ms, max_input_chars, max_output_tokens, "
                + "per_user_daily_limit) VALUES "
                + "('deepseek', 'DeepSeek test', 0, "
                + "'https://api.deepseek.com/chat/completions', 'deepseek-chat', "
                + "5000, 60000, 20000, 8000, 20)");
    }

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
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
    void draftUpdatePersistsEditableLabelButKeepsCodeOwnedContractVersions() {
        AiProfileImportPromptTemplate template =
                templateMapper.selectByCodeForUpdate("full_profile");
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

        assertEquals(1, versionMapper.updateDraftIfExpected(draft, expectedVersion));

        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT version_label, system_prompt_body, repair_prompt_body, "
                        + "content_sha256, change_summary, schema_version, contract_version "
                        + "FROM ai_profile_import_prompt_version WHERE prompt_version_id=?",
                draft.getPromptVersionId());
        assertEquals("bootstrap-v1-edited", stored.get("version_label"));
        assertEquals("edited system\n", stored.get("system_prompt_body"));
        assertEquals("edited repair", stored.get("repair_prompt_body"));
        assertEquals(editedContentSha, stored.get("content_sha256"));
        assertEquals("quality adjustment", stored.get("change_summary"));
        assertEquals("profile-import-json-v1", stored.get("schema_version"));
        assertEquals("profile-import-contract-v1", stored.get("contract_version"));
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
    @MapperScan("com.kaipai.mapper.ai")
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            return DATABASE.dataSource();
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(new GlobalConfig());
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
    }

    private static final class PromptGovernanceDatabase implements AutoCloseable {
        private final MySQLContainer<?> mysql;
        private final DriverManagerDataSource dataSource;

        private PromptGovernanceDatabase() throws Exception {
            mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                    .withDatabaseName("kaipai_prompt_governance_test")
                    .withUsername("kaipai_test")
                    .withPassword("kaipai_test");
            dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            try {
                mysql.start();
                String jdbcUrl = mysql.getJdbcUrl();
                dataSource.setUrl(jdbcUrl
                        + (jdbcUrl.contains("?") ? "&" : "?")
                        + "allowMultiQueries=true");
                dataSource.setUsername(mysql.getUsername());
                dataSource.setPassword(mysql.getPassword());
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
