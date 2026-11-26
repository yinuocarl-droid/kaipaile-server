package com.kaipai.service.actor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardDecorationMapper;
import com.kaipai.model.actor.card.dto.ActorCardDecorationDTO.*;
import com.kaipai.model.actor.card.entity.ActorCardDecorationDraft;
import com.kaipai.model.actor.card.entity.ActorCardDecorationVersion;
import com.kaipai.service.actor.support.ActorCardDecorationSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

/** Draft and immutable decoration publication lifecycle. */
@Service
@RequiredArgsConstructor
public class ActorCardDecorationService {
    private final ActorCardDecorationMapper mapper;
    private final ObjectMapper json;
    private final AdminAuthContext auth;
    private final AdminOperationLogger audit;

    private static final int MAX_BYTES = 262144;
    private static final String STARTER = """
        {"schemaVersion":1,"modules":[
          {"id":"identity","type":"identity","order":0,"layout":{"zone":"hero","x":48,"y":180,"width":654,"height":260},"style":{"backgroundColor":"transparent","color":"#FFFFFF","fontSize":48,"lineHeight":64}},
          {"id":"highlights","type":"highlights","order":10,"layout":{"zone":"hero","x":48,"y":1120,"width":654,"height":150}},
          {"id":"profile","type":"profile","order":20},
          {"id":"introduction","type":"introduction","order":30},
          {"id":"works","type":"works","order":40},
          {"id":"photos","type":"photos","order":50},
          {"id":"stills","type":"stills","order":60},
          {"id":"video","type":"video","order":70},
          {"id":"pdf","type":"pdf","order":80},
          {"id":"cooperation","type":"cooperation","order":90}]}
        """;

    /** Read without initializing storage or changing the active publication. */
    public DraftResp load(Long id) {
        requireTemplate(id);
        var head = storage(() -> mapper.draft(id));
        return view(id, head);
    }

    /** Validate a transient editor preview without persisting any changes. */
    public JsonNode preview(Long id, JsonNode config) {
        requireTemplate(id);
        validateSize(config);
        return ActorCardDecorationSchema.normalize(config);
    }

    /** Save sparse authoring configuration under a revision check. */
    @Transactional(rollbackFor = Exception.class)
    public DraftResp save(Long id, SaveReq req) {
        requireTemplate(id);
        requireRevision(req == null ? null : req.expectedRevision());
        preview(id, req.config());
        Long admin = requireAdmin();
        storage(() -> mapper.initialize(id, STARTER, admin));
        var head = lock(id, req.expectedRevision());
        long before = head.getRevision();
        requireWrite(storage(() -> mapper.saveDraft(id, req.expectedRevision(), write(req.config()), admin)));
        log(id, "edit", before, null, null, null);
        return load(id);
    }

    /** Insert a normalized immutable snapshot and atomically advance the publication head. */
    @Transactional(rollbackFor = Exception.class)
    public DraftResp publish(Long id, ChangeReq req) {
        requireTemplate(id);
        requireChange(req);
        Long admin = requireAdmin();
        var head = lock(id, req.expectedRevision());
        var config = ActorCardDecorationSchema.normalize(read(head.getSchemaJson()));
        String snapshot = write(config);
        String checksum = checksum(snapshot);
        if (head.getActiveVersionNo() != null) {
            var active = storage(() -> mapper.version(id, head.getActiveVersionNo()));
            if (active == null) throw new BizException(409, "装修发布头损坏，请核查版本记录");
            if (checksum.equals(active.getChecksum())) return view(id, head);
        }
        if (head.getLastVersionNo() == Integer.MAX_VALUE) throw new BizException(409, "装修版本编号已达上限");
        int next = head.getLastVersionNo() + 1;
        var version = new ActorCardDecorationVersion();
        version.setTemplateId(id); version.setVersionNo(next); version.setSchemaJson(snapshot);
        version.setChecksum(checksum); version.setPublishedBy(admin); version.setPublishNote(req.note().trim());
        requireWrite(storage(() -> mapper.insertVersion(version)));
        requireWrite(storage(() -> mapper.publish(id, req.expectedRevision(), next, admin)));
        log(id, "publish", req.expectedRevision(), next, checksum, req.note().trim());
        return load(id);
    }

