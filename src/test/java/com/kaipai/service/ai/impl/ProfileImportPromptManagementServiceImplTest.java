package com.kaipai.service.ai.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.ResultCode;
import com.kaipai.mapper.ai.AiProfileImportPromptAuditMapper;
import com.kaipai.mapper.ai.AiProfileImportPromptTemplateMapper;
import com.kaipai.mapper.ai.AiProfileImportPromptVersionMapper;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.ai.dto.ProfileImportPromptAuditRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptCreateDraftReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptRestoreReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptStrictWriteDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptTemplateSummaryRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptTestResultRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptUpdateDraftReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptVersionActionReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptVersionDetailRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptVersionSummaryRespDTO;
import com.kaipai.model.ai.entity.AiProfileImportPromptAudit;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import com.kaipai.service.ai.ProfileImportPromptManagementService;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRenderer;
import com.kaipai.service.ai.profileimport.ProfileImportPromptReasonCode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ProfileImportPromptManagementServiceImplTest {

    private static final String INVALID_MESSAGE =
            "Prompt \u6a21\u677f\u6216\u64cd\u4f5c\u53c2\u6570\u65e0\u6548";

    @Mock
    private AiProfileImportPromptTemplateMapper templateMapper;

    @Mock
    private AiProfileImportPromptVersionMapper versionMapper;

    @Mock
    private AiProfileImportPromptAuditMapper auditMapper;

    @Mock
    private ProfileImportPromptRenderer renderer;

    @Mock
    private AdminAuthContext adminAuthContext;

    private ProfileImportPromptManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProfileImportPromptManagementServiceImpl(
                templateMapper, versionMapper, auditMapper, renderer, adminAuthContext);
    }

    @Test
    void templatesIgnoreDeletedRowsAndComposeOnlySummaryProjection() {
        AiProfileImportPromptTemplate template = template(11L, "full_profile", 91L, 101L, 7, 0);
        AiProfileImportPromptTemplate deleted = template(12L, "deleted", null, null, 1, 1);
        AiProfileImportPromptVersion active = version(91L, 11L, 3, "released", "active-hash", 5);
        active.setVersionLabel("active-v3");
        active.setTestStatus("success");
        AiProfileImportPromptVersion draft = version(101L, 11L, 4, "draft", "draft-hash", 2);
        draft.setVersionLabel("draft-v4");
        draft.setTestStatus("stale");
        when(templateMapper.selectList(any(Wrapper.class))).thenReturn(List.of(template, deleted));
        when(versionMapper.selectSummariesByTemplateId(11L)).thenReturn(List.of(draft, active));

        List<ProfileImportPromptTemplateSummaryRespDTO> result = service.templates();

        assertEquals(1, result.size());
        ProfileImportPromptTemplateSummaryRespDTO summary = result.get(0);
        assertEquals(11L, summary.getTemplateId());
        assertEquals(91L, summary.getActiveVersionId());
        assertEquals(3, summary.getActiveVersionNo());
        assertEquals("active-v3", summary.getActiveVersionLabel());
        assertEquals("active-hash", summary.getActiveContentSha256());
        assertEquals("success", summary.getActiveTestStatus());
        assertEquals(101L, summary.getDraftVersionId());
        assertEquals(4, summary.getDraftVersionNo());
        assertEquals("draft-v4", summary.getDraftVersionLabel());
        assertEquals("draft-hash", summary.getDraftContentSha256());
        assertEquals("stale", summary.getDraftTestStatus());
        assertEquals(7, summary.getVersion());
        verify(versionMapper, never()).selectSummariesByTemplateId(12L);
        verify(versionMapper, never()).selectOwnedDetail(any(), any());
        verifyNoInteractions(auditMapper, renderer, adminAuthContext);
    }

    @Test
    void templatesFailClosedWhenActivePointerHasNoOwnedSummary() {
        AiProfileImportPromptTemplate template =
                template(11L, "full_profile", 91L, null, 7, 0);
        when(templateMapper.selectList(any(Wrapper.class))).thenReturn(List.of(template));
        when(versionMapper.selectSummariesByTemplateId(11L)).thenReturn(List.of());

        assertStateConflict(assertThrows(BizException.class, service::templates));

        verifyNoInteractions(auditMapper, renderer, adminAuthContext);
    }

    @Test
    void templatesFailClosedWhenDraftPointerHasNoOwnedSummary() {
        AiProfileImportPromptTemplate template =
                template(11L, "full_profile", null, 101L, 7, 0);
        when(templateMapper.selectList(any(Wrapper.class))).thenReturn(List.of(template));
        when(versionMapper.selectSummariesByTemplateId(11L)).thenReturn(List.of());

        assertStateConflict(assertThrows(BizException.class, service::templates));

        verifyNoInteractions(auditMapper, renderer, adminAuthContext);
    }

    @Test
    void versionsReadOnlyUndeletedTemplateAndReturnSummaryDtos() {
        AiProfileImportPromptTemplate template = template(11L, "full_profile", 91L, 101L, 7, 0);
        AiProfileImportPromptVersion row = version(101L, 11L, 4, "draft", "draft-hash", 2);
        row.setSystemPromptBody("PRIVATE_SYSTEM_BODY");
        row.setRepairPromptBody("PRIVATE_REPAIR_BODY");
        when(templateMapper.selectOne(any(Wrapper.class))).thenReturn(template);
        when(versionMapper.selectSummariesByTemplateId(11L)).thenReturn(List.of(row));

        List<ProfileImportPromptVersionSummaryRespDTO> result = service.versions("full_profile");

        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).getPromptVersionId());
        assertEquals("draft-hash", result.get(0).getContentSha256());
        assertEquals(ProfileImportPromptVersionSummaryRespDTO.class, result.get(0).getClass());
        verify(versionMapper, never()).selectOwnedDetail(any(), any());
        verifyNoInteractions(auditMapper, renderer, adminAuthContext);
    }

    @Test
    void versionUsesLocatorOnlyForTemplateIdThenLoadsOwnedDetail() {
        AiProfileImportPromptVersion locator = new AiProfileImportPromptVersion();
        locator.setPromptVersionId(101L);
        locator.setTemplateId(11L);
        locator.setSystemPromptBody("LOCATOR_MUST_NOT_ESCAPE");
        AiProfileImportPromptVersion detail = version(101L, 11L, 4, "draft", "draft-hash", 2);
        detail.setSystemPromptBody("system-detail");
        detail.setRepairPromptBody("repair-detail");
        detail.setSchemaVersion("profile-import-v1");
        detail.setContractVersion("profile-import-contract-v1");
        detail.setChangeSummary("safe editor summary");
        when(versionMapper.selectById(101L)).thenReturn(locator);
        when(versionMapper.selectOwnedDetail(11L, 101L)).thenReturn(detail);

        ProfileImportPromptVersionDetailRespDTO result = service.version(101L);

        assertEquals("system-detail", result.getSystemPromptBody());
        assertEquals("repair-detail", result.getRepairPromptBody());
        assertFalse(result.getSystemPromptBody().contains("LOCATOR_MUST_NOT_ESCAPE"));
        verify(versionMapper).selectById(101L);
        verify(versionMapper).selectOwnedDetail(11L, 101L);
        verifyNoInteractions(templateMapper, auditMapper, renderer, adminAuthContext);
    }

    @Test
    void auditsReadAtMostFiftyAndMapOnlySanitizedResponseFields() {
        AiProfileImportPromptAudit row = new AiProfileImportPromptAudit();
        row.setPromptAuditId(501L);
        row.setTemplateId(11L);
        row.setPromptVersionId(101L);
        row.setActionCode("draft_update");
        row.setReasonCode("DRAFT_UPDATED");
        row.setOperatorId(73L);
        row.setOperatorName("Prompt Admin");
        row.setMessage(null);
        when(auditMapper.selectRecent(50)).thenReturn(List.of(row));

        List<ProfileImportPromptAuditRespDTO> result = service.audits();

        assertEquals(1, result.size());
        assertEquals(501L, result.get(0).getPromptAuditId());
        assertEquals("draft_update", result.get(0).getActionCode());
        assertEquals("DRAFT_UPDATED", result.get(0).getReasonCode());
        assertEquals("Prompt Admin", result.get(0).getOperatorName());
        assertEquals(null, result.get(0).getMessage());
        verify(auditMapper).selectRecent(50);
        verifyNoInteractions(templateMapper, versionMapper, renderer, adminAuthContext);
    }

    @Test
    void implementationAndDraftWritesDeclareRequiredSpringTransactions() throws Exception {
        assertNotNull(ProfileImportPromptManagementServiceImpl.class.getAnnotation(Service.class));
        assertArrayEquals(
                new Class<?>[] {
                    AiProfileImportPromptTemplateMapper.class,
                    AiProfileImportPromptVersionMapper.class,
                    AiProfileImportPromptAuditMapper.class,
                    ProfileImportPromptRenderer.class,
                    AdminAuthContext.class
                },
                ProfileImportPromptManagementServiceImpl.class
                        .getDeclaredConstructors()[0]
                        .getParameterTypes());
        for (Method method : List.of(
                ProfileImportPromptManagementServiceImpl.class.getDeclaredMethod(
                        "createDraft",
                        Long.class,
                        String.class,
                        ProfileImportPromptCreateDraftReqDTO.class),
                ProfileImportPromptManagementServiceImpl.class.getDeclaredMethod(
                        "updateDraft",
                        Long.class,
                        Long.class,
                        ProfileImportPromptUpdateDraftReqDTO.class),
                ProfileImportPromptManagementServiceImpl.class.getDeclaredMethod(
                        "abandonDraft",
                        Long.class,
                        Long.class,
                        ProfileImportPromptVersionActionReqDTO.class))) {
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertNotNull(transactional, method.getName());
            assertArrayEquals(new Class<?>[] {Exception.class}, transactional.rollbackFor());
        }
    }

    @Test
    void nextVersionNumberIncludesDeletedHistoryInItsExactSql() throws Exception {
        Method method = AiProfileImportPromptVersionMapper.class.getDeclaredMethod(
                "selectNextVersionNo", Long.class);
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select);
        assertArrayEquals(
                new String[] {
                    "SELECT COALESCE(MAX(version_no),0)+1 "
                            + "FROM ai_profile_import_prompt_version WHERE template_id=#{templateId}"
                },
                select.value());
        assertFalse(String.join(" ", select.value()).toLowerCase().contains("deleted"));
    }

    @Test
    void draftMutationMapperSqlAndAbandonSignaturePersistAuthenticatedOperatorMetadata()
            throws Exception {
        Method updateDraft = AiProfileImportPromptVersionMapper.class.getDeclaredMethod(
                "updateDraftIfExpected", AiProfileImportPromptVersion.class, Integer.class);
        String updateSql = String.join(" ", updateDraft.getAnnotation(Update.class).value());
        assertTrue(updateSql.contains("update_user_id=#{draft.updateUserId}"));
        assertTrue(updateSql.contains("update_user_name=#{draft.updateUserName}"));

        Method abandonDraft = AiProfileImportPromptVersionMapper.class.getDeclaredMethod(
                "abandonDraftIfExpected",
                Long.class,
                Long.class,
                Integer.class,
                Long.class,
                String.class);
        assertArrayEquals(
                new String[] {
                    "templateId", "promptVersionId", "expectedVersion", "operatorId", "operatorName"
                },
                Arrays.stream(abandonDraft.getParameters())
                        .map(parameter -> parameter.getAnnotation(Param.class).value())
                        .toArray(String[]::new));
        String abandonSql = String.join(" ", abandonDraft.getAnnotation(Update.class).value());
        assertTrue(abandonSql.contains("update_user_id=#{operatorId}"));
        assertTrue(abandonSql.contains("update_user_name=#{operatorName}"));
    }

    @Test
    void createDraftFromCurrentReleasedVersionCopiesOnlyGovernedFieldsAndReturnsFreshSummary()
            throws Exception {
        authenticate(73L, "Prompt Admin");
        AiProfileImportPromptTemplate locked =
                template(11L, "full_profile", 91L, null, 4, 0);
        AiProfileImportPromptVersion source = governedVersion(91L, 11L, 3, "released", 9);
        source.setVersionLabel("Current release");
        source.setContentSha256("old-content-hash");
        source.setChangeSummary("must not copy");
        source.setTestStatus("success");
        source.setTestedContentSha256("tested-content");
        source.setTestedRuntimeSha256("tested-runtime");
        source.setTestFixtureCode("private-fixture");
        source.setTestedModelName("private-model");
        source.setReleasedBy(72L);
        source.setReleasedAt(LocalDateTime.of(2026, 7, 25, 10, 30));
        source.setCreateUserId(72L);
        source.setUpdateUserName("Previous Admin");
        AiProfileImportPromptTemplate fresh =
                template(11L, "full_profile", 91L, 101L, 5, 0);
        AiProfileImportPromptVersion freshDraft = governedVersion(101L, 11L, 4, "draft", 0);
        freshDraft.setContentSha256("new-content-hash");
        freshDraft.setTestStatus("untested");

        when(templateMapper.selectByCodeForUpdate("full_profile")).thenReturn(locked);
        when(versionMapper.selectOwnedForUpdate(11L, 91L)).thenReturn(source);
        when(versionMapper.selectNextVersionNo(11L)).thenReturn(4);
        when(renderer.contentSha256(eq(locked), any(AiProfileImportPromptVersion.class)))
                .thenReturn("new-content-hash");
        doAnswer(invocation -> {
                    AiProfileImportPromptVersion inserted = invocation.getArgument(0);
                    inserted.setPromptVersionId(101L);
                    return 1;
                })
                .when(versionMapper)
                .insert(any(AiProfileImportPromptVersion.class));
        when(templateMapper.attachDraftIfExpected(11L, 101L, 4)).thenReturn(1);
        when(auditMapper.insertAudit(any(AiProfileImportPromptAudit.class))).thenReturn(1);
        when(templateMapper.selectById(11L)).thenReturn(fresh);
        when(versionMapper.selectSummariesByTemplateId(11L)).thenReturn(List.of(freshDraft, source));

        ProfileImportPromptTemplateSummaryRespDTO result =
                service.createDraft(73L, "full_profile", createReq(null, 4));

        assertEquals(101L, result.getDraftVersionId());
        assertEquals(4, result.getDraftVersionNo());
        assertEquals("new-content-hash", result.getDraftContentSha256());
        assertEquals("untested", result.getDraftTestStatus());
        assertEquals(5, result.getVersion());

        ArgumentCaptor<AiProfileImportPromptVersion> draftCaptor =
                ArgumentCaptor.forClass(AiProfileImportPromptVersion.class);
        ArgumentCaptor<AiProfileImportPromptAudit> auditCaptor =
                ArgumentCaptor.forClass(AiProfileImportPromptAudit.class);
        InOrder order = Mockito.inOrder(
                adminAuthContext,
                templateMapper,
                versionMapper,
                renderer,
                auditMapper);
        order.verify(adminAuthContext).requireCurrentAdmin();
        order.verify(templateMapper).selectByCodeForUpdate("full_profile");
        order.verify(versionMapper).selectOwnedForUpdate(11L, 91L);
        order.verify(versionMapper).selectNextVersionNo(11L);
        order.verify(renderer).contentSha256(eq(locked), any(AiProfileImportPromptVersion.class));
        order.verify(versionMapper).insert(draftCaptor.capture());
        order.verify(templateMapper).attachDraftIfExpected(11L, 101L, 4);
        order.verify(auditMapper).insertAudit(auditCaptor.capture());
        order.verify(templateMapper).selectById(11L);
        order.verify(versionMapper).selectSummariesByTemplateId(11L);

        AiProfileImportPromptVersion inserted = draftCaptor.getValue();
        assertEquals(11L, inserted.getTemplateId());
        assertEquals(4, inserted.getVersionNo());
        assertEquals("Current release", inserted.getVersionLabel());
        assertEquals(source.getSystemPromptBody(), inserted.getSystemPromptBody());
        assertEquals(source.getRepairPromptBody(), inserted.getRepairPromptBody());
        assertEquals(source.getSchemaVersion(), inserted.getSchemaVersion());
        assertEquals(source.getContractVersion(), inserted.getContractVersion());
        assertEquals("draft", inserted.getLifecycleStatus());
        assertEquals("untested", inserted.getTestStatus());
        assertEquals(0, inserted.getDeleted());
        assertEquals("new-content-hash", inserted.getContentSha256());
        assertNull(inserted.getChangeSummary());
        assertNull(inserted.getTestedContentSha256());
        assertNull(inserted.getTestedRuntimeSha256());
        assertNull(inserted.getTestFixtureCode());
        assertNull(inserted.getTestedModelName());
        assertNull(inserted.getTestedBy());
        assertNull(inserted.getTestedAt());
        assertNull(inserted.getReleasedBy());
        assertNull(inserted.getReleasedAt());
        assertEquals(73L, inserted.getCreateUserId());
        assertEquals("Prompt Admin", inserted.getCreateUserName());
        assertEquals(73L, inserted.getUpdateUserId());
        assertEquals("Prompt Admin", inserted.getUpdateUserName());
        assertNull(inserted.getVersion());

        AiProfileImportPromptAudit audit = auditCaptor.getValue();
        assertDraftAudit(
                audit,
                "draft_create",
                "DRAFT_CREATED_CURRENT",
                11L,
                101L,
                91L,
                101L,
                "new-content-hash",
                73L,
                "Prompt Admin");
        assertEquals(source.getSchemaVersion(), audit.getSchemaVersion());
        assertEquals(source.getContractVersion(), audit.getContractVersion());
        assertFalse(allInstanceFieldValues(audit).contains(source.getSystemPromptBody()));
        assertFalse(allInstanceFieldValues(audit).contains(source.getRepairPromptBody()));
        assertFalse(allInstanceFieldValues(audit).contains(source.getChangeSummary()));
    }

    @Test
    void createDraftFromExplicitReleasedHistoryUsesHistoryReason() {
        authenticate(73L, "History Admin");
        AiProfileImportPromptTemplate locked =
                template(11L, "full_profile", 91L, null, 4, 0);
        AiProfileImportPromptVersion history = governedVersion(88L, 11L, 2, "released", 6);
        AiProfileImportPromptVersion active = governedVersion(91L, 11L, 3, "released", 9);
        AiProfileImportPromptTemplate fresh =
                template(11L, "full_profile", 91L, 102L, 5, 0);
        AiProfileImportPromptVersion freshDraft = governedVersion(102L, 11L, 4, "draft", 0);
        freshDraft.setContentSha256("history-copy-hash");
        when(templateMapper.selectByCodeForUpdate("full_profile")).thenReturn(locked);
        when(versionMapper.selectOwnedForUpdate(11L, 88L)).thenReturn(history);
        when(versionMapper.selectNextVersionNo(11L)).thenReturn(4);
        when(renderer.contentSha256(eq(locked), any(AiProfileImportPromptVersion.class)))
                .thenReturn("history-copy-hash");
        doAnswer(invocation -> {
                    invocation.<AiProfileImportPromptVersion>getArgument(0)
                            .setPromptVersionId(102L);
                    return 1;
                })
                .when(versionMapper)
                .insert(any(AiProfileImportPromptVersion.class));
        when(templateMapper.attachDraftIfExpected(11L, 102L, 4)).thenReturn(1);
        when(auditMapper.insertAudit(any())).thenReturn(1);
        when(templateMapper.selectById(11L)).thenReturn(fresh);
        when(versionMapper.selectSummariesByTemplateId(11L))
                .thenReturn(List.of(freshDraft, active, history));

        ProfileImportPromptTemplateSummaryRespDTO result =
                service.createDraft(73L, "full_profile", createReq(88L, 4));

        assertEquals(102L, result.getDraftVersionId());
        verify(versionMapper).selectOwnedForUpdate(11L, 88L);
        verify(auditMapper).insertAudit(argThat(audit ->
                "DRAFT_CREATED_HISTORY".equals(audit.getReasonCode())
                        && Long.valueOf(88L).equals(audit.getFromVersionId())
                        && Long.valueOf(102L).equals(audit.getToVersionId())));
    }

    @Test
    void writeDtoGuardRunsBeforeAuthenticationAndPersistence() {
        ProfileImportPromptCreateDraftReqDTO request = createReq(null, 4);
        request.captureUnexpectedField("operatorId", null);

        BizException error = assertThrows(
                BizException.class,
                () -> service.createDraft(73L, "full_profile", request));

        assertPromptInvalid(error);
        verifyNoInteractions(
                adminAuthContext, templateMapper, versionMapper, auditMapper, renderer);
    }

    @Test
    void authenticatedOperatorMustMatchMethodOperatorBeforeAnyMapperRead() {
        authenticate(74L, "Different Admin");

        BizException error = assertThrows(
                BizException.class,
                () -> service.createDraft(73L, "full_profile", createReq(null, 4)));

        assertEquals(ResultCode.FORBIDDEN.getCode(), error.getCode());
        assertEquals(ResultCode.FORBIDDEN.getMessage(), error.getMessage());
        verify(adminAuthContext).requireCurrentAdmin();
        verifyNoInteractions(templateMapper, versionMapper, auditMapper, renderer);
    }

    @Test
    void createDraftRejectsExistingDraftAndInvalidSourcesBeforeInsert() {
        authenticate(73L, "Prompt Admin");
        AiProfileImportPromptTemplate withDraft =
                template(11L, "full_profile", 91L, 101L, 4, 0);
        AiProfileImportPromptTemplate withoutDraft =
                template(11L, "full_profile", 91L, null, 4, 0);
        AiProfileImportPromptVersion nonReleased = governedVersion(88L, 11L, 2, "draft", 6);
        when(templateMapper.selectByCodeForUpdate("full_profile"))
                .thenReturn(withDraft, withoutDraft, withoutDraft);
        when(versionMapper.selectOwnedForUpdate(11L, 88L)).thenReturn(nonReleased, null);

        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.createDraft(73L, "full_profile", createReq(null, 4))));
        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.createDraft(73L, "full_profile", createReq(88L, 4))));
        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.createDraft(73L, "full_profile", createReq(88L, 4))));

        verify(versionMapper, never()).selectNextVersionNo(anyLong());
        verify(versionMapper, never()).insert(any());
        verify(templateMapper, never()).attachDraftIfExpected(anyLong(), anyLong(), anyInt());
        verifyNoInteractions(auditMapper, renderer);
    }

    @Test
    void createDraftRejectsCrossTemplateSourceBeforeVersionNumberOrInsert() {
        authenticate(73L, "Prompt Admin");
        AiProfileImportPromptTemplate template =
                template(11L, "full_profile", 91L, null, 4, 0);
        AiProfileImportPromptVersion crossTemplate =
                governedVersion(88L, 12L, 2, "released", 6);
        when(templateMapper.selectByCodeForUpdate("full_profile")).thenReturn(template);
        when(versionMapper.selectOwnedForUpdate(11L, 88L)).thenReturn(crossTemplate);

        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.createDraft(73L, "full_profile", createReq(88L, 4))));

        verify(versionMapper, never()).selectNextVersionNo(anyLong());
        verify(versionMapper, never()).insert(any());
        verifyNoInteractions(auditMapper, renderer);
    }

    @Test
    void createDraftRejectsSoftDeletedSourceBeforeVersionNumberOrInsert() {
        authenticate(73L, "Prompt Admin");
        AiProfileImportPromptTemplate template =
                template(11L, "full_profile", 91L, null, 4, 0);
        AiProfileImportPromptVersion deleted =
                governedVersion(88L, 11L, 2, "released", 6);
        deleted.setDeleted(1);
        when(templateMapper.selectByCodeForUpdate("full_profile")).thenReturn(template);
        when(versionMapper.selectOwnedForUpdate(11L, 88L)).thenReturn(deleted);

        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.createDraft(73L, "full_profile", createReq(88L, 4))));

        verify(versionMapper, never()).selectNextVersionNo(anyLong());
        verify(versionMapper, never()).insert(any());
        verifyNoInteractions(auditMapper, renderer);
    }

    @Test
    void createDraftRejectsBootstrapDraftBeforeTryingMissingActiveSource() {
        authenticate(73L, "Prompt Admin");
        AiProfileImportPromptTemplate bootstrap =
                template(11L, "full_profile", null, 101L, 4, 0);
        when(templateMapper.selectByCodeForUpdate("full_profile")).thenReturn(bootstrap);

        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.createDraft(73L, "full_profile", createReq(null, 4))));

        verifyNoInteractions(versionMapper, auditMapper, renderer);
    }

    @Test
    void createDraftRejectsTemplateAndRequestVersionMismatchBeforeSourceRead() {
        authenticate(73L, "Prompt Admin");
        AiProfileImportPromptTemplate locked =
                template(11L, "full_profile", 91L, null, 5, 0);
        when(templateMapper.selectByCodeForUpdate("full_profile")).thenReturn(locked);

        BizException error = assertThrows(
                BizException.class,
                () -> service.createDraft(73L, "full_profile", createReq(null, 4)));

        assertVersionConflict(error);
        verifyNoInteractions(versionMapper, auditMapper, renderer);
    }

    @Test
    void createDraftStopsImmediatelyWhenInsertDoesNotAffectExactlyOneRow() {
        authenticate(73L, "Prompt Admin");
        AiProfileImportPromptTemplate locked =
                template(11L, "full_profile", 91L, null, 4, 0);
        AiProfileImportPromptVersion source = governedVersion(91L, 11L, 3, "released", 9);
        when(templateMapper.selectByCodeForUpdate("full_profile")).thenReturn(locked);
        when(versionMapper.selectOwnedForUpdate(11L, 91L)).thenReturn(source);
        when(versionMapper.selectNextVersionNo(11L)).thenReturn(4);
        when(renderer.contentSha256(eq(locked), any())).thenReturn("new-hash");
        when(versionMapper.insert(any())).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.createDraft(73L, "full_profile", createReq(null, 4)));

        verify(templateMapper, never()).attachDraftIfExpected(anyLong(), anyLong(), anyInt());
        verifyNoInteractions(auditMapper);
    }

    @Test
    void createDraftMapsAttachConflictAndRequiresItsAuditWrite() {
        authenticate(73L, "Prompt Admin");
        AiProfileImportPromptTemplate locked =
                template(11L, "full_profile", 91L, null, 4, 0);
        AiProfileImportPromptVersion source = governedVersion(91L, 11L, 3, "released", 9);
        when(templateMapper.selectByCodeForUpdate("full_profile")).thenReturn(locked, locked);
        when(versionMapper.selectOwnedForUpdate(11L, 91L)).thenReturn(source, source);
        when(versionMapper.selectNextVersionNo(11L)).thenReturn(4, 4);
        when(renderer.contentSha256(eq(locked), any())).thenReturn("new-hash", "new-hash");
        doAnswer(invocation -> {
                    invocation.<AiProfileImportPromptVersion>getArgument(0)
                            .setPromptVersionId(101L);
                    return 1;
                })
                .when(versionMapper)
                .insert(any());
        when(templateMapper.attachDraftIfExpected(11L, 101L, 4)).thenReturn(0, 1);
        when(auditMapper.insertAudit(any())).thenReturn(0);

        assertVersionConflict(assertThrows(
                BizException.class,
                () -> service.createDraft(73L, "full_profile", createReq(null, 4))));
        verifyNoInteractions(auditMapper);

        assertThrows(
                IllegalStateException.class,
                () -> service.createDraft(73L, "full_profile", createReq(null, 4)));
        verify(templateMapper, never()).selectById(anyLong());
        verify(versionMapper, never()).selectSummariesByTemplateId(anyLong());
    }

    @Test
    void updateDraftLocksLocatorTemplateAndOwnedDraftThenReturnsFreshDetail()
            throws Exception {
        authenticate(73L, "Editor Admin");
        AiProfileImportPromptVersion locator = versionLocator(11L, 101L);
        AiProfileImportPromptTemplate locked =
                template(11L, "full_profile", 91L, 101L, 4, 0);
        AiProfileImportPromptVersion draft = governedVersion(101L, 11L, 4, "draft", 7);
        draft.setContentSha256("old-hash");
        draft.setTestStatus("success");
        draft.setTestedContentSha256("old-hash");
        AiProfileImportPromptVersion fresh = governedVersion(101L, 11L, 4, "draft", 8);
        fresh.setVersionLabel("Edited draft");
        fresh.setSystemPromptBody("new-system-body");
        fresh.setRepairPromptBody("new-repair-body");
        fresh.setChangeSummary("editor-only summary");
        fresh.setContentSha256("new-hash");
        fresh.setTestStatus("stale");
        fresh.setUpdateUserId(73L);
        fresh.setUpdateUserName("Editor Admin");
        when(versionMapper.selectById(101L)).thenReturn(locator);
        when(templateMapper.selectByIdForUpdate(11L)).thenReturn(locked);
        when(versionMapper.selectOwnedForUpdate(11L, 101L)).thenReturn(draft);
        when(renderer.contentSha256(eq(locked), any(AiProfileImportPromptVersion.class)))
                .thenReturn("new-hash");
        when(versionMapper.updateDraftIfExpected(any(), eq(7))).thenReturn(1);
        when(auditMapper.insertAudit(any())).thenReturn(1);
        when(versionMapper.selectOwnedDetail(11L, 101L)).thenReturn(fresh);
        ProfileImportPromptUpdateDraftReqDTO request = updateReq(
                7,
                "Edited draft",
                "new-system-body",
                "new-repair-body",
                "editor-only summary");

        ProfileImportPromptVersionDetailRespDTO result =
                service.updateDraft(73L, 101L, request);

        assertEquals("new-system-body", result.getSystemPromptBody());
        assertEquals("new-repair-body", result.getRepairPromptBody());
        assertEquals("new-hash", result.getContentSha256());
        assertEquals("stale", result.getTestStatus());
        assertEquals(8, result.getVersion());
        assertEquals(73L, result.getUpdateUserId());
        assertEquals("Editor Admin", result.getUpdateUserName());

        ArgumentCaptor<AiProfileImportPromptVersion> updateCaptor =
                ArgumentCaptor.forClass(AiProfileImportPromptVersion.class);
        ArgumentCaptor<AiProfileImportPromptAudit> auditCaptor =
                ArgumentCaptor.forClass(AiProfileImportPromptAudit.class);
        InOrder order = Mockito.inOrder(
                adminAuthContext,
                versionMapper,
                templateMapper,
                renderer,
                auditMapper);
        order.verify(adminAuthContext).requireCurrentAdmin();
        order.verify(versionMapper).selectById(101L);
        order.verify(templateMapper).selectByIdForUpdate(11L);
        order.verify(versionMapper).selectOwnedForUpdate(11L, 101L);
        order.verify(renderer).contentSha256(eq(locked), any(AiProfileImportPromptVersion.class));
        order.verify(versionMapper).updateDraftIfExpected(updateCaptor.capture(), eq(7));
        order.verify(auditMapper).insertAudit(auditCaptor.capture());
        order.verify(versionMapper).selectOwnedDetail(11L, 101L);

        AiProfileImportPromptVersion update = updateCaptor.getValue();
        assertEquals(101L, update.getPromptVersionId());
        assertEquals(11L, update.getTemplateId());
        assertEquals("Edited draft", update.getVersionLabel());
        assertEquals("new-system-body", update.getSystemPromptBody());
        assertEquals("new-repair-body", update.getRepairPromptBody());
        assertEquals("editor-only summary", update.getChangeSummary());
        assertEquals(draft.getSchemaVersion(), update.getSchemaVersion());
        assertEquals(draft.getContractVersion(), update.getContractVersion());
        assertEquals("new-hash", update.getContentSha256());
        assertEquals(73L, update.getUpdateUserId());
        assertEquals("Editor Admin", update.getUpdateUserName());
        assertNull(update.getTestedContentSha256());
        assertNull(update.getTestedRuntimeSha256());

        AiProfileImportPromptAudit audit = auditCaptor.getValue();
        assertDraftAudit(
                audit,
                "draft_update",
                "DRAFT_UPDATED",
                11L,
                101L,
                101L,
                101L,
                "new-hash",
                73L,
                "Editor Admin");
        assertEquals(draft.getSchemaVersion(), audit.getSchemaVersion());
        assertEquals(draft.getContractVersion(), audit.getContractVersion());
        String auditValues = allInstanceFieldValues(audit);
        assertFalse(auditValues.contains("new-system-body"));
        assertFalse(auditValues.contains("new-repair-body"));
        assertFalse(auditValues.contains("editor-only summary"));
    }

    @Test
    void updateDraftRejectsNullVersionLabelBeforeAuthenticationOrDependencies() {
        assertInvalidUpdateMetadata(null, "summary");
    }

    @Test
    void updateDraftRejectsBlankVersionLabelBeforeAuthenticationOrDependencies() {
        assertInvalidUpdateMetadata(" \t\n", "summary");
    }

    @Test
    void updateDraftAcceptsExactly128LabelCodePointsIncludingAstralWithoutRewriting() {
        String label = "L".repeat(127) + "\uD83D\uDE80";
        assertEquals(128, label.codePointCount(0, label.length()));
        assertEquals(129, label.length());

        assertAcceptedUpdateMetadata(label, "summary");
    }

    @Test
    void updateDraftRejects129LabelCodePointsIncludingAstralBeforeDependencies() {
        String label = "L".repeat(128) + "\uD83D\uDE80";
        assertEquals(129, label.codePointCount(0, label.length()));
        assertEquals(130, label.length());

        assertInvalidUpdateMetadata(label, "summary");
    }

    @Test
    void updateDraftAcceptsNullChangeSummaryWithoutRewriting() {
        assertAcceptedUpdateMetadata("Valid label", null);
    }

    @Test
    void updateDraftAcceptsBlankChangeSummaryWithoutTrimming() {
        assertAcceptedUpdateMetadata("Valid label", " \t\n");
    }

    @Test
    void updateDraftAcceptsExactly500SummaryCodePointsIncludingAstralWithoutRewriting() {
        String summary = "S".repeat(499) + "\uD83D\uDE80";
        assertEquals(500, summary.codePointCount(0, summary.length()));
        assertEquals(501, summary.length());

        assertAcceptedUpdateMetadata("Valid label", summary);
    }

    @Test
    void updateDraftRejects501SummaryCodePointsIncludingAstralBeforeDependencies() {
        String summary = "S".repeat(500) + "\uD83D\uDE80";
        assertEquals(501, summary.codePointCount(0, summary.length()));
        assertEquals(502, summary.length());

        assertInvalidUpdateMetadata("Valid label", summary);
    }

    @Test
    void updateDraftRejectsWrongPointerStateOrExpectedVersionBeforeConditionalUpdate() {
        authenticate(73L, "Editor Admin");
        AiProfileImportPromptVersion locator = versionLocator(11L, 101L);
        AiProfileImportPromptTemplate wrongPointer =
                template(11L, "full_profile", 91L, 102L, 4, 0);
        AiProfileImportPromptTemplate matchingPointer =
                template(11L, "full_profile", 91L, 101L, 4, 0);
        AiProfileImportPromptVersion draft = governedVersion(101L, 11L, 4, "draft", 7);
        when(versionMapper.selectById(101L)).thenReturn(locator, locator);
        when(templateMapper.selectByIdForUpdate(11L))
                .thenReturn(wrongPointer, matchingPointer);
        when(versionMapper.selectOwnedForUpdate(11L, 101L)).thenReturn(draft, draft);

        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.updateDraft(
                        73L,
                        101L,
                        updateReq(7, "label", "system", "repair", "summary"))));
        assertVersionConflict(assertThrows(
                BizException.class,
                () -> service.updateDraft(
                        73L,
                        101L,
                        updateReq(6, "label", "system", "repair", "summary"))));

        verify(versionMapper, never()).updateDraftIfExpected(any(), anyInt());
        verifyNoInteractions(auditMapper, renderer);
    }

    @Test
    void updateDraftMapsConditionalConflictAndStopsBeforeAuditOrFreshRead() {
        stubLockedDraftForUpdate(73L, 7);
        when(renderer.contentSha256(any(), any())).thenReturn("new-hash");
        when(versionMapper.updateDraftIfExpected(any(), eq(7))).thenReturn(0);

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateDraft(
                        73L,
                        101L,
                        updateReq(7, "label", "new-system", "new-repair", "summary")));

        assertVersionConflict(error);
        verifyNoInteractions(auditMapper);
        verify(versionMapper, never()).selectOwnedDetail(anyLong(), anyLong());
    }

    @Test
    void updateDraftRequiresDedicatedAuditBeforeFreshResponse() {
        stubLockedDraftForUpdate(73L, 7);
        when(renderer.contentSha256(any(), any())).thenReturn("new-hash");
        when(versionMapper.updateDraftIfExpected(any(), eq(7))).thenReturn(1);
        when(auditMapper.insertAudit(any())).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.updateDraft(
                        73L,
                        101L,
                        updateReq(7, "label", "new-system", "new-repair", "summary")));

        verify(versionMapper, never()).selectOwnedDetail(anyLong(), anyLong());
    }

    @Test
    void updateDraftPropagatesAuditExceptionWithoutFreshResponseOrDerivedBusinessError()
            throws Exception {
        String privateMarker = "PRIVATE_AUDIT_FAILURE_MARKER_41f8";
        RuntimeException auditFailure = new RuntimeException(privateMarker);
        stubLockedDraftForUpdate(73L, 7);
        when(renderer.contentSha256(any(), any())).thenReturn("new-hash");
        when(versionMapper.updateDraftIfExpected(any(), eq(7))).thenReturn(1);
        when(auditMapper.insertAudit(any())).thenThrow(auditFailure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.updateDraft(
                        73L,
                        101L,
                        updateReq(7, "label", "new-system", "new-repair", "summary")));

        assertSame(auditFailure, thrown);
        assertFalse(thrown instanceof BizException);
        ArgumentCaptor<AiProfileImportPromptAudit> auditCaptor =
                ArgumentCaptor.forClass(AiProfileImportPromptAudit.class);
        verify(auditMapper).insertAudit(auditCaptor.capture());
        assertFalse(allInstanceFieldValues(auditCaptor.getValue()).contains(privateMarker));
        verify(versionMapper, never()).selectOwnedDetail(anyLong(), anyLong());
    }

    @Test
    void abandonDraftValidatesActiveThenTargetAndReturnsFreshTemplateSummary()
            throws Exception {
        authenticate(73L, "Release Admin");
        AiProfileImportPromptVersion locator = versionLocator(11L, 101L);
        AiProfileImportPromptTemplate locked =
                template(11L, "full_profile", 91L, 101L, 4, 0);
        AiProfileImportPromptVersion active = governedVersion(91L, 11L, 3, "released", 9);
        active.setContentSha256("active-hash");
        AiProfileImportPromptVersion draft = governedVersion(101L, 11L, 4, "draft", 2);
        draft.setContentSha256("draft-hash");
        AiProfileImportPromptTemplate fresh =
                template(11L, "full_profile", 91L, null, 5, 0);
        when(versionMapper.selectById(101L)).thenReturn(locator);
        when(templateMapper.selectByIdForUpdate(11L)).thenReturn(locked);
        when(versionMapper.selectOwned(11L, 91L)).thenReturn(active);
        when(versionMapper.selectOwnedForUpdate(11L, 101L)).thenReturn(draft);
        when(versionMapper.abandonDraftIfExpected(
                        11L, 101L, 2, 73L, "Release Admin"))
                .thenReturn(1);
        when(templateMapper.clearDraftIfExpected(11L, 101L, 4)).thenReturn(1);
        when(auditMapper.insertAudit(any())).thenReturn(1);
        when(templateMapper.selectById(11L)).thenReturn(fresh);
        when(versionMapper.selectSummariesByTemplateId(11L)).thenReturn(List.of(active));

        ProfileImportPromptTemplateSummaryRespDTO result = service.abandonDraft(
                73L,
                101L,
                actionReq("DRAFT_INVALID", 4, 2));

        assertNull(result.getDraftVersionId());
        assertEquals(91L, result.getActiveVersionId());
        assertEquals(3, result.getActiveVersionNo());
        assertEquals(5, result.getVersion());
        ArgumentCaptor<AiProfileImportPromptAudit> auditCaptor =
                ArgumentCaptor.forClass(AiProfileImportPromptAudit.class);
        InOrder order = Mockito.inOrder(
                adminAuthContext, versionMapper, templateMapper, auditMapper);
        order.verify(adminAuthContext).requireCurrentAdmin();
        order.verify(versionMapper).selectById(101L);
        order.verify(templateMapper).selectByIdForUpdate(11L);
        order.verify(versionMapper).selectOwned(11L, 91L);
        order.verify(versionMapper).selectOwnedForUpdate(11L, 101L);
        order.verify(versionMapper)
                .abandonDraftIfExpected(11L, 101L, 2, 73L, "Release Admin");
        order.verify(templateMapper).clearDraftIfExpected(11L, 101L, 4);
        order.verify(auditMapper).insertAudit(auditCaptor.capture());
        order.verify(templateMapper).selectById(11L);
        order.verify(versionMapper).selectSummariesByTemplateId(11L);
        AiProfileImportPromptAudit audit = auditCaptor.getValue();
        assertDraftAudit(
                audit,
                "draft_abandon",
                "DRAFT_INVALID",
                11L,
                101L,
                101L,
                91L,
                "draft-hash",
                73L,
                "Release Admin");
        assertEquals(draft.getSchemaVersion(), audit.getSchemaVersion());
        assertEquals(draft.getContractVersion(), audit.getContractVersion());
    }

    @Test
    void abandonReasonIsRejectedBeforeAuthenticationAndEveryMapperInteraction() {
        ProfileImportPromptVersionActionReqDTO request =
                actionReq("System Prompt body", 4, 2);

        BizException error = assertThrows(
                BizException.class,
                () -> service.abandonDraft(73L, 101L, request));

        assertPromptInvalid(error);
        assertFalse(error.getMessage().contains("System Prompt body"));
        verifyNoInteractions(
                adminAuthContext, templateMapper, versionMapper, auditMapper, renderer);
    }

    @Test
    void bootstrapDraftCannotBeAbandonedBeforeFirstRelease() {
        authenticate(73L, "Release Admin");
        AiProfileImportPromptVersion locator = versionLocator(11L, 101L);
        AiProfileImportPromptTemplate bootstrap =
                template(11L, "full_profile", null, 101L, 4, 0);
        when(versionMapper.selectById(101L)).thenReturn(locator);
        when(templateMapper.selectByIdForUpdate(11L)).thenReturn(bootstrap);

        BizException error = assertThrows(
                BizException.class,
                () -> service.abandonDraft(
                        73L,
                        101L,
                        actionReq("DRAFT_INVALID", 4, 2)));

        assertStateConflict(error);
        verify(versionMapper, never()).selectOwned(anyLong(), anyLong());
        verify(versionMapper, never()).selectOwnedForUpdate(anyLong(), anyLong());
        verify(versionMapper, never())
                .abandonDraftIfExpected(
                        anyLong(), anyLong(), anyInt(), anyLong(), anyString());
        verifyNoInteractions(auditMapper, renderer);
    }

    @Test
    void abandonDraftRejectsMissingOrDamagedActiveBeforeLockingTarget() {
        authenticate(73L, "Release Admin");
        AiProfileImportPromptVersion locator = versionLocator(11L, 101L);
        AiProfileImportPromptTemplate locked =
                template(11L, "full_profile", 91L, 101L, 4, 0);
        AiProfileImportPromptVersion damagedActive =
                governedVersion(91L, 11L, 3, "draft", 9);
        when(versionMapper.selectById(101L)).thenReturn(locator, locator);
        when(templateMapper.selectByIdForUpdate(11L)).thenReturn(locked, locked);
        when(versionMapper.selectOwned(11L, 91L)).thenReturn(null, damagedActive);

        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.abandonDraft(
                        73L, 101L, actionReq("DRAFT_INVALID", 4, 2))));
        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.abandonDraft(
                        73L, 101L, actionReq("DRAFT_SUPERSEDED", 4, 2))));

        verify(versionMapper, never()).selectOwnedForUpdate(11L, 101L);
        verify(versionMapper, never())
                .abandonDraftIfExpected(
                        anyLong(), anyLong(), anyInt(), anyLong(), anyString());
        verifyNoInteractions(auditMapper, renderer);
    }

    @Test
    void abandonDraftValidatesPointerAndBothExpectedVersionsBeforeUpdates() {
        authenticate(73L, "Release Admin");
        AiProfileImportPromptVersion locator = versionLocator(11L, 101L);
        AiProfileImportPromptTemplate wrongPointer =
                template(11L, "full_profile", 91L, 102L, 4, 0);
        AiProfileImportPromptVersion active = governedVersion(91L, 11L, 3, "released", 9);
        AiProfileImportPromptVersion target = governedVersion(101L, 11L, 4, "draft", 2);
        when(versionMapper.selectById(101L)).thenReturn(locator);
        when(templateMapper.selectByIdForUpdate(11L)).thenReturn(wrongPointer);
        when(versionMapper.selectOwned(11L, 91L)).thenReturn(active);
        when(versionMapper.selectOwnedForUpdate(11L, 101L)).thenReturn(target);

        BizException error = assertThrows(
                BizException.class,
                () -> service.abandonDraft(
                        73L,
                        101L,
                        actionReq("DRAFT_INVALID", 4, 2)));

        assertStateConflict(error);
        verify(versionMapper, never())
                .abandonDraftIfExpected(
                        anyLong(), anyLong(), anyInt(), anyLong(), anyString());
        verify(templateMapper, never()).clearDraftIfExpected(anyLong(), anyLong(), anyInt());
        verifyNoInteractions(auditMapper, renderer);
    }

    @Test
    void abandonDraftStopsAtEachConditionalWriteConflict() {
        stubLockedDraftForAbandon(73L, 4, 2);
        when(versionMapper.abandonDraftIfExpected(
                        11L, 101L, 2, 73L, "Release Admin"))
                .thenReturn(0);

        assertVersionConflict(assertThrows(
                BizException.class,
                () -> service.abandonDraft(
                        73L, 101L, actionReq("DRAFT_INVALID", 4, 2))));

        verify(templateMapper, never()).clearDraftIfExpected(anyLong(), anyLong(), anyInt());
        verifyNoInteractions(auditMapper);
    }

    @Test
    void abandonDraftRequiresPointerClearAndDedicatedAudit() {
        stubLockedDraftForAbandon(73L, 4, 2);
        when(versionMapper.abandonDraftIfExpected(
                        11L, 101L, 2, 73L, "Release Admin"))
                .thenReturn(1);
        when(templateMapper.clearDraftIfExpected(11L, 101L, 4)).thenReturn(0);

        assertVersionConflict(assertThrows(
                BizException.class,
                () -> service.abandonDraft(
                        73L, 101L, actionReq("DRAFT_INVALID", 4, 2))));
        verifyNoInteractions(auditMapper);
    }

    @Test
    void abandonDraftAuditFailureStopsBeforeFreshResponse() {
        stubLockedDraftForAbandon(73L, 4, 2);
        when(versionMapper.abandonDraftIfExpected(
                        11L, 101L, 2, 73L, "Release Admin"))
                .thenReturn(1);
        when(templateMapper.clearDraftIfExpected(11L, 101L, 4)).thenReturn(1);
        when(auditMapper.insertAudit(any())).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.abandonDraft(
                        73L, 101L, actionReq("DRAFT_INVALID", 4, 2)));

        verify(templateMapper, never()).selectById(anyLong());
        verify(versionMapper, never()).selectSummariesByTemplateId(anyLong());
    }

    @Test
    void deferredActionsValidateReasonAndOperatorThenFailClosedWithoutMapperCalls() {
        authenticate(73L, "Future Admin");

        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.test(73L, 101L)));
        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.publish(
                        73L,
                        101L,
                        actionReq("QUALITY_ADJUSTMENT", 4, 2))));
        ProfileImportPromptRestoreReqDTO restore = new ProfileImportPromptRestoreReqDTO();
        restore.setReasonCode("INCIDENT_ROLLBACK");
        restore.setExpectedTemplateVersion(4);
        assertStateConflict(assertThrows(
                BizException.class,
                () -> service.restore(73L, "full_profile", 91L, restore)));

        verify(adminAuthContext, Mockito.times(3)).requireCurrentAdmin();
        verifyNoInteractions(templateMapper, versionMapper, auditMapper, renderer);
    }

    @Test
    void deferredPublishAndRestoreRejectInvalidReasonBeforeAuthenticationOrMappers() {
        assertPromptInvalid(assertThrows(
                BizException.class,
                () -> service.publish(
                        73L,
                        101L,
                        actionReq("DRAFT_INVALID", 4, 2))));
        ProfileImportPromptRestoreReqDTO restore = new ProfileImportPromptRestoreReqDTO();
        restore.setReasonCode("INITIAL_RELEASE");
        restore.setExpectedTemplateVersion(4);
        assertPromptInvalid(assertThrows(
                BizException.class,
                () -> service.restore(73L, "full_profile", 91L, restore)));

        verifyNoInteractions(
                adminAuthContext, templateMapper, versionMapper, auditMapper, renderer);
    }

    @Test
    void strictWriteDtosExposeOnlyTheirExactClientFields() {
        assertDeclaredFieldTypes(
                ProfileImportPromptCreateDraftReqDTO.class,
                fields(
                        "sourceVersionId", Long.class,
                        "expectedTemplateVersion", Integer.class));
        assertDeclaredFieldTypes(
                ProfileImportPromptUpdateDraftReqDTO.class,
                fields(
                        "versionLabel", String.class,
                        "systemPromptBody", String.class,
                        "repairPromptBody", String.class,
                        "changeSummary", String.class,
                        "expectedVersion", Integer.class));
        assertDeclaredFieldTypes(
                ProfileImportPromptVersionActionReqDTO.class,
                fields(
                        "reasonCode", String.class,
                        "expectedTemplateVersion", Integer.class,
                        "expectedVersion", Integer.class));
        assertDeclaredFieldTypes(
                ProfileImportPromptRestoreReqDTO.class,
                fields(
                        "reasonCode", String.class,
                        "expectedTemplateVersion", Integer.class));

        for (Class<?> requestType : List.of(
                ProfileImportPromptCreateDraftReqDTO.class,
                ProfileImportPromptUpdateDraftReqDTO.class,
                ProfileImportPromptVersionActionReqDTO.class,
                ProfileImportPromptRestoreReqDTO.class)) {
            assertEquals(ProfileImportPromptStrictWriteDTO.class, requestType.getSuperclass());
        }
    }

    @Test
    void strictGuardHasTheExactJacksonCaptureContract() throws Exception {
        Field unexpectedFields =
                ProfileImportPromptStrictWriteDTO.class.getDeclaredField("unexpectedFields");
        assertEquals(Set.class, unexpectedFields.getType());

        Method capture = ProfileImportPromptStrictWriteDTO.class.getDeclaredMethod(
                "captureUnexpectedField", String.class, JsonNode.class);
        assertNotNull(capture.getAnnotation(JsonAnySetter.class));
        assertEquals(void.class, capture.getReturnType());

        Method require = ProfileImportPromptStrictWriteDTO.class.getDeclaredMethod(
                "requireNoUnexpectedFields");
        assertEquals(void.class, require.getReturnType());
        assertTrue(Modifier.isPublic(require.getModifiers()));
    }

    @Test
    void jacksonDiscardsUnknownValuesAndRejectsTheirNamesWithoutEchoingValues()
            throws Exception {
        String secretMarker = "SENSITIVE_UNKNOWN_VALUE_7c61";
        String json = """
                {
                  "reasonCode": "DRAFT_INVALID",
                  "expectedTemplateVersion": 4,
                  "expectedVersion": 2,
                  "operatorId": 73,
                  "state": "released",
                  "reason": {"nested": "%s"},
                  "apiKey": ["%s"],
                  "systemPromptBody": "%s"
                }
                """.formatted(secretMarker, secretMarker, secretMarker);

        ProfileImportPromptVersionActionReqDTO request =
                new ObjectMapper().readValue(json, ProfileImportPromptVersionActionReqDTO.class);

        assertEquals("DRAFT_INVALID", request.getReasonCode());
        assertEquals(4, request.getExpectedTemplateVersion());
        assertEquals(2, request.getExpectedVersion());
        Field unexpectedField =
                ProfileImportPromptStrictWriteDTO.class.getDeclaredField("unexpectedFields");
        unexpectedField.setAccessible(true);
        Object captured = unexpectedField.get(request);
        assertInstanceOf(LinkedHashSet.class, captured);
        assertEquals(
                List.of("operatorId", "state", "reason", "apiKey", "systemPromptBody"),
                new ArrayList<>((Set<?>) captured));
        assertFalse(request.toString().contains(secretMarker));
        assertFalse(new ObjectMapper().writeValueAsString(request).contains(secretMarker));
        assertFalse(allInstanceFieldValues(request).contains(secretMarker));

        BizException error = assertThrows(
                BizException.class, request::requireNoUnexpectedFields);
        assertPromptInvalid(error);
        assertFalse(error.getMessage().contains(secretMarker));

        assertDoesNotThrow(
                new ProfileImportPromptVersionActionReqDTO()::requireNoUnexpectedFields);
    }

    @Test
    void reasonCodeValuesAndAllowedClientSubsetsAreExact() {
        assertArrayEquals(
                new String[] {
                    "INITIAL_RELEASE",
                    "QUALITY_ADJUSTMENT",
                    "CONFIG_ALIGNMENT",
                    "QUALITY_REGRESSION",
                    "INCIDENT_ROLLBACK",
                    "DRAFT_SUPERSEDED",
                    "DRAFT_INVALID",
                    "DRAFT_CREATED_CURRENT",
                    "DRAFT_CREATED_HISTORY",
                    "DRAFT_UPDATED",
                    "TEST_EXECUTED"
                },
                Arrays.stream(ProfileImportPromptReasonCode.values())
                        .map(Enum::name)
                        .toArray(String[]::new));

        assertEquals(
                ProfileImportPromptReasonCode.INITIAL_RELEASE,
                ProfileImportPromptReasonCode.requirePublish("INITIAL_RELEASE"));
        assertEquals(
                ProfileImportPromptReasonCode.QUALITY_ADJUSTMENT,
                ProfileImportPromptReasonCode.requirePublish("QUALITY_ADJUSTMENT"));
        assertEquals(
                ProfileImportPromptReasonCode.CONFIG_ALIGNMENT,
                ProfileImportPromptReasonCode.requirePublish("CONFIG_ALIGNMENT"));
        assertEquals(
                ProfileImportPromptReasonCode.QUALITY_REGRESSION,
                ProfileImportPromptReasonCode.requireRestore("QUALITY_REGRESSION"));
        assertEquals(
                ProfileImportPromptReasonCode.INCIDENT_ROLLBACK,
                ProfileImportPromptReasonCode.requireRestore("INCIDENT_ROLLBACK"));
        assertEquals(
                ProfileImportPromptReasonCode.DRAFT_SUPERSEDED,
                ProfileImportPromptReasonCode.requireAbandon("DRAFT_SUPERSEDED"));
        assertEquals(
                ProfileImportPromptReasonCode.DRAFT_INVALID,
                ProfileImportPromptReasonCode.requireAbandon("DRAFT_INVALID"));
    }

    @Test
    void invalidWrongSubsetInternalAndSensitiveReasonsUseOneStableError() {
        assertRejectedReasons(
                ProfileImportPromptReasonCode::requirePublish,
                null,
                "",
                "   ",
                "QUALITY_REGRESSION",
                "DRAFT_CREATED_CURRENT",
                "DRAFT_UPDATED",
                "sk-private-api-key",
                "\u7528\u6237\u539f\u59cb\u526a\u8d34\u677f\u6587\u672c",
                "fixture full body",
                "System Prompt body");
        assertRejectedReasons(
                ProfileImportPromptReasonCode::requireRestore,
                null,
                "INITIAL_RELEASE",
                "DRAFT_CREATED_HISTORY",
                "TEST_EXECUTED",
                "sk-private-api-key");
        assertRejectedReasons(
                ProfileImportPromptReasonCode::requireAbandon,
                null,
                "CONFIG_ALIGNMENT",
                "DRAFT_CREATED_CURRENT",
                "TEST_EXECUTED",
                "Repair Prompt body");
    }

    @Test
    void promptConflictErrorCodesAndMessagesAreStable() {
        assertEquals(46018, ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_VERSION_CONFLICT.code());
        assertEquals(
                "PROFILE_IMPORT_PROMPT_VERSION_CONFLICT",
                ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_VERSION_CONFLICT.errorCode());
        assertEquals(
                "Prompt \u7248\u672c\u5df2\u53d8\u5316\uff0c\u8bf7\u91cd\u65b0\u52a0\u8f7d\u540e\u4eba\u5de5\u5408\u5e76",
                ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_VERSION_CONFLICT.message());
        assertEquals(46019, ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_INVALID.code());
        assertEquals(INVALID_MESSAGE, ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_INVALID.message());
        assertEquals(46022, ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_STATE_CONFLICT.code());
        assertEquals(
                "PROFILE_IMPORT_PROMPT_STATE_CONFLICT",
                ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_STATE_CONFLICT.errorCode());
        assertEquals(
                "Prompt \u6a21\u677f\u5f53\u524d\u72b6\u6001\u4e0d\u5141\u8bb8\u8be5\u64cd\u4f5c",
                ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_STATE_CONFLICT.message());
        assertTrue(Arrays.stream(ProfileDomainErrorCode.values())
                .noneMatch(code -> code.code() == 46020 || code.code() == 46021));
    }

    @Test
    void templateVersionAndTestResponsesExposeOnlyTheirExactContracts() {
        assertDeclaredFieldTypes(
                ProfileImportPromptTemplateSummaryRespDTO.class,
                fields(
                        "templateId", Long.class,
                        "templateCode", String.class,
                        "scene", String.class,
                        "displayName", String.class,
                        "activeVersionId", Long.class,
                        "activeVersionNo", Integer.class,
                        "activeVersionLabel", String.class,
                        "activeContentSha256", String.class,
                        "activeTestStatus", String.class,
                        "draftVersionId", Long.class,
                        "draftVersionNo", Integer.class,
                        "draftVersionLabel", String.class,
                        "draftContentSha256", String.class,
                        "draftTestStatus", String.class,
                        "version", Integer.class));
        assertDeclaredFieldTypes(
                ProfileImportPromptVersionSummaryRespDTO.class,
                fields(
                        "promptVersionId", Long.class,
                        "templateId", Long.class,
                        "versionNo", Integer.class,
                        "versionLabel", String.class,
                        "lifecycleStatus", String.class,
                        "contentSha256", String.class,
                        "testStatus", String.class,
                        "testedModelName", String.class,
                        "testErrorCode", String.class,
                        "testCandidateCount", Integer.class,
                        "testWorkCount", Integer.class,
                        "testedBy", Long.class,
                        "testedAt", LocalDateTime.class,
                        "releasedBy", Long.class,
                        "releasedAt", LocalDateTime.class,
                        "updateUserId", Long.class,
                        "updateUserName", String.class,
                        "lastUpdate", LocalDateTime.class,
                        "version", Integer.class));
        assertEquals(
                ProfileImportPromptVersionSummaryRespDTO.class,
                ProfileImportPromptVersionDetailRespDTO.class.getSuperclass());
        assertDeclaredFieldTypes(
                ProfileImportPromptVersionDetailRespDTO.class,
                fields(
                        "systemPromptBody", String.class,
                        "repairPromptBody", String.class,
                        "schemaVersion", String.class,
                        "contractVersion", String.class,
                        "changeSummary", String.class));
        assertDeclaredFieldTypes(
                ProfileImportPromptTestResultRespDTO.class,
                fields(
                        "promptVersionId", Long.class,
                        "contentSha256", String.class,
                        "runtimeSha256", String.class,
                        "fixtureCode", String.class,
                        "fixtureVersion", String.class,
                        "fixtureSha256", String.class,
                        "modelName", String.class,
                        "configVersion", Integer.class,
                        "status", String.class,
                        "candidateCount", Integer.class,
                        "workCount", Integer.class,
                        "elapsedMs", Long.class,
                        "errorCode", String.class,
                        "testedBy", Long.class,
                        "testedAt", LocalDateTime.class));

        Set<String> summaryFields = allInstanceFieldNames(
                ProfileImportPromptVersionSummaryRespDTO.class);
        assertTrue(summaryFields.stream().noneMatch(Set.of(
                "systemPromptBody",
                "repairPromptBody",
                "schemaVersion",
                "contractVersion",
                "changeSummary")::contains));
        assertTrue(allInstanceFieldNames(ProfileImportPromptVersionDetailRespDTO.class)
                .containsAll(Set.of(
                        "systemPromptBody",
                        "repairPromptBody",
                        "schemaVersion",
                        "contractVersion",
                        "changeSummary")));
    }

    @Test
    void auditResponseMirrorsOnlyTheSanitizedAuditContract() {
        assertDeclaredFieldTypes(
                ProfileImportPromptAuditRespDTO.class,
                fields(
                        "promptAuditId", Long.class,
                        "templateId", Long.class,
                        "promptVersionId", Long.class,
                        "actionCode", String.class,
                        "fromVersionId", Long.class,
                        "toVersionId", Long.class,
                        "contentSha256", String.class,
                        "runtimeSha256", String.class,
                        "schemaVersion", String.class,
                        "contractVersion", String.class,
                        "fixtureCode", String.class,
                        "fixtureVersion", String.class,
                        "fixtureSha256", String.class,
                        "modelName", String.class,
                        "configVersion", Integer.class,
                        "testOperatorId", Long.class,
                        "testedAt", LocalDateTime.class,
                        "operatorId", Long.class,
                        "operatorName", String.class,
                        "reasonCode", String.class,
                        "resultStatus", String.class,
                        "errorCode", String.class,
                        "message", String.class,
                        "createTime", LocalDateTime.class));

        Set<String> fields = allInstanceFieldNames(ProfileImportPromptAuditRespDTO.class);
        for (String forbidden : List.of(
                "systemPromptBody",
                "repairPromptBody",
                "changeSummary",
                "freeReason",
                "rawText",
                "sourceText",
                "response",
                "apiKey")) {
            assertFalse(fields.contains(forbidden), forbidden);
        }
    }

    @Test
    void managementServiceDeclaresTheExactContract() throws Exception {
        assertEquals(10, ProfileImportPromptManagementService.class.getDeclaredMethods().length);
        assertListReturn(
                method("templates"), ProfileImportPromptTemplateSummaryRespDTO.class);
        assertListReturn(
                method("versions", String.class),
                ProfileImportPromptVersionSummaryRespDTO.class);
        assertMethod(
                method("version", Long.class),
                ProfileImportPromptVersionDetailRespDTO.class,
                Long.class);
        assertMethod(
                method(
                        "createDraft",
                        Long.class,
                        String.class,
                        ProfileImportPromptCreateDraftReqDTO.class),
                ProfileImportPromptTemplateSummaryRespDTO.class,
                Long.class,
                String.class,
                ProfileImportPromptCreateDraftReqDTO.class);
        assertMethod(
                method(
                        "updateDraft",
                        Long.class,
                        Long.class,
                        ProfileImportPromptUpdateDraftReqDTO.class),
                ProfileImportPromptVersionDetailRespDTO.class,
                Long.class,
                Long.class,
                ProfileImportPromptUpdateDraftReqDTO.class);
        assertMethod(
                method(
                        "abandonDraft",
                        Long.class,
                        Long.class,
                        ProfileImportPromptVersionActionReqDTO.class),
                ProfileImportPromptTemplateSummaryRespDTO.class,
                Long.class,
                Long.class,
                ProfileImportPromptVersionActionReqDTO.class);
        assertMethod(
                method("test", Long.class, Long.class),
                ProfileImportPromptTestResultRespDTO.class,
                Long.class,
                Long.class);
        assertMethod(
                method(
                        "publish",
                        Long.class,
                        Long.class,
                        ProfileImportPromptVersionActionReqDTO.class),
                ProfileImportPromptTemplateSummaryRespDTO.class,
                Long.class,
                Long.class,
                ProfileImportPromptVersionActionReqDTO.class);
        assertMethod(
                method(
                        "restore",
                        Long.class,
                        String.class,
                        Long.class,
                        ProfileImportPromptRestoreReqDTO.class),
                ProfileImportPromptTemplateSummaryRespDTO.class,
                Long.class,
                String.class,
                Long.class,
                ProfileImportPromptRestoreReqDTO.class);
        assertListReturn(method("audits"), ProfileImportPromptAuditRespDTO.class);
    }

    private static AiProfileImportPromptTemplate template(
            Long templateId,
            String templateCode,
            Long activeVersionId,
            Long draftVersionId,
            Integer version,
            Integer deleted) {
        AiProfileImportPromptTemplate template = new AiProfileImportPromptTemplate();
        template.setTemplateId(templateId);
        template.setTemplateCode(templateCode);
        template.setScene(templateCode);
        template.setDisplayName(templateCode + " display");
        template.setActiveVersionId(activeVersionId);
        template.setDraftVersionId(draftVersionId);
        template.setVersion(version);
        template.setDeleted(deleted);
        return template;
    }

    private static AiProfileImportPromptVersion version(
            Long promptVersionId,
            Long templateId,
            Integer versionNo,
            String lifecycleStatus,
            String contentSha256,
            Integer version) {
        AiProfileImportPromptVersion row = new AiProfileImportPromptVersion();
        row.setPromptVersionId(promptVersionId);
        row.setTemplateId(templateId);
        row.setVersionNo(versionNo);
        row.setVersionLabel("v" + versionNo);
        row.setLifecycleStatus(lifecycleStatus);
        row.setContentSha256(contentSha256);
        row.setTestStatus("untested");
        row.setVersion(version);
        row.setDeleted(0);
        return row;
    }

    private void authenticate(Long operatorId, String operatorName) {
        when(adminAuthContext.requireCurrentAdmin()).thenReturn(AdminAuthenticatedUser.builder()
                .adminUserId(operatorId)
                .account("prompt-admin")
                .userName(operatorName)
                .permissions(Set.of())
                .roleCodes(Set.of())
                .build());
    }

    private static ProfileImportPromptCreateDraftReqDTO createReq(
            Long sourceVersionId, Integer expectedTemplateVersion) {
        ProfileImportPromptCreateDraftReqDTO request =
                new ProfileImportPromptCreateDraftReqDTO();
        request.setSourceVersionId(sourceVersionId);
        request.setExpectedTemplateVersion(expectedTemplateVersion);
        return request;
    }

    private static ProfileImportPromptUpdateDraftReqDTO updateReq(
            Integer expectedVersion,
            String versionLabel,
            String systemPromptBody,
            String repairPromptBody,
            String changeSummary) {
        ProfileImportPromptUpdateDraftReqDTO request =
                new ProfileImportPromptUpdateDraftReqDTO();
        request.setExpectedVersion(expectedVersion);
        request.setVersionLabel(versionLabel);
        request.setSystemPromptBody(systemPromptBody);
        request.setRepairPromptBody(repairPromptBody);
        request.setChangeSummary(changeSummary);
        return request;
    }

    private static ProfileImportPromptVersionActionReqDTO actionReq(
            String reasonCode, Integer expectedTemplateVersion, Integer expectedVersion) {
        ProfileImportPromptVersionActionReqDTO request =
                new ProfileImportPromptVersionActionReqDTO();
        request.setReasonCode(reasonCode);
        request.setExpectedTemplateVersion(expectedTemplateVersion);
        request.setExpectedVersion(expectedVersion);
        return request;
    }

    private static AiProfileImportPromptVersion governedVersion(
            Long promptVersionId,
            Long templateId,
            Integer versionNo,
            String lifecycleStatus,
            Integer version) {
        AiProfileImportPromptVersion row =
                version(promptVersionId, templateId, versionNo, lifecycleStatus, null, version);
        row.setSystemPromptBody("governed-system-v" + versionNo);
        row.setRepairPromptBody("governed-repair-v" + versionNo);
        row.setSchemaVersion("profile-import-v1");
        row.setContractVersion("profile-import-contract-v1");
        row.setChangeSummary("source summary v" + versionNo);
        return row;
    }

    private static AiProfileImportPromptVersion versionLocator(
            Long templateId, Long promptVersionId) {
        AiProfileImportPromptVersion locator = new AiProfileImportPromptVersion();
        locator.setPromptVersionId(promptVersionId);
        locator.setTemplateId(templateId);
        return locator;
    }

    private void stubLockedDraftForUpdate(Long operatorId, Integer draftVersion) {
        authenticate(operatorId, "Editor Admin");
        AiProfileImportPromptTemplate template =
                template(11L, "full_profile", 91L, 101L, 4, 0);
        AiProfileImportPromptVersion draft =
                governedVersion(101L, 11L, 4, "draft", draftVersion);
        draft.setContentSha256("old-hash");
        when(versionMapper.selectById(101L)).thenReturn(versionLocator(11L, 101L));
        when(templateMapper.selectByIdForUpdate(11L)).thenReturn(template);
        when(versionMapper.selectOwnedForUpdate(11L, 101L)).thenReturn(draft);
    }

    private void assertInvalidUpdateMetadata(String versionLabel, String changeSummary) {
        BizException error = assertThrows(
                BizException.class,
                () -> service.updateDraft(
                        73L,
                        101L,
                        updateReq(
                                7,
                                versionLabel,
                                "new-system",
                                "new-repair",
                                changeSummary)));

        assertPromptInvalid(error);
        if (versionLabel != null && !versionLabel.isBlank()) {
            assertFalse(error.getMessage().contains(versionLabel));
        }
        if (changeSummary != null && !changeSummary.isBlank()) {
            assertFalse(error.getMessage().contains(changeSummary));
        }
        verifyNoInteractions(
                adminAuthContext, templateMapper, versionMapper, auditMapper, renderer);
    }

    private void assertAcceptedUpdateMetadata(String versionLabel, String changeSummary) {
        stubLockedDraftForUpdate(73L, 7);
        when(renderer.contentSha256(any(), any())).thenReturn("new-hash");
        when(versionMapper.updateDraftIfExpected(any(), eq(7))).thenReturn(1);
        when(auditMapper.insertAudit(any())).thenReturn(1);
        AiProfileImportPromptVersion fresh =
                governedVersion(101L, 11L, 4, "draft", 8);
        fresh.setVersionLabel(versionLabel);
        fresh.setChangeSummary(changeSummary);
        fresh.setContentSha256("new-hash");
        fresh.setTestStatus("stale");
        when(versionMapper.selectOwnedDetail(11L, 101L)).thenReturn(fresh);

        ProfileImportPromptVersionDetailRespDTO result = service.updateDraft(
                73L,
                101L,
                updateReq(
                        7,
                        versionLabel,
                        "new-system",
                        "new-repair",
                        changeSummary));

        ArgumentCaptor<AiProfileImportPromptVersion> updateCaptor =
                ArgumentCaptor.forClass(AiProfileImportPromptVersion.class);
        verify(versionMapper).updateDraftIfExpected(updateCaptor.capture(), eq(7));
        assertEquals(versionLabel, updateCaptor.getValue().getVersionLabel());
        assertEquals(changeSummary, updateCaptor.getValue().getChangeSummary());
        assertEquals(versionLabel, result.getVersionLabel());
        assertEquals(changeSummary, result.getChangeSummary());
    }

    private void stubLockedDraftForAbandon(
            Long operatorId, Integer templateVersion, Integer draftVersion) {
        authenticate(operatorId, "Release Admin");
        AiProfileImportPromptTemplate template =
                template(11L, "full_profile", 91L, 101L, templateVersion, 0);
        AiProfileImportPromptVersion active =
                governedVersion(91L, 11L, 3, "released", 9);
        AiProfileImportPromptVersion draft =
                governedVersion(101L, 11L, 4, "draft", draftVersion);
        draft.setContentSha256("draft-hash");
        when(versionMapper.selectById(101L)).thenReturn(versionLocator(11L, 101L));
        when(templateMapper.selectByIdForUpdate(11L)).thenReturn(template);
        when(versionMapper.selectOwned(11L, 91L)).thenReturn(active);
        when(versionMapper.selectOwnedForUpdate(11L, 101L)).thenReturn(draft);
    }

    private static void assertDraftAudit(
            AiProfileImportPromptAudit audit,
            String actionCode,
            String reasonCode,
            Long templateId,
            Long promptVersionId,
            Long fromVersionId,
            Long toVersionId,
            String contentSha256,
            Long operatorId,
            String operatorName) {
        assertEquals(actionCode, audit.getActionCode());
        assertEquals(reasonCode, audit.getReasonCode());
        assertEquals(templateId, audit.getTemplateId());
        assertEquals(promptVersionId, audit.getPromptVersionId());
        assertEquals(fromVersionId, audit.getFromVersionId());
        assertEquals(toVersionId, audit.getToVersionId());
        assertEquals(contentSha256, audit.getContentSha256());
        assertEquals(operatorId, audit.getOperatorId());
        assertEquals(operatorName, audit.getOperatorName());
        assertEquals("success", audit.getResultStatus());
        assertNull(audit.getErrorCode());
        assertNull(audit.getMessage());
    }

    private static void assertStateConflict(BizException error) {
        assertEquals(
                ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_STATE_CONFLICT.code(),
                error.getCode());
        assertEquals(
                ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_STATE_CONFLICT.message(),
                error.getMessage());
    }

    private static void assertVersionConflict(BizException error) {
        assertEquals(
                ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_VERSION_CONFLICT.code(),
                error.getCode());
        assertEquals(
                ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_VERSION_CONFLICT.message(),
                error.getMessage());
    }

    private static void assertRejectedReasons(
            Function<String, ProfileImportPromptReasonCode> validator, String... invalidValues) {
        for (String invalidValue : invalidValues) {
            BizException error = assertThrows(
                    BizException.class, () -> validator.apply(invalidValue));
            assertPromptInvalid(error);
            if (invalidValue != null && !invalidValue.isBlank()) {
                assertFalse(error.getMessage().contains(invalidValue));
            }
        }
    }

    private static void assertPromptInvalid(BizException error) {
        assertEquals(46019, error.getCode());
        assertEquals(INVALID_MESSAGE, error.getMessage());
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        return ProfileImportPromptManagementService.class.getDeclaredMethod(name, parameterTypes);
    }

    private static void assertListReturn(Method method, Class<?> elementType) {
        assertEquals(List.class, method.getReturnType());
        ParameterizedType genericReturn =
                assertInstanceOf(ParameterizedType.class, method.getGenericReturnType());
        assertEquals(List.class, genericReturn.getRawType());
        assertArrayEquals(new Type[] {elementType}, genericReturn.getActualTypeArguments());
    }

    private static void assertMethod(
            Method method, Class<?> returnType, Class<?>... parameterTypes) {
        assertEquals(returnType, method.getReturnType());
        assertArrayEquals(parameterTypes, method.getParameterTypes());
    }

    private static void assertDeclaredFieldTypes(
            Class<?> type, Map<String, Class<?>> expected) {
        assertEquals(expected, declaredInstanceFieldTypes(type), type.getSimpleName());
    }

    private static Map<String, Class<?>> declaredInstanceFieldTypes(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !field.isSynthetic())
                .collect(Collectors.toMap(
                        Field::getName,
                        Field::getType,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private static Set<String> allInstanceFieldNames(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> current = type;
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            names.addAll(declaredInstanceFieldTypes(current).keySet());
        }
        return names;
    }

    private static String allInstanceFieldValues(Object value) throws IllegalAccessException {
        List<String> values = new ArrayList<>();
        for (Class<?> current = value.getClass();
                current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                values.add(String.valueOf(field.get(value)));
            }
        }
        return String.join("|", values);
    }

    private static Map<String, Class<?>> fields(Object... namesAndTypes) {
        Map<String, Class<?>> fields = new LinkedHashMap<>();
        for (int index = 0; index < namesAndTypes.length; index += 2) {
            fields.put((String) namesAndTypes[index], (Class<?>) namesAndTypes[index + 1]);
        }
        return fields;
    }
}
