package com.kaipai.service.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardDecorationAssignmentMapper;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.BindingResp;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.BindingSaveReq;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.DecorationEnvelope;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.UpgradePreviewResp;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.UpgradeReq;
import com.kaipai.model.actor.card.entity.ActorCardDecorationAssignment;
import com.kaipai.model.actor.card.entity.ActorCardDecorationAssignmentAudit;
import com.kaipai.model.actor.card.entity.ActorCardDecorationBinding;
import com.kaipai.model.actor.card.entity.ActorCardDecorationVersion;
import com.kaipai.service.actor.support.ActorCardDecorationSchema;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates platform binding and per-card locked decoration selection. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActorCardDecorationAssignmentService {
    public static final String PERSON_AI = "person_ai";
    public static final String PERSON_BACKGROUND_AI = "person_background_ai";
    private static final String DEFAULT = "default";
    private static final String BACKGROUND = "background";
    private static final String BACKGROUND_UNBOUND = "当前背景尚未绑定已发布装修版本";
    private static final String BACKGROUND_VERSION_UNAVAILABLE = "当前背景绑定的装修版本不可用";
    private static final String DECORATION_STORAGE_UNAVAILABLE = "装修绑定存储尚未就绪";

    private final ActorCardDecorationAssignmentMapper mapper;
    private final ObjectMapper json;
    private final AdminAuthContext auth;
    private final AdminOperationLogger audit;

    /**
     * Batch-resolves whether background sources can use the same published-version contract as selectForMode.
     * This read path never locks, writes assignments, or invokes an AI provider.
     */
    public Map<Long, DecorationReadiness> backgroundReadiness(List<Long> backgroundIds) {
        LinkedHashMap<Long, DecorationReadiness> result = new LinkedHashMap<>();
        if (backgroundIds == null || backgroundIds.isEmpty()) return Collections.unmodifiableMap(result);
        backgroundIds.stream().filter(Objects::nonNull).filter(id -> id > 0)
                .forEach(id -> result.putIfAbsent(id, unavailable(BACKGROUND_UNBOUND)));
        if (result.isEmpty()) return Collections.unmodifiableMap(result);
        List<Long> sourceIds = result.keySet().stream().toList();
        try {
            Map<Long, ActorCardDecorationBinding> bindings = new LinkedHashMap<>();
            LinkedHashSet<Long> versionIds = new LinkedHashSet<>();
            for (ActorCardDecorationBinding binding : mapper.backgroundBindings(sourceIds)) {
                if (binding == null || binding.getSourceId() == null) continue;
                bindings.put(binding.getSourceId(), binding);
                if (binding.getVersionId() != null) versionIds.add(binding.getVersionId());
            }
            if (versionIds.isEmpty()) return Collections.unmodifiableMap(result);

            Map<Long, ActorCardDecorationVersion> versions = new LinkedHashMap<>();
            for (ActorCardDecorationVersion version : mapper.publishedVersions(List.copyOf(versionIds))) {
                if (version != null && version.getId() != null) versions.put(version.getId(), version);
            }
            Map<Long, Boolean> parsedVersions = new LinkedHashMap<>();
            result.replaceAll((backgroundId, ignored) -> {
                ActorCardDecorationBinding binding = bindings.get(backgroundId);
                if (binding == null) return unavailable(BACKGROUND_UNBOUND);
                ActorCardDecorationVersion version = versions.get(binding.getVersionId());
                try {
                    requireBoundVersionReference(binding, version);
                } catch (BizException ex) {
                    return unavailable(BACKGROUND_VERSION_UNAVAILABLE);
                }
                boolean usable = parsedVersions.computeIfAbsent(version.getId(), key -> isUsableVersion(version));
                return usable ? new DecorationReadiness(true, null)
                        : unavailable(BACKGROUND_VERSION_UNAVAILABLE);
            });
            return Collections.unmodifiableMap(result);
        } catch (RuntimeException ex) {
            if (!isMissingDecorationTable(ex)) throw ex;
            result.replaceAll((backgroundId, ignored) -> unavailable(DECORATION_STORAGE_UNAVAILABLE));
            return Collections.unmodifiableMap(result);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public DecorationEnvelope selectForMode(Long userId, Long cardId, String generationMode, Long backgroundId) {
        try {
            requireOwnedForUpdate(userId, cardId);
            Source source = requireSource(generationMode, backgroundId);
            ActorCardDecorationAssignment current = mapper.assignmentForUpdate(cardId);
            ActorCardDecorationVersion currentVersion = current == null ? null : requireAssignedVersion(current);
            if (sameSource(current, source)) {
                return envelope(currentVersion);
            }
            requireEnabledBackground(source);
            ActorCardDecorationBinding binding = mapper.bindingForUpdate(source.type(), source.id());
            if (binding == null) {
                if (current == null && DEFAULT.equals(source.type())) return null;
                throw new BizException(409, "当前生成来源尚未绑定已发布装修版本");
            }
            ActorCardDecorationVersion version = requireBoundVersion(binding);
            if (current == null) {
                ActorCardDecorationAssignment created = assignment(cardId, source, binding, userId);
                requireWrite(mapper.insertAssignment(created), "装修锁版写入冲突，请重试");
                auditAssignment(cardId, userId, "select_mode", source, null, binding.getVersionId());
            } else {
                requireWrite(mapper.updateAssignmentSource(cardId, current.getRevision(), source.mode(), source.backgroundId(),
                        binding.getTemplateId(), binding.getVersionId(), binding.getTemplateVersion(), userId),
                        "装修来源已并发修改，请重新读取");
                auditAssignment(cardId, userId, "select_mode", source, current.getVersionId(), binding.getVersionId());
            }
            return envelope(version);
        } catch (RuntimeException ex) {
            throw writeFailure(ex);
        }
    }

    /** Resolve a locked card reference. Missing assignment storage alone is the explicit legacy read path. */
    public DecorationEnvelope resolve(Long cardId) {
        try {
            ActorCardDecorationAssignment selected = mapper.assignment(cardId);
            return selected == null ? null : envelopeForRead(requireAssignedVersion(selected));
        } catch (RuntimeException ex) {
            if (isMissingDecorationTable(ex)) return null;
            throw ex;
        }
    }

    public BindingResp getDefaultBinding() { return getBinding(DEFAULT, 0L); }

    public BindingResp getBackgroundBinding(Long backgroundId) {
        requirePositive(backgroundId, "backgroundId");
        return getBinding(BACKGROUND, backgroundId);
    }

    @Transactional(rollbackFor = Exception.class)
    public BindingResp saveDefaultBinding(BindingSaveReq request) {
        return saveBinding(DEFAULT, 0L, request);
    }

    @Transactional(rollbackFor = Exception.class)
    public BindingResp saveBackgroundBinding(Long backgroundId, BindingSaveReq request) {
        requirePositive(backgroundId, "backgroundId");
        try {
            if (mapper.countEnabledBackground(backgroundId) != 1) throw new BizException(404, "背景不存在或未启用");
        } catch (RuntimeException ex) {
            throw writeFailure(ex);
        }
        return saveBinding(BACKGROUND, backgroundId, request);
    }

    /** Resolve the currently saved binding for an authorized real-card admin preview without mutating the card. */
    public DecorationEnvelope previewForMode(Long cardId, String generationMode, Long backgroundId) {
        try {
            if (cardId == null || cardId <= 0 || mapper.countCard(cardId) != 1) throw new BizException(404, "演员卡不存在");
            Source source = requireSource(generationMode, backgroundId);
            requireEnabledBackground(source);
            ActorCardDecorationBinding selected = mapper.binding(source.type(), source.id());
            return selected == null ? null : envelope(requireBoundVersion(selected));
        } catch (RuntimeException ex) {
            throw writeFailure(ex);
        }
    }

    public UpgradePreviewResp previewUpgrade(Long userId, Long cardId) {
        try {
            requireOwned(userId, cardId);
            ActorCardDecorationAssignment current = mapper.assignment(cardId);
            Source source = current == null
                    ? new Source(DEFAULT, 0L, PERSON_AI, null)
                    : sourceOf(current);
            ActorCardDecorationBinding binding = mapper.binding(source.type(), source.id());
            DecorationEnvelope currentEnvelope = current == null ? null : envelope(requireAssignedVersion(current));
            DecorationEnvelope target = binding == null ? null : envelope(requireBoundVersion(binding));
            boolean available = target != null && (currentEnvelope == null
                    || !Objects.equals(currentEnvelope.versionId(), target.versionId()));
            return new UpgradePreviewResp(current == null ? null : current.getVersionId(), available,
                    currentEnvelope, target);
        } catch (RuntimeException ex) {
            if (isMissingDecorationTable(ex)) return new UpgradePreviewResp(null, false, null, null);
            throw ex;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public DecorationEnvelope confirmUpgrade(Long userId, Long cardId, UpgradeReq request) {
        try {
            requireOwnedForUpdate(userId, cardId);
            if (request == null || request.targetVersionId() == null || request.targetVersionId() <= 0) {
                throw new BizException("targetVersionId不能为空");
            }
            ActorCardDecorationAssignment current = mapper.assignmentForUpdate(cardId);
            if (current != null) requireAssignedVersion(current);
            Long actual = current == null ? null : current.getVersionId();
            if (!Objects.equals(actual, request.expectedVersionId())) {
                throw new BizException(409, "装修版本已变化，请重新预览后确认");
            }
            Source source = current == null
                    ? new Source(DEFAULT, 0L, PERSON_AI, null)
                    : sourceOf(current);
            ActorCardDecorationBinding binding = mapper.bindingForUpdate(source.type(), source.id());
            if (binding == null || !Objects.equals(binding.getVersionId(), request.targetVersionId())) {
                throw new BizException(409, "目标装修绑定已变化，请重新预览后确认");
            }
            if (Objects.equals(actual, binding.getVersionId())) {
                throw new BizException(409, "当前演员卡已使用目标装修版本，无需重复升级");
            }
            ActorCardDecorationVersion target = requireBoundVersion(binding);
            if (current == null) {
                requireWrite(mapper.insertAssignment(assignment(cardId, source, binding, userId)),
                        "装修版本已变化，请重新预览后确认");
            } else {
                requireWrite(mapper.updateAssignmentVersion(cardId, request.expectedVersionId(), binding.getTemplateId(),
                        binding.getVersionId(), binding.getTemplateVersion(), userId),
                        "装修版本已变化，请重新预览后确认");
            }
            auditAssignment(cardId, userId, "upgrade", source, actual, binding.getVersionId());
            return envelope(target);
        } catch (RuntimeException ex) {
            throw writeFailure(ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void copyAssignment(Long userId, Long sourceCardId, Long targetCardId) {
        try {
            requireOwned(userId, sourceCardId);
            requireOwnedForUpdate(userId, targetCardId);
            ActorCardDecorationAssignment source = mapper.assignment(sourceCardId);
            if (source == null) return;
            requireAssignedVersion(source);
            ActorCardDecorationAssignment copy = new ActorCardDecorationAssignment();
            copy.setCardId(targetCardId);
            copy.setGenerationMode(source.getGenerationMode());
            copy.setBackgroundId(source.getBackgroundId());
            copy.setTemplateId(source.getTemplateId());
            copy.setVersionId(source.getVersionId());
            copy.setTemplateVersion(source.getTemplateVersion());
            copy.setRevision(1L);
            copy.setAssignedBy(userId);
            requireWrite(mapper.insertAssignment(copy), "复制装修锁版失败");
            auditAssignment(targetCardId, userId, "copy", sourceOf(source), null, source.getVersionId());
        } catch (RuntimeException ex) {
            throw writeFailure(ex);
        }
    }

    private BindingResp getBinding(String sourceType, Long sourceId) {
        try {
            ActorCardDecorationBinding row = mapper.binding(sourceType, sourceId);
            return row == null ? new BindingResp(sourceType, sourceId, 0, null, null, null, null) : bindingResponse(row);
        } catch (RuntimeException ex) {
            throw writeFailure(ex);
        }
    }

    private BindingResp saveBinding(String sourceType, Long sourceId, BindingSaveReq request) {
        try {
            requireBindingRequest(request);
            Long adminId = requireAdmin();
            ActorCardDecorationVersion version = requireVersion(request.versionId());
            if (!Objects.equals(version.getTemplateId(), request.templateId())) {
                throw new BizException(409, "装修版本不属于指定模板");
            }
            if (request.expectedRevision() == 0) {
                ActorCardDecorationBinding created = new ActorCardDecorationBinding();
                created.setSourceType(sourceType); created.setSourceId(sourceId); created.setRevision(1L);
                created.setTemplateId(version.getTemplateId()); created.setVersionId(version.getId());
                created.setTemplateVersion(version.getVersionNo()); created.setUpdatedBy(adminId);
                requireWrite(mapper.insertBinding(created), "装修绑定写入冲突，请重新读取");
                logBinding(sourceType, sourceId, 0, created, request.note());
                return bindingResponse(created);
            }
            ActorCardDecorationBinding current = mapper.bindingForUpdate(sourceType, sourceId);
            if (current == null) {
                throw new BizException(409, "装修绑定已变化，请重新读取");
            }
            if (!Objects.equals(current.getRevision(), request.expectedRevision())) {
                throw new BizException(409, "装修绑定已变化，请重新读取");
            }
            requireWrite(mapper.updateBinding(sourceType, sourceId, request.expectedRevision(), version.getTemplateId(),
                    version.getId(), version.getVersionNo(), adminId), "装修绑定写入冲突，请重新读取");
            ActorCardDecorationBinding updated = new ActorCardDecorationBinding();
            updated.setSourceType(sourceType); updated.setSourceId(sourceId);
            updated.setRevision(current.getRevision() + 1); updated.setTemplateId(version.getTemplateId());
            updated.setVersionId(version.getId()); updated.setTemplateVersion(version.getVersionNo());
            updated.setUpdatedBy(adminId);
            logBinding(sourceType, sourceId, current.getRevision(), updated, request.note());
            return bindingResponse(updated);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "装修绑定已变化，请重新读取");
        } catch (RuntimeException ex) {
            throw writeFailure(ex);
        }
    }

    private Source requireSource(String generationMode, Long backgroundId) {
        if (PERSON_AI.equals(generationMode)) {
            if (backgroundId != null && backgroundId != 0) throw new BizException("person_ai不能指定背景ID");
            return new Source(DEFAULT, 0L, PERSON_AI, null);
        }
        if (PERSON_BACKGROUND_AI.equals(generationMode)) {
            requirePositive(backgroundId, "backgroundId");
            return new Source(BACKGROUND, backgroundId, PERSON_BACKGROUND_AI, backgroundId);
        }
        throw new BizException("generationMode只支持person_ai或person_background_ai");
    }

    private void requireEnabledBackground(Source source) {
        if (BACKGROUND.equals(source.type()) && mapper.countEnabledBackground(source.id()) != 1) {
            throw new BizException(404, "背景不存在或未启用");
        }
    }

    private Source sourceOf(ActorCardDecorationAssignment row) {
        if (PERSON_AI.equals(row.getGenerationMode()) && row.getBackgroundId() == null) {
            return new Source(DEFAULT, 0L, PERSON_AI, null);
        }
        if (PERSON_BACKGROUND_AI.equals(row.getGenerationMode())
                && row.getBackgroundId() != null && row.getBackgroundId() > 0) {
            return new Source(BACKGROUND, row.getBackgroundId(), PERSON_BACKGROUND_AI, row.getBackgroundId());
        }
        throw new BizException(409, "演员卡装修来源引用损坏，请核查锁版记录");
    }

    private boolean sameSource(ActorCardDecorationAssignment row, Source source) {
        return row != null && Objects.equals(row.getGenerationMode(), source.mode())
                && Objects.equals(row.getBackgroundId(), source.backgroundId());
    }

    private ActorCardDecorationAssignment assignment(Long cardId, Source source,
                                                     ActorCardDecorationBinding binding, Long userId) {
        ActorCardDecorationAssignment row = new ActorCardDecorationAssignment();
        row.setCardId(cardId); row.setGenerationMode(source.mode()); row.setBackgroundId(source.backgroundId());
        row.setTemplateId(binding.getTemplateId()); row.setVersionId(binding.getVersionId());
        row.setTemplateVersion(binding.getTemplateVersion()); row.setRevision(1L); row.setAssignedBy(userId);
        return row;
    }

    private ActorCardDecorationVersion requireBoundVersion(ActorCardDecorationBinding binding) {
        ActorCardDecorationVersion version = requireVersion(binding.getVersionId());
        return requireBoundVersionReference(binding, version);
    }

    private ActorCardDecorationVersion requireBoundVersionReference(
            ActorCardDecorationBinding binding, ActorCardDecorationVersion version) {
        if (version == null) throw new BizException(404, "已发布装修版本不存在");
        if (!Objects.equals(version.getTemplateId(), binding.getTemplateId())
                || !Objects.equals(version.getVersionNo(), binding.getTemplateVersion())) {
            throw new BizException(409, "装修绑定引用损坏，请管理员重新绑定");
        }
        return version;
    }

    private boolean isUsableVersion(ActorCardDecorationVersion version) {
        try {
            envelope(version);
            return true;
        } catch (BizException ex) {
            return false;
        }
    }

    private DecorationReadiness unavailable(String reason) {
        return new DecorationReadiness(false, reason);
    }

    private ActorCardDecorationVersion requireAssignedVersion(ActorCardDecorationAssignment assignment) {
        ActorCardDecorationVersion version = requireVersion(assignment.getVersionId());
        if (!Objects.equals(version.getTemplateId(), assignment.getTemplateId())
                || !Objects.equals(version.getVersionNo(), assignment.getTemplateVersion())) {
            throw new BizException(409, "演员卡装修版本引用损坏，请核查锁版记录");
        }
        return version;
    }

    private ActorCardDecorationVersion requireVersion(Long versionId) {
        ActorCardDecorationVersion version = versionId == null ? null : mapper.publishedVersion(versionId);
        if (version == null) throw new BizException(404, "已发布装修版本不存在");
        return version;
    }

    private DecorationEnvelope envelope(ActorCardDecorationVersion version) {
        try {
            var config = ActorCardDecorationSchema.normalize(json.readTree(version.getSchemaJson()));
            return new DecorationEnvelope(version.getId(), version.getTemplateId(), version.getVersionNo(),
                    config.path("schemaVersion").asInt(), config);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(409, "已发布装修版本内容损坏");
        }
    }

    private DecorationEnvelope envelopeForRead(ActorCardDecorationVersion version) {
        try {
            return envelope(version);
        } catch (BizException ex) {
            if (!hasUnsupportedRendererContract(version.getSchemaJson())) {
                throw ex;
            }
            log.warn("演员卡装修版本超出当前renderer能力，公开读取已降级legacy: versionId={}, templateId={}, templateVersion={}",
                    version.getId(), version.getTemplateId(), version.getVersionNo());
            return null;
        }
    }

    private boolean hasUnsupportedRendererContract(String schemaJson) {
        try {
            var root = json.readTree(schemaJson);
            if (root == null || !root.isObject()) {
                return false;
            }
            var schemaVersion = root.get("schemaVersion");
            if (schemaVersion != null && schemaVersion.isIntegralNumber()
                    && schemaVersion.asInt() != 1 && schemaVersion.asInt() != 2) {
                return true;
            }
            var minRendererVersion = root.get("minRendererVersion");
            return minRendererVersion != null && minRendererVersion.isIntegralNumber()
                    && minRendererVersion.asInt() > 2;
        } catch (Exception ignored) {
            return false;
        }
    }

    private BindingResp bindingResponse(ActorCardDecorationBinding row) {
        return new BindingResp(row.getSourceType(), row.getSourceId(), row.getRevision(), row.getTemplateId(),
                row.getVersionId(), row.getTemplateVersion(), row.getUpdatedAt());
    }

    private void requireOwned(Long userId, Long cardId) {
        requirePositive(userId, "userId"); requirePositive(cardId, "cardId");
        if (mapper.countOwnedCard(userId, cardId) != 1) throw new BizException(404, "演员卡不存在");
    }

    /**
     * Assignment rows may not exist yet. Locking their stable actor_card parent
     * serializes first creation and prevents two missing-row gap locks from
     * deadlocking when each transaction later inserts the same assignment.
     */
    private void requireOwnedForUpdate(Long userId, Long cardId) {
        requirePositive(userId, "userId"); requirePositive(cardId, "cardId");
        if (!Objects.equals(cardId, mapper.lockOwnedCard(userId, cardId))) {
            throw new BizException(404, "演员卡不存在");
        }
    }

    private void requireBindingRequest(BindingSaveReq request) {
        if (request == null || request.expectedRevision() == null || request.expectedRevision() < 0
                || request.templateId() == null || request.templateId() <= 0
                || request.versionId() == null || request.versionId() <= 0
                || request.note() == null || request.note().isBlank() || request.note().length() > 200) {
            throw new BizException("装修绑定参数不完整或操作说明超过200字");
        }
    }

    private Long requireAdmin() {
        Long adminId = auth.getCurrentAdminUserId();
        if (adminId == null || adminId <= 0) throw new BizException(401, "请使用管理员身份操作");
        return adminId;
    }

    private void requirePositive(Long value, String name) {
        if (value == null || value <= 0) throw new BizException(name + "必须为正整数");
    }

    private void requireWrite(int count, String message) {
        if (count != 1) throw new BizException(409, message);
    }

    private void auditAssignment(Long cardId, Long userId, String operation, Source source,
                                 Long fromVersionId, Long toVersionId) {
        ActorCardDecorationAssignmentAudit row = new ActorCardDecorationAssignmentAudit();
        row.setCardId(cardId); row.setActorUserId(userId); row.setOperation(operation);
        row.setGenerationMode(source.mode()); row.setBackgroundId(source.backgroundId());
        row.setFromVersionId(fromVersionId); row.setToVersionId(toVersionId);
        requireWrite(mapper.insertAssignmentAudit(row), "装修锁版审计写入失败");
    }

    private void logBinding(String sourceType, Long sourceId, long before,
                            ActorCardDecorationBinding binding, String note) {
        var context = new LinkedHashMap<String, Object>();
        context.put("sourceType", sourceType); context.put("sourceId", sourceId);
        context.put("revisionBefore", before); context.put("revisionAfter", binding.getRevision());
        context.put("templateId", binding.getTemplateId()); context.put("versionId", binding.getVersionId());
        context.put("templateVersion", binding.getTemplateVersion()); context.put("note", note.trim());
        audit.logRequired(AdminOperationLogCommand.builder().moduleCode("content")
                .operationCode("bind_decoration").targetType("actor_card_decoration_binding")
                .targetId(sourceId).extraContext(context).operationResult(1).build());
    }

    private RuntimeException writeFailure(RuntimeException ex) {
        if (ex instanceof DuplicateKeyException) {
            return new BizException(409, "装修锁版或绑定已并发修改，请重新读取");
        }
        if (isMissingDecorationTable(ex)) {
            return new BizException(503, "装修绑定存储尚未就绪，请执行V20261022_002；未保存任何成功结果");
        }
        return ex;
    }

    private boolean isMissingDecorationTable(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof SQLException sql && sql.getErrorCode() == 1146) {
                String message = String.valueOf(sql.getMessage());
                return message.contains("actor_card_decoration_assignment")
                        || message.contains("actor_card_decoration_binding")
                        || message.contains("actor_card_decoration_version");
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private record Source(String type, Long id, String mode, Long backgroundId) { }

    /** Read-only availability result exposed to the public background catalog assembler. */
    public record DecorationReadiness(boolean ready, String unavailableReason) { }
}