    /** Roll the publication head back or forward; leave draft and old snapshots untouched. */
    @Transactional(rollbackFor = Exception.class)
    public DraftResp activate(Long id, int versionNo, ChangeReq req) {
        requireTemplate(id);
        requireChange(req);
        Long admin = requireAdmin();
        var target = version(id, versionNo);
        var head = lock(id, req.expectedRevision());
        if (Integer.valueOf(versionNo).equals(head.getActiveVersionNo())) return view(id, head);
        requireWrite(storage(() -> mapper.activate(id, req.expectedRevision(), versionNo, admin)));
        log(id, "rollback", req.expectedRevision(), versionNo, target.checksum(), req.note().trim());
        return load(id);
    }

    /** Read an immutable snapshot scoped to its owning template. */
    public VersionResp version(Long id, int versionNo) {
        requireTemplate(id);
        var v = storage(() -> mapper.version(id, versionNo));
        if (v == null) throw new BizException(404, "装修版本不存在");
        return new VersionResp(v.getId(), id, v.getVersionNo(), v.getChecksum(), v.getPublishNote(),
                v.getPublishedBy(), v.getPublishedAt(), ActorCardDecorationSchema.normalize(read(v.getSchemaJson())));
    }

    /** Return the latest 50 publication summaries without their configuration payloads. */
    public List<VersionItem> versions(Long id) {
        requireTemplate(id);
        return storage(() -> mapper.versions(id)).stream().map(v -> new VersionItem(v.getId(), v.getVersionNo(),
                v.getChecksum(), v.getPublishNote(), v.getPublishedBy(), v.getPublishedAt())).toList();
    }

    private DraftResp view(Long id, ActorCardDecorationDraft head) {
        JsonNode config = read(head == null ? STARTER : head.getSchemaJson());
        return new DraftResp(id, head == null ? 0 : head.getRevision(),
                head == null ? null : head.getActiveVersionNo(), config, ActorCardDecorationSchema.normalize(config));
    }

    private ActorCardDecorationDraft lock(Long id, long expected) {
        var head = storage(() -> mapper.lockDraft(id));
        if (head == null || head.getRevision() != expected || head.getRevision() == Long.MAX_VALUE) {
            throw new BizException(409, "装修已由其他操作修改，或尚未保存；请重新读取后再操作");
        }
        return head;
    }

    private void requireTemplate(Long id) {
        if (id == null || id <= 0 || storage(() -> mapper.templateExists(id)) == 0) {
            throw new BizException(404, "模板不存在");
        }
    }

    private Long requireAdmin() {
        Long id = auth.getCurrentAdminUserId();
        if (id == null || id <= 0) throw new BizException(401, "请使用管理员身份操作");
        return id;
    }

    private void requireRevision(Long value) {
        if (value == null || value < 0) throw new BizException("expectedRevision不能为空或小于0");
    }

    private void requireChange(ChangeReq req) {
        requireRevision(req == null ? null : req.expectedRevision());
        if (req.note() == null || req.note().isBlank() || req.note().length() > 200) {
            throw new BizException("请填写不超过200字的操作说明");
        }
    }

    private void requireWrite(int count) {
        if (count != 1) throw new BizException(409, "装修写入冲突，请重新读取后再操作");
    }

    private void validateSize(JsonNode config) {
        if (config == null || write(config).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BizException("装修配置为空或超过256 KiB");
        }
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception ex) { throw new BizException("装修存储内容无法解析，请核查数据"); }
    }

    private String write(JsonNode value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new BizException("装修配置无法序列化"); }
    }

    private String checksum(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    private <T> T storage(Supplier<T> operation) {
        try { return operation.get(); }
        catch (BadSqlGrammarException ex) { throw new BizException(503, "装修存储尚未就绪，请检查装修版本迁移；未保存任何成功结果"); }
    }

    private void log(Long id, String operation, long before, Integer version, String checksum, String note) {
        var context = new LinkedHashMap<String, Object>();
        context.put("revisionBefore", before); context.put("templateId", id);
        context.put("versionNo", version); context.put("checksum", checksum); context.put("note", note);
        audit.logRequired(AdminOperationLogCommand.builder().moduleCode("content").operationCode(operation)
                .targetType("actor_card_decoration").targetId(id).extraContext(context).operationResult(1).build());
    }
}
