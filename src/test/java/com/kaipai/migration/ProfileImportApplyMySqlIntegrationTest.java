package com.kaipai.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaipai.common.handler.MetaObjectHandlerConfig;
import com.kaipai.common.result.PageResult;
import com.kaipai.integration.ai.profileimport.DeepSeekProfileTextExtractor;
import com.kaipai.mapper.ai.AiProfileImportRequestAuditMapper;
import com.kaipai.model.actor.dto.ActorWorkQueryDTO;
import com.kaipai.model.actor.dto.ActorWorkRespDTO;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import com.kaipai.model.ai.dto.ProfileImportApplyRespDTO;
import com.kaipai.model.ai.dto.ProfileImportCapabilityRespDTO;
import com.kaipai.model.ai.dto.ProfileImportExtractReqDTO;
import com.kaipai.model.ai.dto.ProfileImportExtractionRespDTO;
import com.kaipai.service.actor.ActorMediaAssetOwnershipVerifier;
import com.kaipai.service.actor.ActorWorkService;
import com.kaipai.service.actor.impl.ActorWorkServiceImpl;
import com.kaipai.service.ai.ProfileImportApplyService;
import com.kaipai.service.ai.ProfileImportConfigService;
import com.kaipai.service.ai.ProfileImportRateLimiter;
import com.kaipai.service.ai.ProfileImportRuntimeConfig;
import com.kaipai.service.ai.ProfileImportService;
import com.kaipai.service.ai.impl.ActorProfileImportWriter;
import com.kaipai.service.ai.impl.ProfileImportApplyServiceImpl;
import com.kaipai.service.ai.impl.ProfileImportServiceImpl;
import com.kaipai.service.ai.profileimport.ProfileImportCandidateProofService;
import com.kaipai.service.ai.profileimport.ProfileImportHttpTransport;
import com.kaipai.service.ai.profileimport.ProfileImportPayloadHasher;
import com.kaipai.service.ai.profileimport.ProfileImportSchemaValidator;
import com.kaipai.service.ai.profileimport.ProfileImportWorkMatcher;
import com.kaipai.service.ai.profileimport.ProfileImportWorkApplyGuard;
import com.kaipai.service.ai.profileimport.ProfileImportWorkMatchSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringJUnitConfig(ProfileImportApplyMySqlIntegrationTest.TestConfiguration.class)
class ProfileImportApplyMySqlIntegrationTest {

    private static final long GOLDEN_USER_ID = 89101L;
    private static final long ROLLBACK_USER_ID = 89102L;
    private static final long CONCURRENT_USER_ID = 89103L;
    private static final long DIFFERENT_REQUEST_USER_ID = 89104L;
    private static final long MERGE_USER_ID = 89105L;
    private static final long PROFILE_USER_ID = 89106L;
    private static final long PROFILE_ID = 9906L;
    private static final int GOLDEN_MEDIA_PLACEHOLDER_COUNT = 30;
    private static final String GOLDEN_RESOURCE =
            "/profile-migration/wang-huohuo-works-golden.json";
    private static final Map<String, Long> REQUIRED_CATEGORY_COUNTS =
            Map.of("aired", 14L, "upcoming", 6L, "stage", 3L, "horizontal", 6L);
    private static final ObjectMapper FIXTURE_MAPPER = new ObjectMapper();
    private static final GoldenFixture GOLDEN = readGoldenFixture();
    private static final String GOLDEN_EXTRACTION_JSON = buildGoldenExtractionJson();
    private static final String GOLDEN_SANITIZED_EVIDENCE = buildGoldenSanitizedEvidence();
    private static final CareerProfileMySqlTestSupport DATABASE = startDatabase();
    private static final String MIGRATED_LEGACY_SCENE = readMigratedLegacyScene(DATABASE);

    private final ProfileImportService importService;
    private final ProfileImportApplyService applyService;
    private final ActorWorkService actorWorkService;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Autowired
    ProfileImportApplyMySqlIntegrationTest(
            ProfileImportService importService,
            ProfileImportApplyService applyService,
            ActorWorkService actorWorkService,
            DataSource dataSource,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.importService = importService;
        this.applyService = applyService;
        this.actorWorkService = actorWorkService;
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_second_profile_import_work");
        jdbc.execute("DROP TRIGGER IF EXISTS pause_first_profile_import_work");
        jdbc.execute("DROP TABLE IF EXISTS profile_import_insert_probe");
        jdbc.execute("DROP TABLE IF EXISTS profile_import_concurrency_probe");
        jdbc.update("DELETE FROM ai_profile_import_request_audit");
        jdbc.update("DELETE FROM ai_profile_import_config_audit");
        jdbc.update("DELETE FROM ai_profile_import_config");
        DATABASE.resetData();
        DATABASE.insertUser(GOLDEN_USER_ID);
        DATABASE.insertUser(ROLLBACK_USER_ID);
        DATABASE.insertUser(CONCURRENT_USER_ID);
        DATABASE.insertUser(DIFFERENT_REQUEST_USER_ID);
        DATABASE.insertUser(MERGE_USER_ID);
        DATABASE.insertUser(PROFILE_USER_ID);
    }

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @Test
    void forwardSceneMigrationBackfillsLegacyAuditRows() {
        assertEquals("legacy_unknown", MIGRATED_LEGACY_SCENE);
    }

    @Test
    void freshGoldenExtractionsCreateThenMatchAndSkipExactlyTwentyNineWorks() {
        assertGoldenFixtureIsExplicitAndStable();

        ExtractionCycle first = extractFresh(GOLDEN_USER_ID, "wang-huohuo-apply-1");
        assertTrue(first.response().getProfileCandidates().isEmpty());
        assertEquals(0, first.response().getProfileCandidateCount());
        assertEquals(29, first.response().getWorkCandidates().size());
        assertTrue(first.response().getWorkCandidates().stream().allMatch(candidate ->
                "new".equals(candidate.getMatchStatus())
                        && candidate.getMatchedExperienceId() == null
                        && "create".equals(candidate.getSelectedAction())
                        && List.of("create").equals(candidate.getAllowedActions())));

        ProfileImportApplyRespDTO firstResult =
                applyService.apply(GOLDEN_USER_ID, reviewedApply(first));

        assertApplySummary(firstResult, 29, 0, 0);
        assertDatabaseContainsEveryGoldenWork(GOLDEN_USER_ID);
        assertRealActorWorkServicePagination(GOLDEN_USER_ID);

        ContextVersion current = currentContext(GOLDEN_USER_ID);
        assertEquals(0L, current.profileVersion());
        assertEquals(1L, current.workLibraryVersion());

        ExtractionCycle second = extractFresh(GOLDEN_USER_ID, "wang-huohuo-apply-2");
        assertNotEquals(first.auditId(), second.auditId());
        assertNotEquals(first.response().getRequestId(), second.response().getRequestId());
        assertEquals(current, second.context());
        assertAuditContext(second);
        assertFreshProofs(first.response(), second.response());

        Map<String, Long> activeIdsByProject = activeIdsByProject(GOLDEN_USER_ID);
        assertEquals(29, activeIdsByProject.size());
        assertTrue(second.response().getWorkCandidates().stream().allMatch(candidate ->
                "exact_match".equals(candidate.getMatchStatus())
                        && activeIdsByProject.get(candidate.getProjectName())
                                .equals(candidate.getMatchedExperienceId())
                        && "skip".equals(candidate.getSelectedAction())
                        && List.of("skip").equals(candidate.getAllowedActions())));

        ProfileImportApplyRespDTO secondResult =
                applyService.apply(GOLDEN_USER_ID, reviewedApply(second));

        assertApplySummary(secondResult, 0, 29, 0);
        assertEquals(29L, countActiveWorks(GOLDEN_USER_ID));
        assertEquals(29, activeIdsByProject(GOLDEN_USER_ID).size());
        assertEquals("success", auditValue(second.auditId(), "apply_status", String.class));
        assertEquals(1L, currentContext(GOLDEN_USER_ID).workLibraryVersion());
        assertDatabaseContainsEveryGoldenWork(GOLDEN_USER_ID);
    }

