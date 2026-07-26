package com.kaipai.service.ai.profileimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kaipai.common.exception.BizException;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import com.kaipai.model.ai.entity.AiProfileImportRequestAudit;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiProfileImportPersistenceShapeTest {
    @Test
    void promptGovernanceMigrationHasOwnedPointersBootstrapDraftsAndNoSensitiveAuditColumns()
            throws Exception {
        Path path = Path.of(
                "src/main/resources/db/migration/"
                        + "V20260726_001__ai_profile_import_prompt_template_governance.sql");
        assertTrue(Files.exists(path));
        String sql = normalizeSql(Files.readString(path));
        for (String table : List.of(
                "ai_profile_import_prompt_template",
                "ai_profile_import_prompt_version",
                "ai_profile_import_prompt_audit")) {
            assertTrue(sql.contains("create table " + table));
        }
        assertTrue(sql.contains("foreign key (template_id, active_version_id)"));
        assertTrue(sql.contains("foreign key (template_id, draft_version_id)"));
        assertTrue(sql.contains("generated always as"));
        assertTrue(sql.contains("unique key uk_ai_profile_import_prompt_open_draft"));
        assertTrue(sql.contains("'full_profile'"));
        assertTrue(sql.contains("'works_only'"));
        assertTrue(tableBlock(sql, "ai_profile_import_prompt_version")
                .contains("test_status varchar(32) not null default 'untested'"));
        String seed = seedInsertBlock(sql);
        assertEquals(2, count(seed, "'bootstrap-v1'"));
        assertEquals(2, count(seed, "'draft'"));
        assertEquals(2, count(seed, "'untested'"));
        assertFalse(seed.contains("'released'"));
        assertFalse(seed.contains("active_version_id ="));
        String auditBlock = tableBlock(sql, "ai_profile_import_prompt_audit");
        for (String publishBinding : List.of(
                "content_sha256 char(64) default null",
                "runtime_sha256 char(64) default null",
                "schema_version varchar(64) default null",
                "contract_version varchar(64) default null",
                "fixture_code varchar(64) default null",
                "fixture_version varchar(64) default null",
                "fixture_sha256 char(64) default null",
                "model_name varchar(128) default null",
                "config_version int default null",
                "test_operator_id bigint default null",
                "tested_at datetime default null",
                "reason_code varchar(64) not null")) {
            assertTrue(auditBlock.contains(publishBinding), publishBinding);
        }
        for (String forbidden : List.of(
                "system_prompt_body", "repair_prompt_body", "raw_text", "source_text",
                "fixture_body", "api_key", "secret", "change_summary", "free_reason")) {
            assertFalse(auditBlock.contains(forbidden), forbidden);
        }
    }

    @Test
    void promptGovernanceEntitiesExposeOnlyTheOwnedPersistenceFields() throws Exception {
        Class<?> template = Class.forName(
                "com.kaipai.model.ai.entity.AiProfileImportPromptTemplate");
        Class<?> version = Class.forName(
                "com.kaipai.model.ai.entity.AiProfileImportPromptVersion");
        Class<?> audit = Class.forName(
                "com.kaipai.model.ai.entity.AiProfileImportPromptAudit");

        assertFields(template,
                "templateId", "templateCode", "scene", "displayName",
                "activeVersionId", "draftVersionId");
        assertFields(version,
                "promptVersionId", "templateId", "versionNo", "versionLabel",
                "lifecycleStatus", "systemPromptBody", "repairPromptBody", "schemaVersion",
                "contractVersion", "contentSha256", "changeSummary", "testStatus",
                "testedContentSha256", "testedRuntimeSha256", "testFixtureCode",
                "testFixtureVersion", "testFixtureSha256", "testedModelName",
                "testedConfigVersion", "testCandidateCount", "testWorkCount", "testElapsedMs",
                "testErrorCode", "testedBy", "testedAt", "releasedBy", "releasedAt",
                "openDraftTemplateId");
        assertFields(audit,
                "promptAuditId", "templateId", "promptVersionId", "actionCode",
                "fromVersionId", "toVersionId", "contentSha256", "runtimeSha256",
                "schemaVersion", "contractVersion", "fixtureCode", "fixtureVersion",
                "fixtureSha256", "modelName", "configVersion", "testOperatorId", "testedAt",
                "operatorId", "operatorName", "reasonCode", "resultStatus", "errorCode",
                "message");
        assertNoFields(template, "systemPromptBody", "repairPromptBody");
        assertNoFields(audit,
                "systemPromptBody", "repairPromptBody", "rawText", "sourceText",
                "fixtureBody", "apiKey", "secret", "changeSummary", "freeReason");
    }

    @Test
    void requestAuditLineageIsNullableAndContainsNoPromptBody() throws Exception {
        for (String field : List.of(
                "promptTemplateCode", "promptVersionId", "promptVersionNo",
                "promptSchemaVersion", "promptContractVersion", "promptRuntimeSha256")) {
            assertNotNull(AiProfileImportRequestAudit.class.getDeclaredField(field));
        }
        String sql = normalizeSql(Files.readString(Path.of(
                "src/main/resources/db/migration/"
                        + "V20260726_001__ai_profile_import_prompt_template_governance.sql")));
        String requestAuditAlter = tableBlock(sql, "alter table ai_profile_import_request_audit");
        for (String lineageColumn : List.of(
                "add column prompt_template_code varchar(64) null",
                "add column prompt_version_id bigint null",
                "add column prompt_version_no int null",
                "add column prompt_schema_version varchar(64) null",
                "add column prompt_contract_version varchar(64) null",
                "add column prompt_runtime_sha256 char(64) null")) {
            assertTrue(requestAuditAlter.contains(lineageColumn), lineageColumn);
        }
        for (String forbidden : List.of(
                "raw_text", "source_text", "raw_response", "response_body", "api_key",
                "secret", "system_prompt_body", "repair_prompt_body", "prompt_body")) {
            assertFalse(requestAuditAlter.contains(forbidden), forbidden);
        }
        assertNoFields(AiProfileImportRequestAudit.class,
                "systemPromptBody", "repairPromptBody", "promptBody");
    }

    @Test
    void requestAuditAndConfigCannotPersistSourceOrPlainSecret() throws Exception {
        assertNoFields(AiProfileImportRequestAudit.class,
                "rawText", "response", "rawResponse", "sourceText", "evidence");
        assertNoFields(AiProfileImportConfig.class, "apiKey", "secret");
        String sql = governanceMigrationSql();
        for (String forbidden : List.of("raw_text", "raw_response", "source_text", "api_key")) {
            assertFalse(sql.contains(forbidden));
        }
        assertTrue(sql.contains("unique key uk_ai_profile_import_request_user_request"));
    }

    @Test
    void requestAuditSceneUsesAForwardOnlyMigrationWithoutRewritingV004() throws Exception {
        assertNotNull(AiProfileImportRequestAudit.class.getDeclaredField("scene"));
        assertFalse(governanceMigrationSql().contains("scene varchar(32)"));

        Path migration = Path.of(
                "src/main/resources/db/migration/V20260724_001__ai_profile_import_request_scene.sql");
        assertTrue(Files.exists(migration), "scene must be added by a forward-only migration");
        String sql = normalizeSql(Files.readString(migration));
        int addNullable = sql.indexOf("add column scene varchar(32) null after model_name");
        int backfill = sql.indexOf(
                "update ai_profile_import_request_audit set scene = 'legacy_unknown' where scene is null");
        int makeRequired = sql.indexOf("modify column scene varchar(32) not null");
        assertTrue(addNullable >= 0, "scene must first be added as nullable");
        assertTrue(backfill > addNullable, "legacy rows must be backfilled after the additive DDL");
        assertTrue(makeRequired > backfill, "NOT NULL must be enforced only after the backfill");
    }

    @Test
    void sceneGuardAcceptsOnlySupportedScenesAndRejectsApplyMismatch() throws Exception {
        Class<?> guard = Class.forName("com.kaipai.service.ai.profileimport.ProfileImportSceneGuard");
        assertEquals("works_only", guard.getMethod("requireSupported", String.class)
                .invoke(null, " works_only "));
        InvocationTargetException mismatch = assertThrows(InvocationTargetException.class,
                () -> guard.getMethod("requireMatches", String.class, String.class)
                        .invoke(null, "full_profile", "works_only"));
        BizException error = (BizException) mismatch.getCause();
        assertEquals(46008, error.getCode());
        assertTrue(error.getMessage().contains("场景"));
    }

    @Test
    void permissionMigrationContainsPageAndEveryGovernedAction() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260723_005__ai_profile_import_permission_alignment.sql"));
        for (String code : List.of(
                "page.system.ai-profile-import",
                "action.system.ai-profile-import.update",
                "action.system.ai-profile-import.secret",
                "action.system.ai-profile-import.test",
                "action.system.ai-profile-import.audit")) {
            assertTrue(sql.contains(code));
        }
    }

    private String governanceMigrationSql() throws Exception {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/V20260723_004__ai_profile_import_governance.sql"))
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeSql(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int count(String text, String token) {
        int occurrences = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            occurrences++;
            offset += token.length();
        }
        return occurrences;
    }

    private String tableBlock(String sql, String declaration) {
        String marker = declaration.startsWith("alter table ")
                ? declaration
                : "create table " + declaration;
        int start = sql.indexOf(marker);
        assertTrue(start >= 0, "missing SQL block: " + marker);
        int end = sql.indexOf(';', start);
        assertTrue(end > start, "unterminated SQL block: " + marker);
        return sql.substring(start, end);
    }

    private String seedInsertBlock(String sql) {
        int start = sql.indexOf("set @prompt_hash_domain");
        int end = sql.indexOf("create table assert_ai_profile_import_prompt_bootstrap", start);
        assertTrue(start >= 0, "missing prompt seed block");
        assertTrue(end > start, "missing prompt bootstrap assertion boundary");
        return sql.substring(start, end);
    }

    private void assertFields(Class<?> type, String... names) throws Exception {
        for (String name : names) {
            assertNotNull(type.getDeclaredField(name), type.getSimpleName() + "." + name);
        }
    }

    private void assertNoFields(Class<?> type, String... names) {
        Set<String> fields = new HashSet<>();
        for (var field : type.getDeclaredFields()) fields.add(field.getName());
        for (String name : names) assertFalse(fields.contains(name));
    }
}
