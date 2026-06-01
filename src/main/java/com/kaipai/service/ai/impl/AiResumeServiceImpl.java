package com.kaipai.service.ai.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.actor.dto.ActorPhotoCategoriesDTO;
import com.kaipai.model.actor.dto.ActorProfileDTO;
import com.kaipai.model.actor.dto.ActorProfileSaveDTO;
import com.kaipai.model.actor.dto.ActorWorkExperienceDTO;
import com.kaipai.model.ai.dto.ActorAiQuotaRespDTO;
import com.kaipai.model.ai.dto.AiResumeErrorCode;
import com.kaipai.model.ai.dto.AiResumeFailureRecordDTO;
import com.kaipai.model.ai.dto.AiResumeHistoryItemDTO;
import com.kaipai.model.ai.dto.AiResumePolishReqDTO;
import com.kaipai.model.ai.dto.AiResumePolishRespDTO;
import com.kaipai.model.ai.dto.AiResumeRollbackReqDTO;
import com.kaipai.model.ai.dto.AiResumeRollbackRespDTO;
import com.kaipai.service.actor.ActorProfileService;
import com.kaipai.service.ai.adapter.RuleBasedResumePatchAdapter;
import com.kaipai.service.ai.AiResumeFailureRecordService;
import com.kaipai.service.ai.AiQuotaService;
import com.kaipai.service.ai.AiResumeService;
import lombok.Data;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AiResumeServiceImpl implements AiResumeService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final TypeReference<List<AiResumeHistoryItemDTO>> HISTORY_LIST_TYPE = new TypeReference<>() {};
    private static final long DRAFT_EXPIRE_DAYS = 7L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiQuotaService aiQuotaService;
    private final ActorProfileService actorProfileService;
    private final RuleBasedResumePatchAdapter ruleBasedResumePatchAdapter;
    private final AiResumeFailureRecordService aiResumeFailureRecordService;

    public AiResumeServiceImpl(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               AiQuotaService aiQuotaService,
                               @Lazy ActorProfileService actorProfileService,
                               RuleBasedResumePatchAdapter ruleBasedResumePatchAdapter,
                               AiResumeFailureRecordService aiResumeFailureRecordService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.aiQuotaService = aiQuotaService;
        this.actorProfileService = actorProfileService;
        this.ruleBasedResumePatchAdapter = ruleBasedResumePatchAdapter;
        this.aiResumeFailureRecordService = aiResumeFailureRecordService;
    }

    @Override
    public AiResumePolishRespDTO polishResume(Long userId, AiResumePolishReqDTO dto) {
        ActorProfileDTO profile = actorProfileService.mine(userId);
        if (!Boolean.TRUE.equals(profile.getIsCertified())) {
            throw new BizException(AiResumeErrorCode.NOT_CERTIFIED, "完成实名认证后才可使用 AI 简历润色");
        }

        ActorAiQuotaRespDTO quota = aiQuotaService.quota(userId, "resume_polish");
        if (quota.getTotalQuota() == null || quota.getTotalQuota() <= 0 || quota.getUsedCount() >= quota.getTotalQuota()) {
            throw new BizException(AiResumeErrorCode.QUOTA_EXHAUSTED, "本月 AI 润色次数已用完，邀请好友升级可获得更多次数");
        }

        String requestId = "airp_req_" + UUID.randomUUID().toString().replace("-", "");
        String conversationId = dto != null && StringUtils.hasText(dto.getConversationId())
                ? dto.getConversationId().trim()
                : "airp_conv_" + UUID.randomUUID().toString().replace("-", "");
        try {
            RuleBasedResumePatchAdapter.AdaptedResult adapted = ruleBasedResumePatchAdapter.adapt(dto);

            AiResumePolishRespDTO response = new AiResumePolishRespDTO();
            response.setRequestId(requestId);
            response.setConversationId(conversationId);
            response.setDraftId("airp_draft_" + UUID.randomUUID().toString().replace("-", ""));
            response.setReply(adapted.reply());
            response.setPatches(new ArrayList<>(safeList(adapted.patches())));
            response.setWarnings(new ArrayList<>(safeList(adapted.warnings())));

            DraftRecord draft = buildDraftRecord(userId, dto, response);
            saveDraft(draft);
            try {
                response.setQuota(aiQuotaService.consumeResumePolishQuota(userId));
            } catch (RuntimeException error) {
                deleteDraft(userId, response.getDraftId());
                throw error;
            }
            return response;
        } catch (RuntimeException error) {
            recordFailureSafely(userId, requestId, conversationId, dto, error);
            throw error;
        }
    }

    @Override
    public PageResult<AiResumeHistoryItemDTO> history(Long userId, int page, int size) {
        List<AiResumeHistoryItemDTO> histories = loadHistories(userId);
        if (histories.isEmpty()) {
            return PageResult.empty();
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int start = Math.max((safePage - 1) * safeSize, 0);
        if (start >= histories.size()) {
            return new PageResult<>(histories.size(), Collections.emptyList());
        }
        int end = Math.min(start + safeSize, histories.size());
        return new PageResult<>(histories.size(), new ArrayList<>(histories.subList(start, end)));
    }

    @Override
    public AiResumeRollbackRespDTO rollback(Long userId, String historyId, AiResumeRollbackReqDTO dto) {
        List<AiResumeHistoryItemDTO> histories = loadHistories(userId);
        AiResumeHistoryItemDTO target = histories.stream()
                .filter(item -> Objects.equals(item.getHistoryId(), historyId))
                .findFirst()
                .orElseThrow(() -> new BizException(AiResumeErrorCode.HISTORY_NOT_FOUND, "AI 历史不存在"));
        if (!"applied".equals(target.getStatus())) {
            throw new BizException(AiResumeErrorCode.ROLLBACK_CONFLICT, "当前 AI 历史状态不支持回滚");
        }

        ActorProfileDTO currentProfile = actorProfileService.mine(userId);
        ActorProfileSaveDTO saveDTO = toSaveDTO(currentProfile);
        for (AiResumeHistoryItemDTO.FieldSnapshotDTO snapshot : safeList(target.getBeforeSnapshot())) {
            applySnapshot(saveDTO, snapshot);
        }
        actorProfileService.saveProfile(userId, saveDTO);

        String rolledBackAt = now();
        target.setStatus("rolled_back");
        target.setRolledBackAt(rolledBackAt);
        saveHistories(userId, histories);

        AiResumeRollbackRespDTO response = new AiResumeRollbackRespDTO();
        response.setHistoryId(target.getHistoryId());
        response.setRollbackId("airp_rb_" + UUID.randomUUID().toString().replace("-", ""));
        response.setRestoredSnapshots(new ArrayList<>(safeList(target.getBeforeSnapshot())));
        response.setProfileVersion(StringUtils.hasText(dto == null ? null : dto.getProfileVersion()) ? dto.getProfileVersion().trim() : "profile_v" + System.currentTimeMillis());
        response.setRolledBackAt(rolledBackAt);
        return response;
    }

    DraftRecord readDraft(Long userId, String draftId) {
        String raw = redisTemplate.opsForValue().get(AiResumeRedisKeys.draftKey(userId, draftId));
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, DraftRecord.class);
        } catch (Exception error) {
            throw new BizException(AiResumeErrorCode.RESPONSE_UNPARSABLE, "AI 草稿读取失败");
        }
    }

    void deleteDraft(Long userId, String draftId) {
        redisTemplate.delete(AiResumeRedisKeys.draftKey(userId, draftId));
    }

    List<AiResumeHistoryItemDTO> loadHistories(Long userId) {
        String raw = redisTemplate.opsForValue().get(AiResumeRedisKeys.historyKey(userId));
        if (!StringUtils.hasText(raw)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(raw, HISTORY_LIST_TYPE));
        } catch (Exception error) {
            throw new BizException(AiResumeErrorCode.RESPONSE_UNPARSABLE, "AI 历史读取失败");
        }
    }

    void saveHistories(Long userId, List<AiResumeHistoryItemDTO> histories) {
        try {
            redisTemplate.opsForValue().set(AiResumeRedisKeys.historyKey(userId), objectMapper.writeValueAsString(histories));
        } catch (Exception error) {
            throw new BizException(AiResumeErrorCode.RESPONSE_UNPARSABLE, "AI 历史写入失败");
        }
    }

    private DraftRecord buildDraftRecord(Long userId, AiResumePolishReqDTO request, AiResumePolishRespDTO response) {
        DraftRecord draft = new DraftRecord();
        draft.setUserId(userId);
        draft.setDraftId(response.getDraftId());
        draft.setRequestId(response.getRequestId());
        draft.setConversationId(response.getConversationId());
        draft.setInstruction(request.getInstruction());
        draft.setReply(response.getReply());
        draft.setPatches(new ArrayList<>(safeList(response.getPatches())));
        draft.setWarnings(new ArrayList<>(safeList(response.getWarnings())));
        draft.setProfileVersion(request.getProfileVersion());
        draft.setCreatedAt(now());
        return draft;
    }

    private void saveDraft(DraftRecord draft) {
        try {
            redisTemplate.opsForValue().set(
                    AiResumeRedisKeys.draftKey(draft.getUserId(), draft.getDraftId()),
                    objectMapper.writeValueAsString(draft),
                    DRAFT_EXPIRE_DAYS,
                    TimeUnit.DAYS
            );
        } catch (Exception error) {
            throw new BizException(AiResumeErrorCode.RESPONSE_UNPARSABLE, "AI 草稿写入失败");
        }
    }

    private void recordFailureSafely(Long userId,
                                     String requestId,
                                     String conversationId,
                                     AiResumePolishReqDTO dto,
                                     RuntimeException error) {
        try {
            AiResumeFailureRecordDTO record = buildFailureRecord(userId, requestId, conversationId, dto, error);
            if (record == null) {
                return;
            }
            aiResumeFailureRecordService.recordFailure(record);
        } catch (RuntimeException ignored) {
            // never override the primary AI failure with governance recording failures
        }
    }

    private AiResumeFailureRecordDTO buildFailureRecord(Long userId,
                                                        String requestId,
                                                        String conversationId,
                                                        AiResumePolishReqDTO dto,
                                                        RuntimeException error) {
        Integer errorCode = resolveErrorCode(error);
        if (errorCode == null || errorCode == AiResumeErrorCode.NOT_CERTIFIED || errorCode == AiResumeErrorCode.QUOTA_EXHAUSTED) {
            return null;
        }
        AiResumeFailureRecordDTO record = new AiResumeFailureRecordDTO();
        record.setFailureId("airp_fail_" + UUID.randomUUID().toString().replace("-", ""));
        record.setUserId(userId);
        record.setRequestId(requestId);
        record.setConversationId(conversationId);
        record.setInstruction(dto == null ? null : dto.getInstruction());
        record.setErrorCode(errorCode);
        record.setErrorMessage(error == null ? null : error.getMessage());
        record.setFailureType(resolveFailureType(errorCode));
        record.setHitKeyword(errorCode == AiResumeErrorCode.CONTENT_BLOCKED ? ruleBasedResumePatchAdapter.detectBlockedKeyword(dto == null ? null : dto.getInstruction()) : null);
        record.setHandlingStatus("pending");
        record.setHandlingNotes(Collections.emptyList());
        record.setCreatedAt(now());
        return record;
    }

    private Integer resolveErrorCode(RuntimeException error) {
        if (error instanceof BizException bizException) {
            return bizException.getCode();
        }
        return AiResumeErrorCode.RESPONSE_UNPARSABLE;
    }

    private String resolveFailureType(Integer errorCode) {
        if (errorCode == null) {
            return "failed";
        }
        if (errorCode == AiResumeErrorCode.CONTENT_BLOCKED) {
            return "content_blocked";
        }
        if (errorCode == AiResumeErrorCode.MODEL_TIMEOUT) {
            return "model_timeout";
        }
        if (errorCode == AiResumeErrorCode.RESPONSE_UNPARSABLE) {
            return "response_unparsable";
        }
        if (errorCode == AiResumeErrorCode.CONTEXT_INVALID) {
            return "context_invalid";
        }
        return "failed";
    }

    private ActorProfileSaveDTO toSaveDTO(ActorProfileDTO profile) {
        ActorProfileSaveDTO dto = new ActorProfileSaveDTO();
        dto.setName(profile.getName());
        dto.setGender(profile.getGender());
        dto.setAge(profile.getAge());
        dto.setHeight(profile.getHeight());
        dto.setWeight(profile.getWeight());
        dto.setCity(profile.getCity());
        dto.setBirthday(profile.getBirthday());
        dto.setBirthHour(profile.getBirthHour());
        dto.setAvatar(profile.getAvatar());
        dto.setIntro(profile.getIntro());
        dto.setPhotos(new ArrayList<>(safeList(profile.getPhotos())));
        ActorPhotoCategoriesDTO categories = new ActorPhotoCategoriesDTO();
        if (profile.getPhotoCategories() != null) {
            categories.setPortrait(new ArrayList<>(safeList(profile.getPhotoCategories().getPortrait())));
            categories.setLifestyle(new ArrayList<>(safeList(profile.getPhotoCategories().getLifestyle())));
            categories.setProduction(new ArrayList<>(safeList(profile.getPhotoCategories().getProduction())));
        }
        dto.setPhotoCategories(categories);
        dto.setVideoUrl(profile.getVideoUrl());
        dto.setSkillTypes(new ArrayList<>(safeList(profile.getSkillTypes())));
        dto.setWorkExperiences(safeList(profile.getWorkExperiences()).stream().map(this::copyExperience).toList());
        dto.setBodyType(profile.getBodyType());
        dto.setHairStyle(profile.getHairStyle());
        dto.setLanguages(new ArrayList<>(safeList(profile.getLanguages())));
        dto.setContactPhone(profile.getContactPhone());
        return dto;
    }

    private ActorWorkExperienceDTO copyExperience(ActorWorkExperienceDTO source) {
        ActorWorkExperienceDTO dto = new ActorWorkExperienceDTO();
        dto.setId(source.getId());
        dto.setProjectName(source.getProjectName());
        dto.setRoleName(source.getRoleName());
        dto.setShootDate(source.getShootDate());
        dto.setDescription(source.getDescription());
        dto.setPhotos(new ArrayList<>(safeList(source.getPhotos())));
        return dto;
    }

    private void applySnapshot(ActorProfileSaveDTO dto, AiResumeHistoryItemDTO.FieldSnapshotDTO snapshot) {
        if (!StringUtils.hasText(snapshot.getFieldKey())) {
            return;
        }
        if ("intro".equals(snapshot.getFieldKey())) {
            dto.setIntro(snapshot.getValue());
            return;
        }
        if (snapshot.getFieldKey().startsWith("work_experience:") && snapshot.getFieldKey().endsWith(":description")) {
            String targetId = snapshot.getFieldKey().substring("work_experience:".length(), snapshot.getFieldKey().length() - ":description".length());
            ActorWorkExperienceDTO target = safeList(dto.getWorkExperiences()).stream()
                    .filter(item -> item.getId() != null && Objects.equals(String.valueOf(item.getId()), targetId))
                    .findFirst()
                    .orElseThrow(() -> new BizException(AiResumeErrorCode.ROLLBACK_CONFLICT, "拍摄经历已变化，请刷新后重试回滚"));
            target.setDescription(snapshot.getValue());
        }
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }

    @Data
    static class DraftRecord {
        private Long userId;
        private String draftId;
        private String requestId;
        private String conversationId;
        private String instruction;
        private String reply;
        private List<AiResumePolishRespDTO.PatchDTO> patches = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private String profileVersion;
        private String createdAt;
    }
}
