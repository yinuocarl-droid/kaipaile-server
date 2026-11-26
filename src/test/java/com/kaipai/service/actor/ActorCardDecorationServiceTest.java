package com.kaipai.service.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardDecorationMapper;
import com.kaipai.model.actor.card.dto.ActorCardDecorationDTO.*;
import com.kaipai.model.actor.card.entity.ActorCardDecorationDraft;
import com.kaipai.model.actor.card.entity.ActorCardDecorationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActorCardDecorationServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MemoryMapper store = new MemoryMapper();
    private final AdminAuthContext auth = mock(AdminAuthContext.class);
    private final AdminOperationLogger audit = mock(AdminOperationLogger.class);
    private ActorCardDecorationService service;

    @BeforeEach void setup() {
        when(auth.getCurrentAdminUserId()).thenReturn(8L);
        service = new ActorCardDecorationService(store, json, auth, audit);
    }

    @Test void unsavedTemplateHasAnEditableStarterWithoutPersistingIt() {
        var draft = assertDoesNotThrow(() -> service.load(7L));
        assertEquals(0, draft.revision());
        assertNull(draft.activeVersion());
        assertTrue(draft.config().path("modules").size() >= 7);
        assertEquals(0, store.heads.size());
    }

    @Test void savePreservesSparseOverridesAndReturnsEffectivePreview() {
        var draft = assertDoesNotThrow(() -> service.save(7L, new SaveReq(0L, config(32))));
        assertEquals(1, draft.revision());
        assertFalse(draft.config().at("/modules/0").has("style"));
        assertEquals(32, draft.preview().at("/modules/0/style/radius").asInt());
        assertEquals(draft.config(), service.load(7L).config());
    }

    @Test void staleRevisionCannotOverwriteAnotherEditorsSavedDraft() {
        assertDoesNotThrow(() -> service.save(7L, new SaveReq(0L, config(32))));
        var ex = assertThrows(BizException.class, () -> service.save(7L, new SaveReq(0L, config(0))));
        assertEquals(409, ex.getCode());
        assertEquals(32, service.load(7L).config().at("/defaults/radius").asInt());
    }

    @Test void publicationIsImmutableAfterDraftEditsAndHasContentChecksum() {
        var saved = assertDoesNotThrow(() -> service.save(7L, new SaveReq(0L, config(16))));
        var published = service.publish(7L, new ChangeReq(saved.revision(), "首版"));
        assertEquals(1, published.activeVersion());
        service.save(7L, new SaveReq(published.revision(), config(0)));
        var v1 = service.version(7L, 1);
        assertEquals(16, v1.config().at("/modules/0/style/radius").asInt());
        assertEquals(64, v1.checksum().length());
        assertEquals(0, service.load(7L).config().at("/defaults/radius").asInt());
    }

    @Test void equivalentPublicationDoesNotCreateAnotherSnapshot() {
        var saved = assertDoesNotThrow(() -> service.save(7L, new SaveReq(0L, config(16))));
        var first = service.publish(7L, new ChangeReq(saved.revision(), "首版"));
        var again = service.publish(7L, new ChangeReq(first.revision(), "重复点击"));
        assertEquals(1, again.activeVersion());
        assertEquals(1, store.snapshots.size());
        assertEquals(first.revision(), again.revision());
    }

    @Test void rollbackChangesHeadOnlyAndVersionNumbersNeverGoBackwards() {
        var a = assertDoesNotThrow(() -> service.save(7L, new SaveReq(0L, config(16))));
        var b = service.publish(7L, new ChangeReq(a.revision(), "v1"));
        var c = service.save(7L, new SaveReq(b.revision(), config(32)));
        var d = service.publish(7L, new ChangeReq(c.revision(), "v2"));
        var back = service.activate(7L, 1, new ChangeReq(d.revision(), "回滚"));
        assertEquals(1, back.activeVersion());
        assertEquals(32, back.config().at("/defaults/radius").asInt());
        var next = service.publish(7L, new ChangeReq(back.revision(), "重新发布"));
        assertEquals(3, next.activeVersion());
        assertEquals(16, service.version(7L, 1).config().at("/defaults/radius").asInt());
    }

    @Test void snapshotsCannotBeReadOrActivatedFromAnotherTemplate() {
        var a = assertDoesNotThrow(() -> service.save(7L, new SaveReq(0L, config(16))));
        service.publish(7L, new ChangeReq(a.revision(), "v1"));
        assertEquals(404, assertThrows(BizException.class, () -> service.version(9L, 1)).getCode());
        assertEquals(404, assertThrows(BizException.class,
                () -> service.activate(9L, 1, new ChangeReq(0L, "错误目标"))).getCode());
    }

    @Test void invalidSchemaAndUnknownTemplateAreRejectedBeforeAnyDraftIsWritten() {
        assertEquals(404, assertThrows(BizException.class, () -> service.load(999L)).getCode());
        var invalid = config(200);
        assertThrows(BizException.class, () -> service.save(7L, new SaveReq(0L, invalid)));
        assertTrue(store.heads.isEmpty());
    }

    @Test void missingMigrationIsExplicitNotAnEmptySuccessfulDraft() {
        var unavailable = mock(ActorCardDecorationMapper.class);
        when(unavailable.templateExists(7L)).thenReturn(1);
        when(unavailable.draft(7L)).thenThrow(new BadSqlGrammarException("test", "", new java.sql.SQLException()));
        var s = new ActorCardDecorationService(unavailable, json, auth, audit);
        assertEquals(503, assertThrows(BizException.class, () -> s.load(7L)).getCode());
    }

    @Test void zeroAffectedRowsCannotBeReportedAsSavedOrPublished() {
        assertDoesNotThrow(() -> service.save(7L, new SaveReq(0L, config(16))));
        store.rejectWrites = true;
        assertEquals(409, assertThrows(BizException.class,
                () -> service.save(7L, new SaveReq(1L, config(32)))).getCode());
        assertEquals(409, assertThrows(BizException.class,
                () -> service.publish(7L, new ChangeReq(1L, "竞争"))).getCode());
    }

    private ObjectNode config(int radius) {
        var node = json.createObjectNode().put("schemaVersion", 1);
        node.putObject("defaults").put("radius", radius);
        node.putArray("modules").addObject().put("id", "photos").put("type", "photos");
        return node;
    }

    // Tests replace external SQL only. They exercise the real service and schema against observable stored values.
    private static class MemoryMapper implements ActorCardDecorationMapper {
        final Map<Long, ActorCardDecorationDraft> heads = new HashMap<>();
        final Map<String, ActorCardDecorationVersion> snapshots = new HashMap<>();
        boolean rejectWrites;
        public int templateExists(Long id) { return Set.of(7L, 9L).contains(id) ? 1 : 0; }
        public ActorCardDecorationDraft draft(Long id) { return heads.get(id); }
        public ActorCardDecorationDraft lockDraft(Long id) { return draft(id); }
        public int initialize(Long id, String raw, Long admin) {
            heads.computeIfAbsent(id, ignored -> {
                var head = new ActorCardDecorationDraft(); head.setTemplateId(id); head.setRevision(0L);
                head.setSchemaJson(raw); head.setLastVersionNo(0); return head;
            }); return 1;
        }
        public int saveDraft(Long id, long revision, String raw, Long admin) {
            if (rejectWrites || heads.get(id).getRevision() != revision) return 0;
            heads.get(id).setSchemaJson(raw); heads.get(id).setRevision(revision + 1); return 1;
        }
        public int insertVersion(ActorCardDecorationVersion v) {
            v.setId((long) snapshots.size() + 1);
            snapshots.put(v.getTemplateId() + ":" + v.getVersionNo(), v); return 1;
        }
        public int publish(Long id, long revision, int version, Long admin) {
            if (rejectWrites || heads.get(id).getRevision() != revision) return 0;
            heads.get(id).setLastVersionNo(version); return activate(id, revision, version, admin);
        }
        public int activate(Long id, long revision, int version, Long admin) {
            if (rejectWrites || heads.get(id).getRevision() != revision) return 0;
            heads.get(id).setActiveVersionNo(version); heads.get(id).setRevision(revision + 1); return 1;
        }
        public ActorCardDecorationVersion version(Long id, int v) { return snapshots.get(id + ":" + v); }
        public List<ActorCardDecorationVersion> versions(Long id) {
            return snapshots.values().stream().filter(v -> id.equals(v.getTemplateId())).toList();
        }
    }
}
