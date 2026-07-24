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

    private void assertNoFields(Class<?> type, String... names) {
        Set<String> fields = new HashSet<>();
        for (var field : type.getDeclaredFields()) fields.add(field.getName());
        for (String name : names) assertFalse(fields.contains(name));
    }
}