    @Test
    void goldenExtractionPreservesProfileFactsAndRejectsUnconfirmedInferredGender() {
        ExtractionCycle extraction = extractFresh(
                GOLDEN_USER_ID, "wang-huohuo-profile-review", "full_profile");

        assertGoldenProfileCandidates(extraction.response());
        assertEquals(GOLDEN_MEDIA_PLACEHOLDER_COUNT,
                extraction.response().getIgnoredMediaPlaceholderCount());
        assertEquals(0L, countActiveMediaAssets(GOLDEN_USER_ID));

        ProfileImportExtractionRespDTO.ProfileCandidate gender =
                profileCandidate(extraction.response(), "gender");
        assertEquals("female", gender.getCandidateValue());
        assertEquals("inferred_from_roles", gender.getSourceType());
        assertFalse(gender.isSelected());
        assertFalse(gender.isConfirmed());
        assertTrue(gender.isRequiresExplicitConfirmation());

        ProfileImportApplyReqDTO request = reviewedProfileApply(extraction, false);
        request.setWorks(new ArrayList<>());
        com.kaipai.common.exception.BizException error = assertThrows(
                com.kaipai.common.exception.BizException.class,
                () -> applyService.apply(GOLDEN_USER_ID, request));

        assertEquals(46011, error.getCode());
        assertEquals(0L, countActiveWorks(GOLDEN_USER_ID));
        assertEquals(0L, countActiveMediaAssets(GOLDEN_USER_ID));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_profile WHERE user_id = ? AND deleted = 0",
                Long.class,
                GOLDEN_USER_ID));
    }

    @Test
    void confirmedGoldenProfileFactsPersistWithoutChangingRealNameCityOrCreatingMedia() {
        DATABASE.insertProfile(PROFILE_ID, PROFILE_USER_ID, null, 0L);
        jdbc.update(
                """
                UPDATE actor_profile
                SET nick_name = '旧公开名', real_name = '实名保留值', gender = 0,
                    age = 99, height = 165, location_city = '杭州',
                    birth_year = 1988, birth_month = 5, birth_day = 16,
                    birth_precision = 'day',
                    avatar_url = 'https://legacy.test/avatar.jpg'
                WHERE actor_profile_id = ?
                """,
                PROFILE_ID);
        ExtractionCycle extraction = extractFresh(
                PROFILE_USER_ID, "wang-huohuo-profile-confirmed", "full_profile");
        assertGoldenProfileCandidates(extraction.response());

        ProfileImportApplyRespDTO result = applyService.apply(
                PROFILE_USER_ID, reviewedProfileApply(extraction, true));

        assertApplySummary(result, 0, 0, 0);
        Map<String, Object> profile = jdbc.queryForMap(
                """
                SELECT nick_name, real_name, gender, age, height, weight, location_city,
                       origin_place, school_name, major_name, birth_year, birth_month,
                       birth_day, birth_precision, language_tags_json, specialty_tags_json,
                       role_type_tags_json, professional_ability_tags_json, profile_status,
                       avatar_url
                FROM actor_profile
                WHERE user_id = ? AND deleted = 0
                """,
                PROFILE_USER_ID);
        assertEquals("王火火", profile.get("nick_name"));
        assertEquals("实名保留值", profile.get("real_name"));
        assertEquals(2, profile.get("gender"));
        assertEquals(expectedGoldenAge(), profile.get("age"));
        assertEquals(170, profile.get("height"));
        assertEquals(45, profile.get("weight"));
        assertEquals("杭州", profile.get("location_city"));
        assertEquals("中国香港", profile.get("origin_place"));
        assertEquals("浙江传媒学院", profile.get("school_name"));
        assertEquals("表演", profile.get("major_name"));
        assertEquals(2004, profile.get("birth_year"));
        assertEquals(9, profile.get("birth_month"));
        assertNull(profile.get("birth_day"));
        assertEquals("month", profile.get("birth_precision"));
        assertEquals(List.of("粤语", "英语", "东北话"),
                readStringList((String) profile.get("language_tags_json")));
        assertEquals(List.of("表演", "主持", "唱歌", "跳舞", "架子鼓", "羽毛球", "排球", "跑步", "游泳"),
                readStringList((String) profile.get("specialty_tags_json")));
        assertEquals(List.of("悲情女主", "复仇大女主", "小白花", "绿茶"),
                readStringList((String) profile.get("role_type_tags_json")));
        assertEquals(List.of(
                        "普通话标准", "台词功底扎实", "同期声", "眼神戏好", "情感戏强",
                        "爆发力强", "打戏", "威亚", "配合度高"),
                readStringList((String) profile.get("professional_ability_tags_json")));
        assertEquals(1, profile.get("profile_status"));
        assertEquals("https://legacy.test/avatar.jpg", profile.get("avatar_url"));
        assertEquals(new ContextVersion(1L, 0L), currentContext(PROFILE_USER_ID));
        assertEquals(0L, countActiveWorks(PROFILE_USER_ID));
        assertEquals(0L, countActiveMediaAssets(PROFILE_USER_ID));
    }

    @Test
    void secondWorkInsertFailureRollsBackProfileWorksAndAuditApplyState() {
        ExtractionCycle extraction = extractFresh(ROLLBACK_USER_ID, "rollback-after-first-work");
        ProfileImportApplyReqDTO request = reviewedApply(extraction);
        request.setWorks(new ArrayList<>(request.getWorks().subList(0, 2)));
        installSecondInsertFailureTrigger();

        assertThrows(RuntimeException.class, () -> applyService.apply(ROLLBACK_USER_ID, request));

        List<String> insertProbe = jdbc.queryForList(
                "SELECT project_name FROM profile_import_insert_probe ORDER BY probe_id",
                String.class);
        assertEquals(
                List.of(GOLDEN.works().get(0).projectName(), GOLDEN.works().get(1).projectName()),
                insertProbe,
                "the non-transactional trigger proves the first insert completed before the second failed");
        assertEquals(0L, countActiveWorks(ROLLBACK_USER_ID));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_profile WHERE user_id = ? AND deleted = 0",
                Long.class,
                ROLLBACK_USER_ID));
        assertEquals("success", auditValue(extraction.auditId(), "status", String.class));
        assertNull(auditValue(extraction.auditId(), "apply_status", String.class));
        assertNull(auditValue(extraction.auditId(), "apply_payload_sha256", String.class));
        assertNull(auditValue(extraction.auditId(), "apply_result_summary_json", String.class));
        assertNull(auditValue(extraction.auditId(), "applied_at", java.time.LocalDateTime.class));
    }

    @Test
    void concurrentSameRequestRetryPersistsOnlyOneGoldenWorkSet() throws Exception {
        ExtractionCycle extraction = extractFresh(CONCURRENT_USER_ID, "concurrent-same-request");
        ProfileImportApplyReqDTO request = reviewedApply(extraction);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection userBlocker = dataSource.getConnection()) {
            lockUser(userBlocker, CONCURRENT_USER_ID);
            Future<ProfileImportApplyRespDTO> first = executor.submit(
                    () -> applyService.apply(CONCURRENT_USER_ID, request));
            awaitAuditRowLock(extraction.auditId());
            Future<ProfileImportApplyRespDTO> retry = executor.submit(
                    () -> applyService.apply(CONCURRENT_USER_ID, request));

            assertThrows(TimeoutException.class, () -> retry.get(300, TimeUnit.MILLISECONDS));
            userBlocker.rollback();

            ProfileImportApplyRespDTO firstResult = first.get(30, TimeUnit.SECONDS);
            ProfileImportApplyRespDTO retryResult = retry.get(30, TimeUnit.SECONDS);
            assertEquals(firstResult.getSummary(), retryResult.getSummary());
            assertApplySummary(firstResult, 29, 0, 0);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_profile WHERE user_id = ? AND deleted = 0",
                Long.class,
                CONCURRENT_USER_ID));
        assertEquals(29L, countActiveWorks(CONCURRENT_USER_ID));
        assertEquals(29L, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT experience_id) FROM actor_experience "
                        + "WHERE user_id = ? AND deleted = 0",
                Long.class,
                CONCURRENT_USER_ID));
        assertEquals(1L, currentContext(CONCURRENT_USER_ID).workLibraryVersion());
        assertEquals("success", auditValue(extraction.auditId(), "apply_status", String.class));
    }

    @Test
    void concurrentDifferentRequestsForOneUserSerializeAndRejectStaleContext() throws Exception {
        ExtractionCycle firstExtraction =
                extractFresh(DIFFERENT_REQUEST_USER_ID, "concurrent-different-request-1");
        ExtractionCycle secondExtraction =
                extractFresh(DIFFERENT_REQUEST_USER_ID, "concurrent-different-request-2");
        assertEquals(firstExtraction.context(), secondExtraction.context());
        ProfileImportApplyReqDTO firstRequest = reviewedApply(firstExtraction);
        ProfileImportApplyReqDTO secondRequest = reviewedApply(secondExtraction);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection userBlocker = dataSource.getConnection()) {
            lockUser(userBlocker, DIFFERENT_REQUEST_USER_ID);
            Future<ProfileImportApplyRespDTO> first = executor.submit(
                    () -> applyService.apply(DIFFERENT_REQUEST_USER_ID, firstRequest));
            awaitAuditRowLock(firstExtraction.auditId());
            assertThrows(TimeoutException.class, () -> first.get(300, TimeUnit.MILLISECONDS));
            Future<ProfileImportApplyRespDTO> second = executor.submit(
                    () -> applyService.apply(DIFFERENT_REQUEST_USER_ID, secondRequest));
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));

            userBlocker.rollback();

            assertApplySummary(first.get(30, TimeUnit.SECONDS), 29, 0, 0);
            ExecutionException failure =
                    assertThrows(ExecutionException.class, () -> second.get(30, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof com.kaipai.common.exception.BizException);
            assertEquals(
                    46010,
                    ((com.kaipai.common.exception.BizException) failure.getCause()).getCode());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(29L, countActiveWorks(DIFFERENT_REQUEST_USER_ID));
        assertEquals(1L, currentContext(DIFFERENT_REQUEST_USER_ID).workLibraryVersion());
        assertEquals("success", auditValue(firstExtraction.auditId(), "apply_status", String.class));
        assertNull(auditValue(secondExtraction.auditId(), "apply_status", String.class));
        assertNull(auditValue(secondExtraction.auditId(), "apply_payload_sha256", String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"manual", "migration"})
    void mergePreservesExistingWorkProvenanceAndIncrementsVersionOnce(String sourceType) {
        GoldenWork golden = GOLDEN.works().get(0);
        long profileId = 9905L;
        long experienceId = 9951L;
        DATABASE.insertProfile(profileId, MERGE_USER_ID, null, 0L);
        DATABASE.insertBaselineWork(
                experienceId,
                MERGE_USER_ID,
                profileId,
                golden.projectName(),
                golden.roleName(),
                com.kaipai.service.actor.support.ActorWorkDeduplicationSupport.normalizeName(
                        golden.projectName()),
                com.kaipai.service.actor.support.ActorWorkDeduplicationSupport.normalizeName(
                        golden.roleName()),
                com.kaipai.service.actor.support.ActorWorkDeduplicationSupport.dedupeKey(
                        golden.projectName(), golden.roleName()));
        jdbc.update(
                "UPDATE actor_experience SET source_type = ? WHERE experience_id = ?",
                sourceType,
                experienceId);

        ExtractionCycle extraction =
                extractFresh(MERGE_USER_ID, "merge-provenance-" + sourceType);
        ProfileImportExtractionRespDTO.WorkCandidate candidate = extraction.response()
                .getWorkCandidates()
                .stream()
                .filter(item -> golden.projectName().equals(item.getProjectName()))
                .findFirst()
                .orElseThrow();
        assertEquals("field_conflict", candidate.getMatchStatus());
        assertEquals(experienceId, candidate.getMatchedExperienceId());

        ProfileImportApplyReqDTO request = reviewedApply(extraction);
        ProfileImportApplyReqDTO.ConfirmedWork merge = request.getWorks()
                .stream()
                .filter(item -> candidate.getCandidateId().equals(item.getCandidateId()))
                .findFirst()
                .orElseThrow();
        merge.setSelectedAction("merge");
        merge.setConfirmedConflictFields(new ArrayList<>(candidate.getConflictFields()));
        merge.setFinalFields(finalFields(candidate));
        request.setWorks(List.of(merge));

        ProfileImportApplyRespDTO result = applyService.apply(MERGE_USER_ID, request);

        assertApplySummary(result, 0, 0, 1);
        assertEquals(sourceType, jdbc.queryForObject(
                "SELECT source_type FROM actor_experience WHERE experience_id = ? AND deleted = 0",
                String.class,
                experienceId));
        assertEquals(1L, countActiveWorks(MERGE_USER_ID));
        assertEquals(1L, currentContext(MERGE_USER_ID).workLibraryVersion());
    }

    private ExtractionCycle extractFresh(long userId, String requestId) {
        return extractFresh(userId, requestId, "works_only");
    }

    private ExtractionCycle extractFresh(long userId, String requestId, String scene) {
        ContextVersion context = currentContext(userId);
        ProfileImportExtractReqDTO request = new ProfileImportExtractReqDTO();
        request.setRequestId(requestId);
        request.setRawText(GOLDEN_SANITIZED_EVIDENCE);
        request.setScene(scene);
        request.setProfileVersion(context.profileVersion() + 101L);
        request.setWorkLibraryVersion(context.workLibraryVersion() + 202L);

        ProfileImportExtractionRespDTO response = importService.extract(userId, request);
        assertEquals(context.profileVersion(), response.getProfileVersion());
        assertEquals(context.workLibraryVersion(), response.getWorkLibraryVersion());
        Long auditId = jdbc.queryForObject(
                "SELECT audit_id FROM ai_profile_import_request_audit "
                        + "WHERE user_id = ? AND request_id = ?",
                Long.class,
                userId,
                requestId);
        assertNotNull(auditId);
        assertEquals("success", auditValue(auditId, "status", String.class));
        assertEquals(scene, auditValue(auditId, "scene", String.class));
        assertEquals(29, response.getWorkCandidateCount());
        assertEquals(29, auditValue(auditId, "work_count", Integer.class));
        if ("works_only".equals(scene)) {
            assertTrue(response.getProfileCandidates().isEmpty());
            assertEquals(0, response.getProfileCandidateCount());
            assertEquals(0, auditValue(auditId, "candidate_count", Integer.class));
        }
        return new ExtractionCycle(context, response, auditId);
    }

    private ProfileImportApplyReqDTO reviewedProfileApply(
            ExtractionCycle extraction, boolean confirmInferredGender) {
        ProfileImportApplyReqDTO request = new ProfileImportApplyReqDTO();
        request.setRequestId(extraction.response().getRequestId());
        request.setScene("full_profile");
        request.setProfileVersion(extraction.context().profileVersion());
        request.setWorkLibraryVersion(extraction.context().workLibraryVersion());
        for (ProfileImportExtractionRespDTO.ProfileCandidate candidate
                : extraction.response().getProfileCandidates()) {
            ProfileImportApplyReqDTO.ConfirmedCandidate confirmed =
                    new ProfileImportApplyReqDTO.ConfirmedCandidate();
            confirmed.setCandidateId(candidate.getCandidateId());
            confirmed.setFieldKey(candidate.getFieldKey());
            confirmed.setCandidateValue(candidate.getCandidateValue());
            confirmed.setValue(candidate.getCandidateValue());
            confirmed.setSourceType(candidate.getSourceType());
            confirmed.setRequiresExplicitConfirmation(candidate.isRequiresExplicitConfirmation());
            confirmed.setConfirmed(!candidate.isRequiresExplicitConfirmation() || confirmInferredGender);
            confirmed.setProof(candidate.getCandidateProof());
            request.getProfileCandidates().add(confirmed);
        }
        return request;
    }

    private ProfileImportApplyReqDTO reviewedApply(ExtractionCycle extraction) {
        ProfileImportApplyReqDTO request = new ProfileImportApplyReqDTO();
        request.setRequestId(extraction.response().getRequestId());
        request.setScene("works_only");
        request.setProfileVersion(extraction.context().profileVersion());
        request.setWorkLibraryVersion(extraction.context().workLibraryVersion());
        for (ProfileImportExtractionRespDTO.WorkCandidate candidate
                : extraction.response().getWorkCandidates()) {
            ProfileImportApplyReqDTO.ConfirmedWork work = new ProfileImportApplyReqDTO.ConfirmedWork();
            work.setCandidateId(candidate.getCandidateId());
            work.setSourceType(candidate.getSourceType());
            work.setConfirmed(true);
            work.setProof(candidate.getCandidateProof());
            work.setMatchStatus(candidate.getMatchStatus());
            work.setMatchedExperienceId(candidate.getMatchedExperienceId());
            work.setSelectedAction(candidate.getSelectedAction());
            work.setAllowedActions(new ArrayList<>(candidate.getAllowedActions()));
            work.setConflictFields(new ArrayList<>(candidate.getConflictFields()));
            work.setProjectName(candidate.getProjectName());
            work.setRoleName(candidate.getRoleName());
            work.setPublishStatus(candidate.getPublishStatus());
            work.setWorkTypeCode(candidate.getWorkTypeCode());
            work.setRoleLevelCode(candidate.getRoleLevelCode());
            work.setShootYear(candidate.getShootYear());
            work.setShootMonth(candidate.getShootMonth());
            work.setPlatform(candidate.getPlatform());
            work.setSyncSoundStatus(candidate.getSyncSoundStatus());
            work.setCollaborators(new ArrayList<>(candidate.getCollaborators()));
            work.setAchievementText(candidate.getAchievementText());
            work.setDescription(candidate.getDescription());
            request.getWorks().add(work);
        }
        return request;
    }

    private ProfileImportApplyReqDTO.WorkFields finalFields(
            ProfileImportExtractionRespDTO.WorkCandidate candidate) {
        ProfileImportApplyReqDTO.WorkFields fields = new ProfileImportApplyReqDTO.WorkFields();
        fields.setProjectName(candidate.getProjectName());
        fields.setRoleName(candidate.getRoleName());
        fields.setPublishStatus(candidate.getPublishStatus());
        fields.setWorkTypeCode(candidate.getWorkTypeCode());
        fields.setRoleLevelCode(candidate.getRoleLevelCode());
        fields.setShootYear(candidate.getShootYear());
        fields.setShootMonth(candidate.getShootMonth());
        fields.setPlatform(candidate.getPlatform());
        fields.setSyncSoundStatus(candidate.getSyncSoundStatus());
        fields.setCollaborators(new ArrayList<>(candidate.getCollaborators()));
        fields.setAchievementText(candidate.getAchievementText());
        fields.setDescription(candidate.getDescription());
        return fields;
    }

    private void assertDatabaseContainsEveryGoldenWork(long userId) {
        assertEquals(29L, countActiveWorks(userId));
        assertEquals(29L, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT experience_id) FROM actor_experience "
                        + "WHERE user_id = ? AND deleted = 0",
                Long.class,
                userId));
        assertEquals(29L, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT dedupe_key) FROM actor_experience "
                        + "WHERE user_id = ? AND deleted = 0 AND dedupe_key IS NOT NULL "
                        + "AND TRIM(dedupe_key) <> ''",
                Long.class,
                userId));
        assertEquals(29L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_experience "
                        + "WHERE user_id = ? AND deleted = 0 AND source_type = 'import'",
                Long.class,
                userId));
        assertEquals(REQUIRED_CATEGORY_COUNTS, categoryCounts(userId));

        Map<String, ActualWork> actualByProject = jdbc.query(
                        """
                        SELECT experience_id, drama_name, role_name, publish_status, work_type_code,
                               role_level_code, shoot_year, shoot_month, platform, sync_sound_status,
                               collaborators_json, achievement_text, role_desc, dedupe_key, source_type
                        FROM actor_experience
                        WHERE user_id = ? AND deleted = 0
                        """,
                        (resultSet, rowNum) -> new ActualWork(
                                resultSet.getLong("experience_id"),
                                resultSet.getString("drama_name"),
                                resultSet.getString("role_name"),
                                resultSet.getString("publish_status"),
                                resultSet.getString("work_type_code"),
                                resultSet.getString("role_level_code"),
                                (Integer) resultSet.getObject("shoot_year"),
                                (Integer) resultSet.getObject("shoot_month"),
                                resultSet.getString("platform"),
                                resultSet.getString("sync_sound_status"),
                                readCollaborators(resultSet.getString("collaborators_json")),
                                resultSet.getString("achievement_text"),
                                resultSet.getString("role_desc"),
                                resultSet.getString("dedupe_key"),
                                resultSet.getString("source_type")),
                        userId)
                .stream()
                .collect(Collectors.toMap(ActualWork::projectName, Function.identity()));
        assertEquals(29, actualByProject.size());

        for (GoldenWork expected : GOLDEN.works()) {
            ActualWork actual = actualByProject.get(expected.projectName());
            assertNotNull(actual, expected.fixtureId() + " must exist as a real active row");
            assertTrue(actual.experienceId() > 0L);
            assertTrue(actual.dedupeKey() != null && !actual.dedupeKey().isBlank());
            assertEquals("import", actual.sourceType());
            assertEquals(expected.roleName(), actual.roleName());
            assertEquals(expected.publishStatus(), actual.publishStatus());
            assertEquals(expected.workTypeCode(), actual.workTypeCode());
            assertEquals(expected.roleLevelCode(), actual.roleLevelCode());
            assertNull(actual.shootYear());
            assertNull(actual.shootMonth());
            assertEquals(expected.platform(), actual.platform());
            assertEquals(expected.syncSoundStatus(), actual.syncSoundStatus());
            assertEquals(expected.collaborators(), actual.collaborators());
            assertEquals(expected.achievementText(), actual.achievementText());
            assertNull(actual.description());
        }
    }

    private void assertRealActorWorkServicePagination(long userId) {
        PageResult<ActorWorkRespDTO> first = page(userId, 1);
        PageResult<ActorWorkRespDTO> second = page(userId, 2);
        PageResult<ActorWorkRespDTO> third = page(userId, 3);
        assertEquals(29L, first.getTotal());
        assertEquals(29L, second.getTotal());
        assertEquals(29L, third.getTotal());
        assertEquals(10, first.getList().size());
        assertEquals(10, second.getList().size());
        assertEquals(9, third.getList().size());
        Set<Long> ids = new LinkedHashSet<>();
        first.getList().forEach(work -> {
            assertEquals("import", work.getSourceType());
            ids.add(work.getExperienceId());
        });
        second.getList().forEach(work -> {
            assertEquals("import", work.getSourceType());
            ids.add(work.getExperienceId());
        });
        third.getList().forEach(work -> {
            assertEquals("import", work.getSourceType());
            ids.add(work.getExperienceId());
        });
        assertEquals(29, ids.size());
    }

    private PageResult<ActorWorkRespDTO> page(long userId, int pageNumber) {
        ActorWorkQueryDTO query = new ActorWorkQueryDTO();
        query.setPage(pageNumber);
        query.setSize(10);
        return actorWorkService.listWorks(userId, query);
    }

    private void assertFreshProofs(
            ProfileImportExtractionRespDTO first,
            ProfileImportExtractionRespDTO second) {
        Map<String, String> firstProofs = first.getWorkCandidates().stream().collect(Collectors.toMap(
                ProfileImportExtractionRespDTO.WorkCandidate::getCandidateId,
                ProfileImportExtractionRespDTO.WorkCandidate::getCandidateProof));
        Map<String, String> secondProofs = second.getWorkCandidates().stream().collect(Collectors.toMap(
                ProfileImportExtractionRespDTO.WorkCandidate::getCandidateId,
                ProfileImportExtractionRespDTO.WorkCandidate::getCandidateProof));
        assertEquals(firstProofs.keySet(), secondProofs.keySet());
        assertTrue(firstProofs.keySet().stream()
                .allMatch(candidateId -> !firstProofs.get(candidateId).equals(secondProofs.get(candidateId))));
        assertTrue(new HashSet<>(firstProofs.values()).stream()
                .noneMatch(new HashSet<>(secondProofs.values())::contains));
    }

    private void assertAuditContext(ExtractionCycle extraction) {
        assertEquals(extraction.context().profileVersion(),
                auditValue(extraction.auditId(), "profile_version", Long.class));
        assertEquals(extraction.context().workLibraryVersion(),
                auditValue(extraction.auditId(), "work_library_version", Long.class));
        assertEquals("success", auditValue(extraction.auditId(), "status", String.class));
        assertNull(auditValue(extraction.auditId(), "apply_status", String.class));
    }

    private void assertApplySummary(
            ProfileImportApplyRespDTO response,
            int worksCreated,
            int worksSkipped,
            int worksMerged) {
        try {
            JsonNode summary = objectMapper.readTree(response.getSummary());
            assertEquals(worksCreated, summary.path("worksCreated").asInt());
            assertEquals(worksSkipped, summary.path("worksSkipped").asInt());
            assertEquals(worksMerged, summary.path("worksMerged").asInt());
        } catch (IOException error) {
            throw new AssertionError("apply summary must be valid JSON", error);
        }
    }

    private ContextVersion currentContext(long userId) {
        List<ContextVersion> rows = jdbc.query(
                "SELECT version, work_library_version FROM actor_profile "
                        + "WHERE user_id = ? AND deleted = 0",
                (resultSet, rowNum) -> new ContextVersion(
                        resultSet.getLong("version"),
                        resultSet.getLong("work_library_version")),
                userId);
        return rows.isEmpty() ? new ContextVersion(0L, 0L) : rows.get(0);
    }

    private Map<String, Long> activeIdsByProject(long userId) {
        return jdbc.query(
                        "SELECT experience_id, drama_name FROM actor_experience "
                                + "WHERE user_id = ? AND deleted = 0",
                        (resultSet, rowNum) -> Map.entry(
                                resultSet.getString("drama_name"),
                                resultSet.getLong("experience_id")),
                        userId)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, Long> categoryCounts(long userId) {
        Map<String, Long> counts = new HashMap<>();
        jdbc.query(
                "SELECT publish_status, COUNT(*) AS total FROM actor_experience "
                        + "WHERE user_id = ? AND deleted = 0 GROUP BY publish_status",
                resultSet -> {
                    counts.put(resultSet.getString("publish_status"), resultSet.getLong("total"));
                },
                userId);
        return counts;
    }

    private long countActiveWorks(long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_experience WHERE user_id = ? AND deleted = 0",
                Long.class,
                userId);
    }

    private long countActiveMediaAssets(long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM actor_media_asset WHERE user_id = ? AND deleted = 0",
                Long.class,
                userId);
    }

    private void assertGoldenProfileCandidates(ProfileImportExtractionRespDTO response) {
        assertEquals("王火火", profileCandidate(response, "public_name").getCandidateValue());
        assertEquals("170", profileCandidate(response, "height").getCandidateValue());
        assertEquals("45", profileCandidate(response, "weight").getCandidateValue());
        assertEquals("2004", profileCandidate(response, "birth_year").getCandidateValue());
        assertEquals("9", profileCandidate(response, "birth_month").getCandidateValue());
        assertEquals("month", profileCandidate(response, "birth_precision").getCandidateValue());
        ProfileImportExtractionRespDTO.ProfileCandidate age = profileCandidate(response, "age");
        assertEquals(Integer.toString(expectedGoldenAge()), age.getCandidateValue());
        assertEquals("derived_from_birth", age.getSourceType());
        assertEquals("根据部分生日动态推算", age.getWarning());
        assertFalse(age.isSelected());
        assertEquals("中国香港", profileCandidate(response, "origin_place").getCandidateValue());
        assertEquals("浙江传媒学院", profileCandidate(response, "school_name").getCandidateValue());
        assertEquals("表演", profileCandidate(response, "major_name").getCandidateValue());
        assertEquals("[\"粤语\",\"英语\",\"东北话\"]",
                profileCandidate(response, "language_tags").getCandidateValue());
        assertEquals("[\"普通话标准\",\"台词功底扎实\",\"同期声\",\"眼神戏好\",\"情感戏强\","
                        + "\"爆发力强\",\"打戏\",\"威亚\",\"配合度高\"]",
                profileCandidate(response, "professional_ability_tags").getCandidateValue());
        assertNull(response.getProfileCandidates().stream()
                .filter(candidate -> "birth_day".equals(candidate.getFieldKey()))
                .findFirst()
                .orElse(null));
        assertNull(response.getProfileCandidates().stream()
                .filter(candidate -> "current_city".equals(candidate.getFieldKey()))
                .findFirst()
                .orElse(null));
    }

    private int expectedGoldenAge() {
        LocalDate today = LocalDate.now();
        return today.getYear() - 2004 - (today.getMonthValue() < 9 ? 1 : 0);
    }

    private ProfileImportExtractionRespDTO.ProfileCandidate profileCandidate(
            ProfileImportExtractionRespDTO response, String fieldKey) {
        return response.getProfileCandidates().stream()
                .filter(candidate -> fieldKey.equals(candidate.getFieldKey()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(
                    json == null ? "[]" : json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (IOException error) {
            throw new AssertionError("profile tags must be valid JSON", error);
        }
    }

    private <T> T auditValue(long auditId, String column, Class<T> type) {
        Set<String> allowed = Set.of(
                "status",
                "scene",
                "candidate_count",
                "work_count",
                "profile_version",
                "work_library_version",
                "apply_status",
                "apply_payload_sha256",
                "apply_result_summary_json",
                "applied_at");
        if (!allowed.contains(column)) {
            throw new IllegalArgumentException("unsupported audit column: " + column);
        }
        return jdbc.queryForObject(
                "SELECT " + column + " FROM ai_profile_import_request_audit WHERE audit_id = ?",
                type,
                auditId);
    }

    private List<String> readCollaborators(String json) {
        try {
            return objectMapper.readValue(
                    json == null ? "[]" : json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (IOException error) {
            throw new IllegalStateException("invalid collaborators_json", error);
        }
    }

    private void installSecondInsertFailureTrigger() {
        jdbc.execute("""
                CREATE TABLE profile_import_insert_probe (
                  probe_id BIGINT NOT NULL AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  project_name VARCHAR(255) NOT NULL,
                  connection_id BIGINT NOT NULL,
                  PRIMARY KEY (probe_id)
                ) ENGINE=MyISAM
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_second_profile_import_work
                AFTER INSERT ON actor_experience FOR EACH ROW
                BEGIN
                  IF NEW.user_id = 89102 THEN
                    INSERT INTO profile_import_insert_probe (user_id, project_name, connection_id)
                    VALUES (NEW.user_id, NEW.drama_name, CONNECTION_ID());
                    IF (SELECT COUNT(*) FROM profile_import_insert_probe
                        WHERE user_id = NEW.user_id) = 2 THEN
                      SIGNAL SQLSTATE '45000'
                        SET MESSAGE_TEXT = 'forced second profile import work insert failure';
                    END IF;
                  END IF;
                END
                """);
    }

    private static void lockUser(Connection connection, long userId) throws SQLException {
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id FROM `user` WHERE user_id = ? FOR UPDATE")) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
            }
        }
    }

    private void awaitAuditRowLock(long auditId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            try (Connection observer = dataSource.getConnection()) {
                observer.setAutoCommit(false);
                try (Statement timeout = observer.createStatement()) {
                    timeout.execute("SET SESSION innodb_lock_wait_timeout = 1");
                }
                try (PreparedStatement statement = observer.prepareStatement(
                        "SELECT audit_id FROM ai_profile_import_request_audit "
                                + "WHERE audit_id = ? FOR UPDATE")) {
                    statement.setLong(1, auditId);
                    statement.executeQuery().close();
                    observer.rollback();
                } catch (SQLException error) {
                    observer.rollback();
                    if (error.getErrorCode() == 1205) return;
                    throw error;
                }
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("timed out waiting for the first apply to lock its audit row");
    }

    private static void assertGoldenFixtureIsExplicitAndStable() {
        assertEquals(1, GOLDEN.schemaVersion());
        assertEquals(29, GOLDEN.works().size());
        assertEquals(
                Map.of("aired", 14, "upcoming", 6, "stage", 3, "horizontal", 6),
                GOLDEN.categoryCounts());
        assertEquals(29, GOLDEN.works().stream().map(GoldenWork::fixtureId).distinct().count());
        assertEquals(29, GOLDEN.works().stream().map(GoldenWork::projectName).distinct().count());
        assertFalse(GOLDEN_EXTRACTION_JSON.contains("rawText"));
        assertFalse(GOLDEN_EXTRACTION_JSON.contains("clipboard"));
    }

    private static GoldenFixture readGoldenFixture() {
        try (InputStream input =
                ProfileImportApplyMySqlIntegrationTest.class.getResourceAsStream(GOLDEN_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing explicit golden fixture: " + GOLDEN_RESOURCE);
            }
            return FIXTURE_MAPPER.readValue(input, GoldenFixture.class);
        } catch (IOException error) {
            throw new IllegalStateException("cannot read explicit golden fixture", error);
        }
    }

    private static String buildGoldenExtractionJson() {
        ObjectNode root = FIXTURE_MAPPER.createObjectNode();
        ArrayNode profileCandidates = root.putArray("profileCandidates");
        addProfileCandidate(
                profileCandidates, "profile-public-name", "public_name", "王火火",
                "演员王火火", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-gender", "gender", "female",
                "女主 / 女二 / 女反一", "inferred_from_roles", "根据多条作品角色推断，请确认");
        addProfileCandidate(
                profileCandidates, "profile-height", "height", "170",
                "170/45kg", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-weight", "weight", "45",
                "170/45kg", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-birth-year", "birth_year", "2004",
                "生日：2004.9", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-birth-month", "birth_month", "9",
                "生日：2004.9", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-birth-precision", "birth_precision", "month",
                "生日：2004.9", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-origin", "origin_place", "中国香港",
                "籍贯：中国香港", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-school", "school_name", "浙江传媒学院",
                "院校：浙江传媒学院 表演专业", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-major", "major_name", "表演",
                "院校：浙江传媒学院 表演专业", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-languages", "language_tags",
                List.of("粤语", "英语", "东北话"),
                "语言：粤语 英语 东北话", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-specialties", "specialty_tags",
                List.of("表演", "主持", "唱歌", "跳舞", "架子鼓", "羽毛球", "排球", "跑步", "游泳"),
                "特长：表演 主持 唱歌 跳舞 架子鼓 羽毛球 排球 跑步 游泳", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-role-types", "role_type_tags",
                List.of("悲情女主", "复仇大女主", "小白花", "绿茶"),
                "人物形象：悲情女主/复仇大女主/小白花/绿茶", "explicit", null);
        addProfileCandidate(
                profileCandidates, "profile-abilities", "professional_ability_tags",
                List.of(
                        "普通话标准", "台词功底扎实", "同期声", "眼神戏好", "情感戏强",
                        "爆发力强", "打戏", "威亚", "配合度高"),
                "普通话标准，台词功底扎实，可同期声；眼神戏好；情感戏强，爆发力强，可打戏可威亚，配合度高",
                "explicit", null);
        ArrayNode workCandidates = root.putArray("workCandidates");
        for (GoldenWork work : GOLDEN.works()) {
            ObjectNode candidate = workCandidates.addObject();
            candidate.put("candidateId", work.fixtureId());
            candidate.put("projectName", work.projectName());
            putNullable(candidate, "roleName", work.roleName());
            candidate.put("publishStatus", work.publishStatus());
            candidate.put("workTypeCode", work.workTypeCode());
            putNullable(candidate, "roleLevelCode", work.roleLevelCode());
            putNullable(candidate, "platform", work.platform());
            putNullable(candidate, "syncSoundStatus", work.syncSoundStatus());
            candidate.set("collaborators", FIXTURE_MAPPER.valueToTree(work.collaborators()));
            putNullable(candidate, "achievementText", work.achievementText());
            candidate.put("sourceType", "explicit");
            ObjectNode fields = candidate.putObject("fields");
            addEvidence(fields, "projectName", work.projectName(), work.projectName());
            addEvidence(fields, "roleName", work.roleName(), work.roleName());
            addEvidence(fields, "publishStatus", work.publishStatus(),
                    evidenceText("publishStatus", work.publishStatus()));
            addEvidence(fields, "workTypeCode", work.workTypeCode(),
                    evidenceText("workTypeCode", work.workTypeCode()));
            addEvidence(fields, "roleLevelCode", work.roleLevelCode(),
                    evidenceText("roleLevelCode", work.roleLevelCode()));
            addEvidence(fields, "platform", work.platform(), work.platform());
            addEvidence(fields, "syncSoundStatus", work.syncSoundStatus(),
                    evidenceText("syncSoundStatus", work.syncSoundStatus()));
            if (work.collaborators() != null && !work.collaborators().isEmpty()) {
                addEvidence(fields, "collaborators", work.collaborators(),
                        String.join(" " , work.collaborators()));
            }
            addEvidence(fields, "achievementText", work.achievementText(), work.achievementText());
        }
        root.put("ignoredMediaPlaceholderCount", 0);
        try {
            return FIXTURE_MAPPER.writeValueAsString(root);
        } catch (IOException error) {
            throw new IllegalStateException("cannot build extraction response from golden fixture", error);
        }
    }

    private static String buildGoldenSanitizedEvidence() {
        List<String> fragments = new ArrayList<>(List.of(
                "演员王火火",
                "170/45kg",
                "生日：2004.9",
                "籍贯：中国香港",
                "院校：浙江传媒学院 表演专业",
                "语言：粤语 英语 东北话",
                "特长：表演 主持 唱歌 跳舞 架子鼓 羽毛球 排球 跑步 游泳",
                "人物形象：悲情女主/复仇大女主/小白花/绿茶",
                "普通话标准，台词功底扎实，可同期声；眼神戏好；情感戏强，爆发力强，可打戏可威亚，配合度高"));
        for (GoldenWork work : GOLDEN.works()) {
            fragments.add(work.projectName());
            if (work.roleName() != null) fragments.add(work.roleName());
            fragments.add(evidenceText("publishStatus", work.publishStatus()));
            fragments.add(evidenceText("workTypeCode", work.workTypeCode()));
            if (work.roleLevelCode() != null) {
                fragments.add(evidenceText("roleLevelCode", work.roleLevelCode()));
            }
            if (work.platform() != null) fragments.add(work.platform());
            if (work.syncSoundStatus() != null) {
                fragments.add(evidenceText("syncSoundStatus", work.syncSoundStatus()));
            }
            fragments.addAll(work.collaborators());
            if (work.achievementText() != null) fragments.add(work.achievementText());
        }
        fragments.add("[图片]".repeat(18) + "[视频]".repeat(12));
        return String.join(" ", fragments);
    }

    private static void addProfileCandidate(
            ArrayNode candidates,
            String candidateId,
            String fieldKey,
            Object value,
            String sourceText,
            String sourceType,
            String warning) {
        ObjectNode candidate = candidates.addObject();
        candidate.put("candidateId", candidateId);
        candidate.put("fieldKey", fieldKey);
        candidate.set("candidateValue", FIXTURE_MAPPER.valueToTree(value));
        candidate.put("confidence", 0.99d);
        candidate.put("sourceText", sourceText);
        candidate.put("sourceType", sourceType);
        if (warning == null) {
            candidate.putNull("warning");
        } else {
            candidate.put("warning", warning);
        }
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static String evidenceText(String field, String value) {
        if (value == null) return null;
        return switch (field + ":" + value) {
            case "publishStatus:aired" -> "已播";
            case "publishStatus:upcoming" -> "待播";
            case "publishStatus:stage" -> "舞台";
            case "publishStatus:horizontal" -> "横屏";
            case "publishStatus:other" -> "其他";
            case "workTypeCode:short_drama" -> "短剧";
            case "workTypeCode:horizontal_short_drama" -> "横屏短剧";
            case "workTypeCode:stage_play" -> "话剧";
            case "workTypeCode:musical" -> "音乐剧";
            case "workTypeCode:tv_column_drama" -> "栏目剧";
            case "workTypeCode:film_tv" -> "影视";
            case "workTypeCode:micro_film" -> "微电影";
            case "workTypeCode:horizontal" -> "横屏";
            case "workTypeCode:stage" -> "舞台";
            case "workTypeCode:other" -> "其他";
            case "roleLevelCode:lead" -> "主演";
            case "roleLevelCode:supporting" -> "配角";
            case "roleLevelCode:antagonist" -> "反派";
            case "roleLevelCode:female_lead" -> "女主";
            case "roleLevelCode:female_supporting_1" -> "女配一";
            case "roleLevelCode:female_supporting_2" -> "女二";
            case "roleLevelCode:female_antagonist_1" -> "女反一";
            case "roleLevelCode:male_lead" -> "男主";
            case "roleLevelCode:male_supporting_1" -> "男配一";
            case "roleLevelCode:male_supporting_2" -> "男二";
            case "roleLevelCode:male_antagonist_1" -> "男反一";
            case "roleLevelCode:other" -> "其他";
            case "syncSoundStatus:sync" -> "同期声";
            case "syncSoundStatus:dubbed" -> "配音";
            case "syncSoundStatus:unknown" -> "未知";
            default -> value;
        };
    }

    private static void addEvidence(ObjectNode fields, String field, Object value, String sourceText) {
        if (value == null) return;
        ObjectNode evidence = fields.putObject(field);
        evidence.set("candidateValue", FIXTURE_MAPPER.valueToTree(value));
        evidence.put("confidence", 0.99d);
        evidence.put("sourceText", sourceText);
        evidence.put("sourceType", "explicit");
    }

    private static CareerProfileMySqlTestSupport startDatabase() {
        CareerProfileMySqlTestSupport database = null;
        try {
            database = CareerProfileMySqlTestSupport.start("profile_import_apply_it");
            try (Connection connection = database.dataSource().getConnection()) {
                executeMigration(connection, "V20260723_004__ai_profile_import_governance.sql");
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            INSERT INTO ai_profile_import_request_audit (
                              request_id, user_id, config_id, model_name, status, input_length,
                              profile_version, work_library_version
                            ) VALUES (
                              'legacy-scene-fixture', 1, 1, 'legacy-model', 'success', 0, 0, 0
                            )
                            """);
                }
                executeMigration(connection, "V20260724_001__ai_profile_import_request_scene.sql");
            }
            return database;
        } catch (Exception error) {
            if (database != null) {
                database.close();
            }
            throw new ExceptionInInitializerError(error);
        }
    }

    private static String readMigratedLegacyScene(CareerProfileMySqlTestSupport database) {
        try (Connection connection = database.dataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT scene FROM ai_profile_import_request_audit WHERE request_id = ?")) {
            statement.setString(1, "legacy-scene-fixture");
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("legacy scene fixture was not migrated");
                }
                return resultSet.getString("scene");
            }
        } catch (SQLException error) {
            throw new IllegalStateException("cannot read migrated legacy scene", error);
        }
    }

    private static void executeMigration(Connection connection, String migrationName)
            throws IOException, SQLException {
        String sql = CareerProfileMySqlTestSupport.readMigrationSql(migrationName);
        try (Statement statement = connection.createStatement()) {
            boolean resultSet = statement.execute(sql);
            while (resultSet || statement.getUpdateCount() != -1) {
                if (resultSet) {
                    try (ResultSet ignored = statement.getResultSet()) {
                        // Drain each result emitted by the repository migration.
                    }
                }
                resultSet = statement.getMoreResults();
            }
        }
    }

    private record ExtractionCycle(
            ContextVersion context,
            ProfileImportExtractionRespDTO response,
            long auditId) {
    }

    private record ContextVersion(long profileVersion, long workLibraryVersion) {
    }

    private record GoldenFixture(
            int schemaVersion,
            Map<String, Integer> categoryCounts,
            List<GoldenWork> works) {
    }

    private record GoldenWork(
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
            String roleName,
            String publishStatus,
            String workTypeCode,
            String roleLevelCode,
            Integer shootYear,
            Integer shootMonth,
            String platform,
            String syncSoundStatus,
            List<String> collaborators,
            String achievementText,
            String description,
            String dedupeKey,
            String sourceType) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan(basePackages = {
        "com.kaipai.mapper.actor",
        "com.kaipai.mapper.card",
        "com.kaipai.mapper.ai",
        "com.kaipai.mapper.user"
    })
    @Import({
        ActorWorkServiceImpl.class,
        ActorProfileImportWriter.class,
        ProfileImportApplyServiceImpl.class,
        ProfileImportServiceImpl.class,
        ProfileImportWorkMatcher.class,
        ProfileImportWorkApplyGuard.class,
        ProfileImportWorkMatchSupport.class
    })
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
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            return interceptor;
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
        ProfileImportCandidateProofService profileImportCandidateProofService() {
            return new ProfileImportCandidateProofService(
                    "profile-import-apply-mysql-integration-proof-secret");
        }

        @Bean
        ProfileImportPayloadHasher profileImportPayloadHasher(ObjectMapper objectMapper) {
            return new ProfileImportPayloadHasher(objectMapper);
        }

        @Bean
        ProfileImportSchemaValidator profileImportSchemaValidator() {
            return new ProfileImportSchemaValidator();
        }

        @Bean
        ProfileImportConfigService profileImportConfigService() {
            ProfileImportConfigService service = mock(ProfileImportConfigService.class);
            when(service.capability()).thenReturn(new ProfileImportCapabilityRespDTO(
                    true, true, "deepseek", "deepseek-fixture", 10000, null));
            when(service.runtimeConfig()).thenReturn(new ProfileImportRuntimeConfig(
                    1L,
                    1,
                    "https://deepseek.invalid/profile-import",
                    "deepseek-fixture",
                    "test-memory-only-key",
                    3000,
                    30000,
                    10000,
                    8000,
                    100));
            return service;
        }

        @Bean
        ProfileImportRateLimiter profileImportRateLimiter() {
            return (userId, dailyLimit) -> true;
        }

        @Bean
        ProfileImportHttpTransport profileImportHttpTransport() {
            return (endpoint, apiKey, body, connectTimeoutMs, readTimeoutMs) -> GOLDEN_EXTRACTION_JSON;
        }

        @Bean
        DeepSeekProfileTextExtractor deepSeekProfileTextExtractor(
                ProfileImportHttpTransport transport) {
            return new DeepSeekProfileTextExtractor(transport);
        }

        @Bean
        ActorMediaAssetOwnershipVerifier actorMediaAssetOwnershipVerifier() {
            return new ActorMediaAssetOwnershipVerifier() {
                @Override
                public void requireOwnedReadyPhoto(Long userId, Long assetId) {
                    throw new UnsupportedOperationException("works_only must not resolve an avatar asset");
                }

                @Override
                public void requireOwnedReadyPdf(Long userId, Long assetId) {
                    throw new UnsupportedOperationException("works_only must not resolve a resume asset");
                }
            };
        }
    }
}
