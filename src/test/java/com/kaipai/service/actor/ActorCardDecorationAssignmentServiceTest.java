package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardDecorationAssignmentMapper;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.BindingSaveReq;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.UpgradeReq;
import com.kaipai.model.actor.card.entity.ActorCardDecorationAssignment;
import com.kaipai.model.actor.card.entity.ActorCardDecorationBinding;
import com.kaipai.model.actor.card.entity.ActorCardDecorationVersion;
import java.sql.SQLSyntaxErrorException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;

class ActorCardDecorationAssignmentServiceTest {
    private ActorCardDecorationAssignmentMapper mapper;
    private AdminAuthContext auth;
    private AdminOperationLogger audit;
    private ActorCardDecorationAssignmentService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ActorCardDecorationAssignmentMapper.class);
        auth = mock(AdminAuthContext.class);
        audit = mock(AdminOperationLogger.class);
        when(auth.getCurrentAdminUserId()).thenReturn(8L);
        when(mapper.lockOwnedCard(anyLong(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        service = new ActorCardDecorationAssignmentService(mapper, new ObjectMapper(), auth, audit);
    }

    @Test
    void sameModeAndBackgroundKeepsTheLockedVersionAfterBindingChanges() {
        when(mapper.countOwnedCard(7L, 11L)).thenReturn(1);
        when(mapper.assignmentForUpdate(11L)).thenReturn(assignment(11L, "person_ai", null, 101L, 1L, 1, 4L));
        when(mapper.publishedVersion(101L)).thenReturn(version(101L, 1L, 1, 16));

        var selected = service.selectForMode(7L, 11L, "person_ai", null);

        assertEquals(101L, selected.versionId());
        assertEquals(16, selected.config().at("/defaults/radius").asInt());
        verify(mapper, never()).bindingForUpdate(any(), any());
        verify(mapper, never()).updateAssignmentSource(any(), anyLong(), any(), any(), any(), any(), anyInt(), anyLong());
    }

    @Test
    void sameBackgroundKeepsItsHistoricalLockEvenIfTheLibraryItemWasLaterDisabled() {
        when(mapper.countOwnedCard(7L, 11L)).thenReturn(1);
        when(mapper.assignmentForUpdate(11L)).thenReturn(
                assignment(11L, "person_background_ai", 55L, 101L, 1L, 1, 4L));
        when(mapper.publishedVersion(101L)).thenReturn(version(101L, 1L, 1, 16));

        var selected = service.selectForMode(7L, 11L, "person_background_ai", 55L);

        assertEquals(101L, selected.versionId());
        verify(mapper, never()).countEnabledBackground(55L);
        verify(mapper, never()).bindingForUpdate(any(), any());
    }

    @Test
    void explicitModeAndBackgroundChangeSelectsTheCurrentRealBackgroundBinding() {
        when(mapper.countOwnedCard(7L, 11L)).thenReturn(1);
        when(mapper.countEnabledBackground(55L)).thenReturn(1);
        when(mapper.assignmentForUpdate(11L)).thenReturn(assignment(11L, "person_ai", null, 101L, 1L, 1, 4L));
        when(mapper.bindingForUpdate("background", 55L)).thenReturn(binding("background", 55L, 2L, 202L, 2L, 3, 5L));
        when(mapper.publishedVersion(101L)).thenReturn(version(101L, 1L, 1, 16));
        when(mapper.publishedVersion(202L)).thenReturn(version(202L, 2L, 3, 40));
        when(mapper.updateAssignmentSource(eq(11L), eq(4L), eq("person_background_ai"), eq(55L),
                eq(2L), eq(202L), eq(3), eq(7L))).thenReturn(1);
        when(mapper.insertAssignmentAudit(any())).thenReturn(1);

        var selected = service.selectForMode(7L, 11L, "person_background_ai", 55L);

        assertEquals(202L, selected.versionId());
        assertEquals(40, selected.config().at("/defaults/radius").asInt());
        verify(mapper).insertAssignmentAudit(argThat(row -> "select_mode".equals(row.getOperation())
                && Long.valueOf(101L).equals(row.getFromVersionId())
                && Long.valueOf(202L).equals(row.getToVersionId())));
    }

    @Test
    void defaultAndBackgroundBindingsRequireOwnedPublishedVersionsAndCasRevision() {
        when(mapper.publishedVersion(202L)).thenReturn(version(202L, 2L, 3, 40));
        when(mapper.bindingForUpdate("default", 0L)).thenReturn(null);
        when(mapper.insertBinding(any())).thenReturn(1);

        var saved = service.saveDefaultBinding(new BindingSaveReq(0L, 2L, 202L, "切换默认"));

        assertEquals(1L, saved.revision());
        assertEquals(202L, saved.versionId());
        verify(audit).logRequired(any());

        when(mapper.countEnabledBackground(55L)).thenReturn(1);
        when(mapper.publishedVersion(303L)).thenReturn(version(303L, 9L, 1, 8));
        BizException wrongOwner = assertThrows(BizException.class,
                () -> service.saveBackgroundBinding(55L, new BindingSaveReq(0L, 2L, 303L, "错误模板")));
        assertEquals(409, wrongOwner.getCode());
    }

    @Test
    void oldCardWithoutAssignmentIsLegacyAndMissingReadTableOnlyAlsoDegradesToLegacy() {
        when(mapper.assignment(11L)).thenReturn(null);
        assertNull(service.resolve(11L));

        SQLSyntaxErrorException missing = new SQLSyntaxErrorException(
                "Table 'test.actor_card_decoration_assignment' doesn't exist", "42S02", 1146);
        when(mapper.assignment(12L)).thenThrow(new BadSqlGrammarException("select", "select", missing));
        assertNull(service.resolve(12L));

        when(mapper.assignment(13L)).thenThrow(new BadSqlGrammarException(
                "syntax", "bad sql", new SQLSyntaxErrorException("syntax error", "42000", 1064)));
        assertThrows(BadSqlGrammarException.class, () -> service.resolve(13L));
    }

    @Test
    void readResolveSafelyFallsBackForAnUnsupportedSchemaVersion() {
        when(mapper.assignment(11L)).thenReturn(
                assignment(11L, "person_ai", null, 101L, 1L, 1, 4L));
        when(mapper.publishedVersion(101L)).thenReturn(versionWithSchema(
                101L, 1L, 1,
                "{\"schemaVersion\":3,\"modules\":[{\"id\":\"photos\",\"type\":\"photos\"}]}"));

        assertNull(service.resolve(11L));
    }

    @Test void readResolveConsumesV2ButDoesNotHideMalformedSupportedV2() {
        when(mapper.assignment(11L)).thenReturn(assignment(11L,"person_ai",null,101L,1L,1,4L));
        when(mapper.publishedVersion(101L)).thenReturn(versionWithSchema(101L,1L,1,
                "{\"schemaVersion\":2,\"modules\":[{\"id\":\"photos\",\"type\":\"photos\"}]}"));
        assertEquals(2,service.resolve(11L).schemaVersion());
        when(mapper.publishedVersion(101L)).thenReturn(versionWithSchema(101L,1L,1,
                "{\"schemaVersion\":2,\"minRendererVersion\":2,\"modules\":[]}"));
        assertThrows(BizException.class, () -> service.resolve(11L));
    }

    @Test
    void readResolveSafelyFallsBackForAnUnsupportedMinimumRendererVersion() {
        when(mapper.assignment(11L)).thenReturn(
                assignment(11L, "person_ai", null, 101L, 1L, 1, 4L));
        when(mapper.publishedVersion(101L)).thenReturn(versionWithSchema(
                101L, 1L, 1,
                "{\"schemaVersion\":1,\"minRendererVersion\":3,"
                        + "\"modules\":[{\"id\":\"photos\",\"type\":\"photos\"}]}"));

        assertNull(service.resolve(11L));
    }

    @Test
    void readResolveDoesNotHideMalformedSupportedSchemaAndWritePathsRemainFailClosed() {
        ActorCardDecorationAssignment locked =
                assignment(11L, "person_ai", null, 101L, 1L, 1, 4L);
        ActorCardDecorationVersion malformed = versionWithSchema(
                101L, 1L, 1, "{\"schemaVersion\":1,\"modules\":[]}");
        when(mapper.assignment(11L)).thenReturn(locked);
        when(mapper.publishedVersion(101L)).thenReturn(malformed);

        assertThrows(BizException.class, () -> service.resolve(11L));

        when(mapper.countOwnedCard(7L, 11L)).thenReturn(1);
        when(mapper.assignmentForUpdate(11L)).thenReturn(locked);
        assertThrows(BizException.class,
                () -> service.selectForMode(7L, 11L, "person_ai", null));
    }

    @Test
    void upgradeUsesPreviewedVersionAsCasAndRejectsAStaleConfirmation() {
        ActorCardDecorationAssignment current = assignment(11L, "person_ai", null, 101L, 1L, 1, 7L);
        ActorCardDecorationBinding target = binding("default", 0L, 3L, 202L, 2L, 3, 8L);
        when(mapper.countOwnedCard(7L, 11L)).thenReturn(1);
        when(mapper.assignment(11L)).thenReturn(current);
        when(mapper.binding("default", 0L)).thenReturn(target);
        when(mapper.publishedVersion(101L)).thenReturn(version(101L, 1L, 1, 16));
        when(mapper.publishedVersion(202L)).thenReturn(version(202L, 2L, 3, 40));

        var preview = service.previewUpgrade(7L, 11L);
        assertEquals(101L, preview.expectedVersionId());
        assertEquals(202L, preview.target().versionId());
        assertTrue(preview.upgradeAvailable());

        when(mapper.assignmentForUpdate(11L)).thenReturn(current);
        when(mapper.bindingForUpdate("default", 0L)).thenReturn(target);
        when(mapper.updateAssignmentVersion(11L, 101L, 2L, 202L, 3, 7L)).thenReturn(0);
        BizException stale = assertThrows(BizException.class,
                () -> service.confirmUpgrade(7L, 11L, new UpgradeReq(101L, 202L)));
        assertEquals(409, stale.getCode());
        verify(mapper, never()).insertAssignmentAudit(any());
    }

    @Test
    void copyPreservesTheExactLockedVersionInsteadOfSelectingLatestBinding() {
        when(mapper.countOwnedCard(7L, 11L)).thenReturn(1);
        when(mapper.countOwnedCard(7L, 12L)).thenReturn(1);
        when(mapper.assignment(11L)).thenReturn(assignment(11L, "person_background_ai", 55L, 101L, 1L, 1, 4L));
        when(mapper.publishedVersion(101L)).thenReturn(version(101L, 1L, 1, 16));
        when(mapper.insertAssignment(any())).thenReturn(1);
        when(mapper.insertAssignmentAudit(any())).thenReturn(1);

        service.copyAssignment(7L, 11L, 12L);

        verify(mapper).insertAssignment(argThat(row -> Long.valueOf(12L).equals(row.getCardId())
                && Long.valueOf(101L).equals(row.getVersionId())
                && Long.valueOf(55L).equals(row.getBackgroundId())));
        verify(mapper, never()).binding(any(), any());
    }

    @Test
    void backgroundReadinessKeepsUnboundItemsAndAcceptsSupportedV1AndV2OncePerVersion() throws Exception {
        ObjectMapper countingJson = spy(new ObjectMapper());
        service = new ActorCardDecorationAssignmentService(mapper, countingJson, auth, audit);
        ActorCardDecorationBinding v1 = binding("background", 14L, 1L, 101L, 1L, 1, 8L);
        ActorCardDecorationBinding v2a = binding("background", 15L, 1L, 202L, 2L, 2, 8L);
        ActorCardDecorationBinding v2b = binding("background", 16L, 1L, 202L, 2L, 2, 8L);
        String schemaV1 = "{\"schemaVersion\":1,\"modules\":[{\"id\":\"photos\",\"type\":\"photos\"}]}";
        String schemaV2 = "{\"schemaVersion\":2,\"modules\":[{\"id\":\"photos\",\"type\":\"photos\"}]}";
        when(mapper.backgroundBindings(List.of(13L, 14L, 15L, 16L)))
                .thenReturn(List.of(v1, v2a, v2b));
        when(mapper.publishedVersions(List.of(101L, 202L)))
                .thenReturn(List.of(
                        versionWithSchema(101L, 1L, 1, schemaV1),
                        versionWithSchema(202L, 2L, 2, schemaV2)));

        var readiness = service.backgroundReadiness(List.of(13L, 14L, 15L, 16L));

        assertFalse(readiness.get(13L).ready());
        assertEquals("当前背景尚未绑定已发布装修版本", readiness.get(13L).unavailableReason());
        assertTrue(readiness.get(14L).ready());
        assertNull(readiness.get(14L).unavailableReason());
        assertTrue(readiness.get(15L).ready());
        assertTrue(readiness.get(16L).ready());
        verify(countingJson, times(1)).readTree(schemaV1);
        verify(countingJson, times(1)).readTree(schemaV2);
    }

    @Test
    void backgroundReadinessRejectsBrokenReferencesAndVersionsWhoseTemplateWasDeleted() {
        ActorCardDecorationBinding damaged = binding("background", 31L, 1L, 301L, 3L, 7, 8L);
        ActorCardDecorationBinding deletedTemplate = binding("background", 32L, 1L, 302L, 4L, 1, 8L);
        when(mapper.backgroundBindings(List.of(31L, 32L)))
                .thenReturn(List.of(damaged, deletedTemplate));
        when(mapper.publishedVersions(List.of(301L, 302L)))
                .thenReturn(List.of(version(301L, 9L, 1, 16)));

        var readiness = service.backgroundReadiness(List.of(31L, 32L));

        assertFalse(readiness.get(31L).ready());
        assertEquals("当前背景绑定的装修版本不可用", readiness.get(31L).unavailableReason());
        assertFalse(readiness.get(32L).ready());
        assertEquals("当前背景绑定的装修版本不可用", readiness.get(32L).unavailableReason());
    }

    @Test
    void backgroundReadinessDegradesOnlyForMissingDecorationStorageAndPropagatesOtherSqlErrors() {
        SQLSyntaxErrorException missing = new SQLSyntaxErrorException(
                "Table 'test.actor_card_decoration_binding' doesn't exist", "42S02", 1146);
        when(mapper.backgroundBindings(List.of(41L, 42L)))
                .thenThrow(new BadSqlGrammarException("select", "select", missing));

        var readiness = service.backgroundReadiness(List.of(41L, 42L));

        assertEquals(List.of(41L, 42L), readiness.keySet().stream().toList());
        assertTrue(readiness.values().stream().noneMatch(ActorCardDecorationAssignmentService.DecorationReadiness::ready));
        assertTrue(readiness.values().stream().allMatch(
                item -> "装修绑定存储尚未就绪".equals(item.unavailableReason())));

        reset(mapper);
        when(mapper.backgroundBindings(List.of(43L))).thenThrow(new BadSqlGrammarException(
                "syntax", "bad sql", new SQLSyntaxErrorException("syntax error", "42000", 1064)));
        assertThrows(BadSqlGrammarException.class, () -> service.backgroundReadiness(List.of(43L)));
    }

    private ActorCardDecorationVersion version(Long id, Long templateId, int versionNo, int radius) {
        return versionWithSchema(id, templateId, versionNo,
                "{\"schemaVersion\":1,\"defaults\":{\"radius\":" + radius
                        + "},\"modules\":[{\"id\":\"photos\",\"type\":\"photos\"}]}");
    }

    private ActorCardDecorationVersion versionWithSchema(
            Long id, Long templateId, int versionNo, String schemaJson) {
        ActorCardDecorationVersion row = new ActorCardDecorationVersion();
        row.setId(id);
        row.setTemplateId(templateId);
        row.setVersionNo(versionNo);
        row.setSchemaJson(schemaJson);
        return row;
    }

    private ActorCardDecorationBinding binding(String type, Long sourceId, long revision,
                                                Long versionId, Long templateId, int templateVersion,
                                                Long updatedBy) {
        ActorCardDecorationBinding row = new ActorCardDecorationBinding();
        row.setSourceType(type);
        row.setSourceId(sourceId);
        row.setRevision(revision);
        row.setVersionId(versionId);
        row.setTemplateId(templateId);
        row.setTemplateVersion(templateVersion);
        row.setUpdatedBy(updatedBy);
        return row;
    }

    private ActorCardDecorationAssignment assignment(Long cardId, String mode, Long backgroundId,
                                                      Long versionId, Long templateId, int templateVersion,
                                                      long revision) {
        ActorCardDecorationAssignment row = new ActorCardDecorationAssignment();
        row.setCardId(cardId);
        row.setGenerationMode(mode);
        row.setBackgroundId(backgroundId);
        row.setVersionId(versionId);
        row.setTemplateId(templateId);
        row.setTemplateVersion(templateVersion);
        row.setRevision(revision);
        return row;
    }
}
