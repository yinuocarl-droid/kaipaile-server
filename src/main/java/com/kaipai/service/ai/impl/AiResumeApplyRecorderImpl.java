package com.kaipai.service.ai.impl;

import com.kaipai.common.exception.BizException;
import com.kaipai.model.actor.dto.ActorProfileDTO;
import com.kaipai.model.actor.dto.ActorProfileSaveDTO;
import com.kaipai.model.actor.dto.ActorWorkExperienceDTO;
import com.kaipai.model.ai.dto.AiResumeApplyMetaDTO;
import com.kaipai.model.ai.dto.AiResumeErrorCode;
import com.kaipai.model.ai.dto.AiResumeHistoryItemDTO;
import com.kaipai.model.ai.dto.AiResumePolishRespDTO;
import com.kaipai.service.ai.AiResumeApplyRecorder;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AiResumeApplyRecorderImpl implements AiResumeApplyRecorder {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_HISTORY_COUNT = 20;

    private final AiResumeServiceImpl aiResumeService;

    public AiResumeApplyRecorderImpl(@Lazy AiResumeServiceImpl aiResumeService) {
        this.aiResumeService = aiResumeService;
    }

    @Override
    public void recordAppliedDraft(Long userId, ActorProfileDTO beforeProfile, ActorProfileSaveDTO saveDTO) {
        AiResumeApplyMetaDTO applyMeta = saveDTO.getAiResumeApplyMeta();
        if (applyMeta == null || !StringUtils.hasText(applyMeta.getDraftId()) || applyMeta.getAppliedPatchIds() == null || applyMeta.getAppliedPatchIds().isEmpty()) {
            return;
        }

        AiResumeServiceImpl.DraftRecord draft = aiResumeService.readDraft(userId, applyMeta.getDraftId());
        if (draft == null) {
            throw new BizException(AiResumeErrorCode.HISTORY_NOT_FOUND, "AI 草稿不存在或已失效，请重新生成");
        }

        List<AiResumePolishRespDTO.PatchDTO> selectedPatches = draft.getPatches().stream()
                .filter(item -> applyMeta.getAppliedPatchIds().contains(item.getPatchId()))
                .map(this::copyPatchAsApplied)
                .toList();
        if (selectedPatches.isEmpty()) {
            throw new BizException(AiResumeErrorCode.PROFILE_STALE, "当前保存未匹配到已应用的 AI patch");
        }

        AiResumeHistoryItemDTO historyItem = new AiResumeHistoryItemDTO();
        historyItem.setHistoryId("airp_hist_" + UUID.randomUUID().toString().replace("-", ""));
        historyItem.setDraftId(draft.getDraftId());
        historyItem.setRequestId(draft.getRequestId());
        historyItem.setConversationId(draft.getConversationId());
        historyItem.setInstruction(draft.getInstruction());
        historyItem.setReply(draft.getReply());
        historyItem.setStatus("applied");
        historyItem.setPatches(new ArrayList<>(selectedPatches));
        historyItem.setBeforeSnapshot(buildBeforeSnapshot(beforeProfile, selectedPatches));
        historyItem.setAfterSnapshot(buildAfterSnapshot(saveDTO, selectedPatches));
        historyItem.setCreatedAt(draft.getCreatedAt());
        historyItem.setAppliedAt(now());
        historyItem.setRolledBackAt(null);

        List<AiResumeHistoryItemDTO> histories = aiResumeService.loadHistories(userId);
        histories.removeIf(item -> Objects.equals(item.getDraftId(), historyItem.getDraftId()) && Objects.equals(item.getRequestId(), historyItem.getRequestId()));
        histories.add(0, historyItem);
        if (histories.size() > MAX_HISTORY_COUNT) {
            histories = new ArrayList<>(histories.subList(0, MAX_HISTORY_COUNT));
        }
        aiResumeService.saveHistories(userId, histories);
        aiResumeService.deleteDraft(userId, draft.getDraftId());
    }

    private AiResumePolishRespDTO.PatchDTO copyPatchAsApplied(AiResumePolishRespDTO.PatchDTO source) {
        AiResumePolishRespDTO.PatchDTO patch = new AiResumePolishRespDTO.PatchDTO();
        patch.setPatchId(source.getPatchId());
        patch.setFieldType(source.getFieldType());
        patch.setFieldKey(source.getFieldKey());
        patch.setLabel(source.getLabel());
        patch.setTargetId(source.getTargetId());
        patch.setBeforeValue(source.getBeforeValue());
        patch.setAfterValue(source.getAfterValue());
        patch.setReason(source.getReason());
        patch.setStatus("applied");
        return patch;
    }

    private List<AiResumeHistoryItemDTO.FieldSnapshotDTO> buildBeforeSnapshot(ActorProfileDTO beforeProfile,
                                                                              List<AiResumePolishRespDTO.PatchDTO> patches) {
        List<AiResumeHistoryItemDTO.FieldSnapshotDTO> snapshots = new ArrayList<>();
        for (AiResumePolishRespDTO.PatchDTO patch : patches) {
            snapshots.add(snapshotOf(patch.getFieldKey(), resolveFieldValue(beforeProfile, patch)));
        }
        return snapshots;
    }

    private List<AiResumeHistoryItemDTO.FieldSnapshotDTO> buildAfterSnapshot(ActorProfileSaveDTO saveDTO,
                                                                             List<AiResumePolishRespDTO.PatchDTO> patches) {
        List<AiResumeHistoryItemDTO.FieldSnapshotDTO> snapshots = new ArrayList<>();
        for (AiResumePolishRespDTO.PatchDTO patch : patches) {
            snapshots.add(snapshotOf(patch.getFieldKey(), resolveFieldValue(saveDTO, patch)));
        }
        return snapshots;
    }

    private AiResumeHistoryItemDTO.FieldSnapshotDTO snapshotOf(String fieldKey, String value) {
        AiResumeHistoryItemDTO.FieldSnapshotDTO snapshot = new AiResumeHistoryItemDTO.FieldSnapshotDTO();
        snapshot.setFieldKey(fieldKey);
        snapshot.setValue(value);
        return snapshot;
    }

    private String resolveFieldValue(ActorProfileDTO profile, AiResumePolishRespDTO.PatchDTO patch) {
        if ("intro".equals(patch.getFieldType())) {
            return profile.getIntro();
        }
        if ("work_experience_description".equals(patch.getFieldType())) {
            String targetId = patch.getTargetId();
            ActorWorkExperienceDTO experience = safeList(profile.getWorkExperiences()).stream()
                    .filter(item -> item.getId() != null && Objects.equals(String.valueOf(item.getId()), targetId))
                    .findFirst()
                    .orElseThrow(() -> new BizException(AiResumeErrorCode.PROFILE_STALE, "拍摄经历已变化，请重新生成 AI patch"));
            return experience.getDescription();
        }
        throw new BizException(AiResumeErrorCode.CONTEXT_INVALID, "不支持的 AI 字段类型");
    }

    private String resolveFieldValue(ActorProfileSaveDTO saveDTO, AiResumePolishRespDTO.PatchDTO patch) {
        if ("intro".equals(patch.getFieldType())) {
            return saveDTO.getIntro();
        }
        if ("work_experience_description".equals(patch.getFieldType())) {
            String targetId = patch.getTargetId();
            ActorWorkExperienceDTO experience = safeList(saveDTO.getWorkExperiences()).stream()
                    .filter(item -> item.getId() != null && Objects.equals(String.valueOf(item.getId()), targetId))
                    .findFirst()
                    .orElseThrow(() -> new BizException(AiResumeErrorCode.PROFILE_STALE, "AI patch 与当前档案不匹配，请重新生成"));
            return experience.getDescription();
        }
        throw new BizException(AiResumeErrorCode.CONTEXT_INVALID, "不支持的 AI 字段类型");
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }
}
