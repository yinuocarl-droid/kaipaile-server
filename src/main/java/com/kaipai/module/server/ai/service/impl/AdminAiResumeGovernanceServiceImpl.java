package com.kaipai.module.server.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureActionDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureAssigneeOptionDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureCollaborationCatalogDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureEscalationRoleOptionDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureItemDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureQueryDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeHistoryItemDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeHistoryQueryDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeOverviewDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeQuotaUserDTO;
import com.kaipai.module.model.ai.dto.AiResumeErrorCode;
import com.kaipai.module.model.ai.dto.AiResumeFailureHandlingNoteDTO;
import com.kaipai.module.model.ai.dto.AiResumeFailureRecordDTO;
import com.kaipai.module.model.ai.dto.AiResumeHistoryItemDTO;
import com.kaipai.module.model.level.dto.ActorLevelInfoRespDTO;
import com.kaipai.module.model.system.dto.AdminRoleAiGovernanceMatrixItemDTO;
import com.kaipai.module.model.system.entity.AdminUser;
import com.kaipai.module.model.system.entity.AdminUserRole;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.ai.service.AdminAiResumeGovernanceService;
import com.kaipai.module.server.ai.service.AiResumeFailureRecordService;
import com.kaipai.module.server.membership.service.MembershipAccountService;
import com.kaipai.module.server.system.service.AdminRoleService;
import com.kaipai.module.server.system.service.AdminUserRoleService;
import com.kaipai.module.server.system.service.AdminUserService;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAiResumeGovernanceServiceImpl implements AdminAiResumeGovernanceService {

    private static final TypeReference<List<AiResumeHistoryItemDTO>> HISTORY_LIST_TYPE = new TypeReference<>() {
    };
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int OVERVIEW_TOP_QUOTA_LIMIT = 5;
    private static final int OVERVIEW_RECENT_HISTORY_LIMIT = 5;
    private static final int FAILURE_ASSIGN_ACK_SLA_HOURS = 4;
    private static final int FAILURE_AUTO_REMIND_MAX_COUNT = 2;
    private static final String HISTORY_KEY_PREFIX = "ai:resume_polish:history:";
    private static final Map<String, List<String>> FAILURE_ALLOWED_TRANSITIONS = Map.of(
            "pending", List.of("reviewed", "retry_advised", "escalated", "ignored", "closed"),
            "reviewed", List.of("retry_advised", "escalated", "ignored", "closed"),
            "retry_advised", List.of("reviewed", "escalated", "ignored", "closed"),
            "escalated", List.of("reviewed", "ignored", "closed"),
            "ignored", Collections.emptyList(),
            "closed", Collections.emptyList()
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final MembershipAccountService membershipAccountService;
    private final AiResumeFailureRecordService aiResumeFailureRecordService;
    private final AdminAuthContext adminAuthContext;
    private final AdminOperationLogger adminOperationLogger;
    private final AdminUserService adminUserService;
    private final AdminUserRoleService adminUserRoleService;
    private final AdminRoleService adminRoleService;

    @Override
    public AdminAiResumeOverviewDTO overview() {
        List<HistoryRecord> historyRecords = sortHistoryRecords(loadAllHistoryRecords());
        List<QuotaUsageRecord> quotaUsageRecords = sortQuotaUsageRecords(loadCurrentMonthQuotaUsages());

        AdminAiResumeOverviewDTO dto = new AdminAiResumeOverviewDTO();
        dto.setTotalHistoryCount(historyRecords.size());
        dto.setAppliedHistoryCount(historyRecords.stream().filter(item -> Objects.equals(item.history().getStatus(), "applied")).count());
        dto.setRolledBackHistoryCount(historyRecords.stream().filter(item -> Objects.equals(item.history().getStatus(), "rolled_back")).count());
        dto.setHistoryUserCount((int) historyRecords.stream().map(HistoryRecord::userId).distinct().count());
        dto.setCurrentMonthHistoryCount(historyRecords.stream().filter(this::isCurrentMonthHistory).count());
        dto.setCurrentMonthQuotaUserCount(quotaUsageRecords.size());
        dto.setCurrentMonthQuotaUsageTotal(quotaUsageRecords.stream().mapToLong(QuotaUsageRecord::usedCount).sum());
        dto.setTopQuotaUsers(buildQuotaUserItems(quotaUsageRecords.stream().limit(OVERVIEW_TOP_QUOTA_LIMIT).toList()));
        dto.setRecentHistories(buildHistoryItems(historyRecords.stream().limit(OVERVIEW_RECENT_HISTORY_LIMIT).toList()));
        return dto;
    }

    @Override
    public PageResult<AdminAiResumeHistoryItemDTO> history(AdminAiResumeHistoryQueryDTO query) {
        List<HistoryRecord> records = sortHistoryRecords(loadAllHistoryRecords()).stream()
                .filter(item -> matchesQuery(item, query))
                .toList();
        int safePageNo = Math.max(query.getPageNo(), 1);
        int safePageSize = Math.max(query.getPageSize(), 1);
        int start = Math.max((safePageNo - 1) * safePageSize, 0);
        if (start >= records.size()) {
            return new PageResult<>(records.size(), Collections.emptyList());
        }
        int end = Math.min(start + safePageSize, records.size());
        return new PageResult<>(records.size(), buildHistoryItems(records.subList(start, end)));
    }

    @Override
    public AdminAiResumeHistoryItemDTO historyDetail(String historyId) {
        HistoryRecord record = loadAllHistoryRecords().stream()
                .filter(item -> Objects.equals(item.history().getHistoryId(), historyId))
                .findFirst()
                .orElseThrow(() -> new BizException(AiResumeErrorCode.HISTORY_NOT_FOUND, "AI 历史不存在"));
        return buildHistoryItems(List.of(record)).get(0);
    }

    @Override
    public List<AdminAiResumeFailureItemDTO> failures(AdminAiResumeFailureQueryDTO query) {
        return buildFailureItems(loadFailureRecords(query, false));
    }

    @Override
    public List<AdminAiResumeFailureItemDTO> sensitiveHits(AdminAiResumeFailureQueryDTO query) {
        return buildFailureItems(loadFailureRecords(query, true));
    }

    @Override
    public AdminAiResumeFailureCollaborationCatalogDTO collaborationCatalog() {
        List<AdminAiResumeFailureEscalationRoleOptionDTO> roleOptions = loadEligibleEscalationRoleOptions();
        AdminAiResumeFailureCollaborationCatalogDTO dto = new AdminAiResumeFailureCollaborationCatalogDTO();
        dto.setEscalationRoleOptions(roleOptions);
        dto.setAssigneeOptions(loadEligibleAssigneeOptions(roleOptions));
        return dto;
    }

    @Override
    public AdminAiResumeFailureItemDTO reviewFailure(String failureId, AdminAiResumeFailureActionDTO action) {
        return handleFailure(failureId, "reviewed", action);
    }

    @Override
    public AdminAiResumeFailureItemDTO suggestRetry(String failureId, AdminAiResumeFailureActionDTO action) {
        return handleFailure(failureId, "retry_advised", action);
    }

    @Override
    public AdminAiResumeFailureItemDTO closeFailure(String failureId, AdminAiResumeFailureActionDTO action) {
        return handleFailure(failureId, "closed", action);
    }

    @Override
    public AdminAiResumeFailureItemDTO ignoreFailure(String failureId, AdminAiResumeFailureActionDTO action) {
        return handleFailure(failureId, "ignored", action);
    }

    @Override
    public AdminAiResumeFailureItemDTO escalateFailure(String failureId, AdminAiResumeFailureActionDTO action) {
        return handleFailure(failureId, "escalated", action);
    }

    @Override
    public AdminAiResumeFailureItemDTO assignFailure(String failureId, AdminAiResumeFailureActionDTO action) {
        if (action == null || action.getAssignedAdminId() == null) {
            throw new BizException("请选择分派处理人");
        }
        AiResumeFailureRecordDTO current = aiResumeFailureRecordService.findFailure(failureId);
        if (current == null) {
            throw new BizException(AiResumeErrorCode.HISTORY_NOT_FOUND, "AI 失败样本不存在");
        }
        String currentStatus = normalizeFailureHandlingStatus(current.getHandlingStatus());
        ensureFailureOpenForCollaboration(currentStatus);

        AdminAiResumeFailureAssigneeOptionDTO assignee = requireEligibleAssignee(action.getAssignedAdminId());
        if (Objects.equals(current.getAssignedAdminId(), assignee.getAdminUserId())) {
            throw new BizException("失败样本当前责任人已是目标处理人");
        }
        AiResumeFailureRecordDTO before = copyFailure(current);
        AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();

        current.setHandlingStatus(currentStatus);
        current.setHandlingNote(action.getReason());
        current.setHandledByAdminId(admin.getAdminUserId());
        current.setHandledByAdminName(admin.getUserName());
        String now = LocalDateTime.now().format(TIME_FORMATTER);
        current.setHandledAt(now);
        current.setAssignedAdminId(assignee.getAdminUserId());
        current.setAssignedAdminName(assignee.getUserName());
        current.setAssignedAt(now);
        current.setAssignmentAcknowledgedByAdminId(null);
        current.setAssignmentAcknowledgedByAdminName(null);
        current.setAssignmentAcknowledgedAt(null);
        current.setReminderCount(0);
        current.setLastRemindedByAdminId(null);
        current.setLastRemindedByAdminName(null);
        current.setLastRemindedAt(null);
        clearFailureManualTakeover(current);
        clearFailureAutoRemindSkip(current);
        AdminAiResumeFailureEscalationRoleOptionDTO escalationRole = null;
        if (StringUtils.hasText(action.getEscalationRoleCode())) {
            escalationRole = requireEligibleEscalationRole(action.getEscalationRoleCode());
            current.setEscalationRoleCode(escalationRole.getRoleCode());
            current.setEscalationRoleName(escalationRole.getRoleName());
        }
        current.setHandlingNotes(appendHandlingNote(current, "assign", currentStatus, action.getReason(), admin, assignee, escalationRole));
        aiResumeFailureRecordService.recordFailure(current);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("ai_resume_assign")
                .targetType("ai_resume_failure")
                .beforeSnapshot(before)
                .afterSnapshot(current)
                .extraContext(buildFailureActionContext(current, action.getReason(), currentStatus, currentStatus, "assign"))
                .operationResult(1)
                .build());

        return buildFailureItems(List.of(current)).get(0);
    }

    @Override
    public AdminAiResumeFailureItemDTO acknowledgeAssignment(String failureId, AdminAiResumeFailureActionDTO action) {
        AiResumeFailureRecordDTO current = aiResumeFailureRecordService.findFailure(failureId);
        if (current == null) {
            throw new BizException(AiResumeErrorCode.HISTORY_NOT_FOUND, "AI 失败样本不存在");
        }
        String currentStatus = normalizeFailureHandlingStatus(current.getHandlingStatus());
        ensureFailureOpenForCollaboration(currentStatus);
        if (current.getAssignedAdminId() == null) {
            throw new BizException("失败样本尚未分派处理人");
        }
        if (StringUtils.hasText(current.getAssignmentAcknowledgedAt())) {
            throw new BizException("当前失败样本已确认接手");
        }

        AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();
        if (!Objects.equals(current.getAssignedAdminId(), admin.getAdminUserId())) {
            throw new BizException("仅当前责任人可确认接手");
        }
        AiResumeFailureRecordDTO before = copyFailure(current);
        String now = LocalDateTime.now().format(TIME_FORMATTER);

        current.setHandlingStatus(currentStatus);
        current.setHandlingNote(action == null ? null : action.getReason());
        current.setHandledByAdminId(admin.getAdminUserId());
        current.setHandledByAdminName(admin.getUserName());
        current.setHandledAt(now);
        current.setAssignmentAcknowledgedByAdminId(admin.getAdminUserId());
        current.setAssignmentAcknowledgedByAdminName(admin.getUserName());
        current.setAssignmentAcknowledgedAt(now);
        current.setHandlingNotes(appendHandlingNote(current, "acknowledge", currentStatus,
                action == null ? null : action.getReason(), admin, null, null));
        aiResumeFailureRecordService.recordFailure(current);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("ai_resume_acknowledge")
                .targetType("ai_resume_failure")
                .beforeSnapshot(before)
                .afterSnapshot(current)
                .extraContext(buildFailureActionContext(current,
                        action == null ? null : action.getReason(),
                        currentStatus,
                        currentStatus,
                        "acknowledge"))
                .operationResult(1)
                .build());

        return buildFailureItems(List.of(current)).get(0);
    }

    @Override
    public AdminAiResumeFailureItemDTO remindFailure(String failureId, AdminAiResumeFailureActionDTO action) {
        AiResumeFailureRecordDTO current = aiResumeFailureRecordService.findFailure(failureId);
        if (current == null) {
            throw new BizException(AiResumeErrorCode.HISTORY_NOT_FOUND, "AI 失败样本不存在");
        }
        String currentStatus = normalizeFailureHandlingStatus(current.getHandlingStatus());
        ensureFailureOpenForCollaboration(currentStatus);
        if (current.getAssignedAdminId() == null) {
            throw new BizException("失败样本尚未分派处理人");
        }
        if (StringUtils.hasText(current.getAssignmentAcknowledgedAt())) {
            throw new BizException("当前失败样本已确认接手，无需继续催办");
        }
        AiResumeFailureRecordDTO before = copyFailure(current);
        AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();
        String now = LocalDateTime.now().format(TIME_FORMATTER);

        current.setHandlingStatus(currentStatus);
        current.setHandlingNote(action == null ? null : action.getReason());
        current.setHandledByAdminId(admin.getAdminUserId());
        current.setHandledByAdminName(admin.getUserName());
        current.setHandledAt(now);
        current.setReminderCount((current.getReminderCount() == null ? 0 : current.getReminderCount()) + 1);
        current.setLastRemindedByAdminId(admin.getAdminUserId());
        current.setLastRemindedByAdminName(admin.getUserName());
        current.setLastRemindedAt(now);
        clearFailureAutoRemindSkip(current);
        current.setHandlingNotes(appendHandlingNote(current, "remind", currentStatus,
                action == null ? null : action.getReason(), admin, null, null));
        aiResumeFailureRecordService.recordFailure(current);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("ai_resume_remind")
                .targetType("ai_resume_failure")
                .beforeSnapshot(before)
                .afterSnapshot(current)
                .extraContext(buildFailureActionContext(current,
                        action == null ? null : action.getReason(),
                        currentStatus,
                        currentStatus,
                        "remind"))
                .operationResult(1)
                .build());

        return buildFailureItems(List.of(current)).get(0);
    }

    @Override
    public AdminAiResumeFailureItemDTO manualTakeoverFailure(String failureId, AdminAiResumeFailureActionDTO action) {
        AiResumeFailureRecordDTO current = aiResumeFailureRecordService.findFailure(failureId);
        if (current == null) {
            throw new BizException(AiResumeErrorCode.HISTORY_NOT_FOUND, "AI 失败样本不存在");
        }
        String currentStatus = normalizeFailureHandlingStatus(current.getHandlingStatus());
        ensureFailureOpenForCollaboration(currentStatus);

        AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();
        if (Objects.equals(current.getAssignedAdminId(), admin.getAdminUserId())
                && StringUtils.hasText(current.getAssignmentAcknowledgedAt())) {
            throw new BizException("当前失败样本已由你接管并确认，无需重复操作");
        }

        AiResumeFailureRecordDTO before = copyFailure(current);
        String now = LocalDateTime.now().format(TIME_FORMATTER);
        current.setHandlingStatus(currentStatus);
        current.setHandlingNote(action == null ? null : action.getReason());
        current.setHandledByAdminId(admin.getAdminUserId());
        current.setHandledByAdminName(admin.getUserName());
        current.setHandledAt(now);
        current.setAssignedAdminId(admin.getAdminUserId());
        current.setAssignedAdminName(admin.getUserName());
        current.setAssignedAt(now);
        current.setAssignmentAcknowledgedByAdminId(admin.getAdminUserId());
        current.setAssignmentAcknowledgedByAdminName(admin.getUserName());
        current.setAssignmentAcknowledgedAt(now);
        current.setManualTakeoverByAdminId(admin.getAdminUserId());
        current.setManualTakeoverByAdminName(admin.getUserName());
        current.setManualTakeoverAt(now);
        clearFailureAutoRemindSkip(current);
        current.setHandlingNotes(appendHandlingNote(current, "manual_takeover", currentStatus,
                action == null ? null : action.getReason(), admin, null, null));
        aiResumeFailureRecordService.recordFailure(current);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("ai_resume_manual_takeover")
                .targetType("ai_resume_failure")
                .beforeSnapshot(before)
                .afterSnapshot(current)
                .extraContext(buildFailureActionContext(current,
                        action == null ? null : action.getReason(),
                        currentStatus,
                        currentStatus,
                        "manual_takeover"))
                .operationResult(1)
                .build());

        return buildFailureItems(List.of(current)).get(0);
    }

    @Override
    public AdminAiResumeFailureItemDTO skipAutoRemind(String failureId, AdminAiResumeFailureActionDTO action) {
        AiResumeFailureRecordDTO current = aiResumeFailureRecordService.findFailure(failureId);
        if (current == null) {
            throw new BizException(AiResumeErrorCode.HISTORY_NOT_FOUND, "AI 失败样本不存在");
        }
        String currentStatus = normalizeFailureHandlingStatus(current.getHandlingStatus());
        ensureFailureOpenForCollaboration(currentStatus);
        if (current.getAssignedAdminId() == null) {
            throw new BizException("失败样本尚未分派处理人");
        }
        if (StringUtils.hasText(current.getAssignmentAcknowledgedAt())) {
            throw new BizException("当前失败样本已确认接手，无需跳过自动催办");
        }
        if (StringUtils.hasText(current.getAutoRemindSkippedAt())) {
            throw new BizException("当前失败样本已标记为跳过自动催办");
        }

        AiResumeFailureRecordDTO before = copyFailure(current);
        AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();
        String now = LocalDateTime.now().format(TIME_FORMATTER);
        current.setHandlingStatus(currentStatus);
        current.setHandlingNote(action == null ? null : action.getReason());
        current.setHandledByAdminId(admin.getAdminUserId());
        current.setHandledByAdminName(admin.getUserName());
        current.setHandledAt(now);
        current.setAutoRemindSkippedByAdminId(admin.getAdminUserId());
        current.setAutoRemindSkippedByAdminName(admin.getUserName());
        current.setAutoRemindSkippedAt(now);
        current.setHandlingNotes(appendHandlingNote(current, "skip_auto_remind", currentStatus,
                action == null ? null : action.getReason(), admin, null, null));
        aiResumeFailureRecordService.recordFailure(current);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("ai_resume_skip_auto_remind")
                .targetType("ai_resume_failure")
                .beforeSnapshot(before)
                .afterSnapshot(current)
                .extraContext(buildFailureActionContext(current,
                        action == null ? null : action.getReason(),
                        currentStatus,
                        currentStatus,
                        "skip_auto_remind"))
                .operationResult(1)
                .build());

        return buildFailureItems(List.of(current)).get(0);
    }

    private List<HistoryRecord> loadAllHistoryRecords() {
        List<HistoryRecord> records = new ArrayList<>();
        for (String key : scanKeys(AiResumeRedisKeys.historyPattern())) {
            Long userId = parseUserIdFromHistoryKey(key);
            if (userId == null) {
                continue;
            }
            String raw = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            try {
                List<AiResumeHistoryItemDTO> histories = objectMapper.readValue(raw, HISTORY_LIST_TYPE);
                for (AiResumeHistoryItemDTO history : safeList(histories)) {
                    records.add(new HistoryRecord(userId, history));
                }
            } catch (Exception error) {
                throw new BizException(AiResumeErrorCode.RESPONSE_UNPARSABLE, "AI 治理历史读取失败");
            }
        }
        return records;
    }

    private List<QuotaUsageRecord> loadCurrentMonthQuotaUsages() {
        LocalDate periodStart = LocalDate.now().withDayOfMonth(1);
        List<QuotaUsageRecord> records = new ArrayList<>();
        for (String key : scanKeys(AiQuotaRedisKeys.quotaPattern(periodStart))) {
            Long userId = parseUserIdFromQuotaKey(key);
            if (userId == null) {
                continue;
            }
            String raw = redisTemplate.opsForValue().get(key);
            int usedCount = parseInteger(raw);
            if (usedCount <= 0) {
                continue;
            }
            records.add(new QuotaUsageRecord(userId, usedCount));
        }
        return records;
    }

    private List<AdminAiResumeHistoryItemDTO> buildHistoryItems(List<HistoryRecord> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, UserContext> userContextMap = loadUserContexts(records.stream().map(HistoryRecord::userId).collect(Collectors.toSet()));
        Map<Long, ActorLevelInfoRespDTO> levelInfoMap = loadLevelInfos(userContextMap.keySet());
        List<AdminAiResumeHistoryItemDTO> items = new ArrayList<>();
        for (HistoryRecord record : records) {
            UserContext context = userContextMap.get(record.userId());
            ActorLevelInfoRespDTO levelInfo = levelInfoMap.get(record.userId());
            items.add(toAdminHistoryItem(record, context, levelInfo));
        }
        return items;
    }

    private List<AdminAiResumeQuotaUserDTO> buildQuotaUserItems(List<QuotaUsageRecord> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, UserContext> userContextMap = loadUserContexts(records.stream().map(QuotaUsageRecord::userId).collect(Collectors.toSet()));
        Map<Long, ActorLevelInfoRespDTO> levelInfoMap = loadLevelInfos(userContextMap.keySet());
        List<AdminAiResumeQuotaUserDTO> items = new ArrayList<>();
        for (QuotaUsageRecord record : records) {
            UserContext context = userContextMap.get(record.userId());
            ActorLevelInfoRespDTO levelInfo = levelInfoMap.get(record.userId());
            AdminAiResumeQuotaUserDTO item = new AdminAiResumeQuotaUserDTO();
            item.setUserId(record.userId());
            item.setUserName(resolveUserName(context, record.userId()));
            item.setPhone(resolvePhone(context));
            item.setRealAuthStatus(context == null || context.user() == null ? null : context.user().getRealAuthStatus());
            item.setLevel(levelInfo == null ? null : levelInfo.getLevel());
            item.setMembershipTier(levelInfo == null ? null : levelInfo.getMembershipTier());
            item.setTotalQuota(levelInfo == null || levelInfo.getLevelCapability() == null ? null : levelInfo.getLevelCapability().getAiQuotaPerMonth());
            item.setUsedCount(record.usedCount());
            items.add(item);
        }
        return items;
    }

    private List<AdminAiResumeFailureItemDTO> buildFailureItems(List<AiResumeFailureRecordDTO> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, UserContext> userContextMap = loadUserContexts(records.stream()
                .map(AiResumeFailureRecordDTO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, ActorLevelInfoRespDTO> levelInfoMap = loadLevelInfos(userContextMap.keySet());
        List<AdminAiResumeFailureItemDTO> items = new ArrayList<>();
        for (AiResumeFailureRecordDTO record : records) {
            UserContext context = userContextMap.get(record.getUserId());
            ActorLevelInfoRespDTO levelInfo = levelInfoMap.get(record.getUserId());
            AdminAiResumeFailureItemDTO item = new AdminAiResumeFailureItemDTO();
            item.setFailureId(record.getFailureId());
            item.setUserId(record.getUserId());
            item.setUserName(resolveUserName(context, record.getUserId()));
            item.setPhone(resolvePhone(context));
            item.setRealAuthStatus(context == null || context.user() == null ? null : context.user().getRealAuthStatus());
            item.setLevel(levelInfo == null ? null : levelInfo.getLevel());
            item.setMembershipTier(levelInfo == null ? null : levelInfo.getMembershipTier());
            item.setRequestId(record.getRequestId());
            item.setConversationId(record.getConversationId());
            item.setInstruction(record.getInstruction());
            item.setErrorCode(record.getErrorCode());
            item.setErrorMessage(record.getErrorMessage());
            item.setFailureType(record.getFailureType());
            item.setHitKeyword(record.getHitKeyword());
            item.setHandlingStatus(record.getHandlingStatus());
            item.setHandlingNote(record.getHandlingNote());
            item.setHandledByAdminId(record.getHandledByAdminId());
            item.setHandledByAdminName(record.getHandledByAdminName());
            item.setAssignedAdminId(record.getAssignedAdminId());
            item.setAssignedAdminName(record.getAssignedAdminName());
            item.setAssignedAt(record.getAssignedAt());
            item.setEscalationRoleCode(record.getEscalationRoleCode());
            item.setEscalationRoleName(record.getEscalationRoleName());
            item.setAssignmentAcknowledgedByAdminId(record.getAssignmentAcknowledgedByAdminId());
            item.setAssignmentAcknowledgedByAdminName(record.getAssignmentAcknowledgedByAdminName());
            item.setAssignmentAcknowledgedAt(record.getAssignmentAcknowledgedAt());
            item.setReminderCount(record.getReminderCount());
            item.setLastRemindedByAdminId(record.getLastRemindedByAdminId());
            item.setLastRemindedByAdminName(record.getLastRemindedByAdminName());
            item.setLastRemindedAt(record.getLastRemindedAt());
            item.setClaimDeadlineAt(resolveFailureClaimDeadlineAt(record));
            item.setCollaborationStatus(resolveFailureCollaborationStatus(record));
            item.setNotificationStatus(resolveFailureNotificationStatus(record));
            item.setNotificationSentAt(resolveFailureNotificationSentAt(record));
            item.setNotificationReceiptStatus(resolveFailureNotificationReceiptStatus(record));
            item.setNotificationReceiptAt(resolveFailureNotificationReceiptAt(record));
            item.setAutoRemindStage(resolveFailureAutoRemindStage(record));
            item.setSlaStatus(resolveFailureSlaStatus(record));
            item.setManualTakeoverByAdminId(record.getManualTakeoverByAdminId());
            item.setManualTakeoverByAdminName(record.getManualTakeoverByAdminName());
            item.setManualTakeoverAt(record.getManualTakeoverAt());
            item.setAutoRemindSkippedByAdminId(record.getAutoRemindSkippedByAdminId());
            item.setAutoRemindSkippedByAdminName(record.getAutoRemindSkippedByAdminName());
            item.setAutoRemindSkippedAt(record.getAutoRemindSkippedAt());
            item.setHandledAt(record.getHandledAt());
            item.setCreatedAt(record.getCreatedAt());
            item.setHandlingNotes(copyHandlingNotes(record.getHandlingNotes()));
            items.add(item);
        }
        return items;
    }

    private AdminAiResumeFailureItemDTO handleFailure(String failureId, String handlingStatus, AdminAiResumeFailureActionDTO action) {
        AiResumeFailureRecordDTO current = aiResumeFailureRecordService.findFailure(failureId);
        if (current == null) {
            throw new BizException(AiResumeErrorCode.HISTORY_NOT_FOUND, "AI 失败样本不存在");
        }
        AiResumeFailureRecordDTO before = copyFailure(current);
        AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();
        String currentStatus = normalizeFailureHandlingStatus(current.getHandlingStatus());
        ensureFailureTransitionAllowed(currentStatus, handlingStatus);
        String actionType = resolveFailureActionType(handlingStatus);
        AdminAiResumeFailureEscalationRoleOptionDTO escalationRole = null;
        if ("escalated".equals(handlingStatus)) {
            escalationRole = requireEligibleEscalationRole(action == null ? null : action.getEscalationRoleCode());
        }

        current.setHandlingStatus(handlingStatus);
        current.setHandlingNote(action == null ? null : action.getReason());
        current.setHandledByAdminId(admin.getAdminUserId());
        current.setHandledByAdminName(admin.getUserName());
        current.setHandledAt(LocalDateTime.now().format(TIME_FORMATTER));
        if (escalationRole != null) {
            current.setEscalationRoleCode(escalationRole.getRoleCode());
            current.setEscalationRoleName(escalationRole.getRoleName());
        }
        current.setHandlingNotes(appendHandlingNote(current, actionType, handlingStatus,
                action == null ? null : action.getReason(), admin, null, escalationRole));
        aiResumeFailureRecordService.recordFailure(current);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode(resolveFailureOperationCode(actionType))
                .targetType("ai_resume_failure")
                .beforeSnapshot(before)
                .afterSnapshot(current)
                .extraContext(buildFailureActionContext(current, action == null ? null : action.getReason(),
                        currentStatus, handlingStatus, actionType))
                .operationResult(1)
                .build());

        return buildFailureItems(List.of(current)).get(0);
    }

    private Map<Long, UserContext> loadUserContexts(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<Long, ActorProfile> actorProfileMap = actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>()
                        .in(ActorProfile::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(ActorProfile::getUserId, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<Long, UserContext> result = new HashMap<>();
        for (Long userId : userIds) {
            result.put(userId, new UserContext(userMap.get(userId), actorProfileMap.get(userId)));
        }
        return result;
    }

    private Map<Long, ActorLevelInfoRespDTO> loadLevelInfos(Collection<Long> userIds) {
        Map<Long, ActorLevelInfoRespDTO> result = new HashMap<>();
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            try {
                result.put(userId, membershipAccountService.actorLevelInfo(userId));
            } catch (RuntimeException ignored) {
                // keep this governance page readable even when a single user's等级信息不可用
            }
        }
        return result;
    }

    private AdminAiResumeHistoryItemDTO toAdminHistoryItem(HistoryRecord record,
                                                           UserContext context,
                                                           ActorLevelInfoRespDTO levelInfo) {
        AdminAiResumeHistoryItemDTO item = new AdminAiResumeHistoryItemDTO();
        item.setHistoryId(record.history().getHistoryId());
        item.setUserId(record.userId());
        item.setUserName(resolveUserName(context, record.userId()));
        item.setPhone(resolvePhone(context));
        item.setRealAuthStatus(context == null || context.user() == null ? null : context.user().getRealAuthStatus());
        item.setLevel(levelInfo == null ? null : levelInfo.getLevel());
        item.setMembershipTier(levelInfo == null ? null : levelInfo.getMembershipTier());
        item.setDraftId(record.history().getDraftId());
        item.setRequestId(record.history().getRequestId());
        item.setConversationId(record.history().getConversationId());
        item.setInstruction(record.history().getInstruction());
        item.setReply(record.history().getReply());
        item.setStatus(record.history().getStatus());
        item.setPatchCount(safeList(record.history().getPatches()).size());
        item.setPatches(new ArrayList<>(safeList(record.history().getPatches())));
        item.setBeforeSnapshot(new ArrayList<>(safeList(record.history().getBeforeSnapshot())));
        item.setAfterSnapshot(new ArrayList<>(safeList(record.history().getAfterSnapshot())));
        item.setCreatedAt(record.history().getCreatedAt());
        item.setAppliedAt(record.history().getAppliedAt());
        item.setRolledBackAt(record.history().getRolledBackAt());
        return item;
    }

    private List<HistoryRecord> sortHistoryRecords(List<HistoryRecord> records) {
        return records.stream()
                .sorted(Comparator.comparing(this::historySortTime, Comparator.nullsLast(LocalDateTime::compareTo)).reversed()
                        .thenComparing(item -> defaultString(item.history().getHistoryId()), Comparator.reverseOrder()))
                .toList();
    }

    private List<QuotaUsageRecord> sortQuotaUsageRecords(List<QuotaUsageRecord> records) {
        return records.stream()
                .sorted(Comparator.comparingInt(QuotaUsageRecord::usedCount).reversed()
                        .thenComparing(item -> item.userId() == null ? Long.MAX_VALUE : item.userId()))
                .toList();
    }

    private List<AiResumeFailureRecordDTO> loadFailureRecords(AdminAiResumeFailureQueryDTO query, boolean sensitiveOnly) {
        return aiResumeFailureRecordService.listAllRecords().stream()
                .filter(item -> !sensitiveOnly || isSensitiveFailure(item))
                .filter(item -> matchesFailureQuery(item, query))
                .limit(resolveFailureLimit(query))
                .toList();
    }

    private boolean matchesQuery(HistoryRecord item, AdminAiResumeHistoryQueryDTO query) {
        if (query.getUserId() != null && !Objects.equals(query.getUserId(), item.userId())) {
            return false;
        }
        if (StringUtils.hasText(query.getStatus())
                && !Objects.equals(normalize(query.getStatus()), normalize(item.history().getStatus()))) {
            return false;
        }
        if (StringUtils.hasText(query.getRequestId())
                && !Objects.equals(normalize(query.getRequestId()), normalize(item.history().getRequestId()))) {
            return false;
        }
        if (!StringUtils.hasText(query.getKeyword())) {
            return true;
        }
        String keyword = normalize(query.getKeyword());
        return containsNormalized(item.history().getHistoryId(), keyword)
                || containsNormalized(item.history().getDraftId(), keyword)
                || containsNormalized(item.history().getInstruction(), keyword)
                || containsNormalized(item.history().getReply(), keyword)
                || containsNormalized(item.history().getRequestId(), keyword)
                || containsNormalized(item.userId() == null ? null : String.valueOf(item.userId()), keyword);
    }

    private boolean matchesFailureQuery(AiResumeFailureRecordDTO item, AdminAiResumeFailureQueryDTO query) {
        if (query == null) {
            return true;
        }
        if (query.getUserId() != null && !Objects.equals(query.getUserId(), item.getUserId())) {
            return false;
        }
        if (StringUtils.hasText(query.getHandlingStatus())
                && !Objects.equals(normalize(query.getHandlingStatus()), normalize(item.getHandlingStatus()))) {
            return false;
        }
        if (StringUtils.hasText(query.getFailureType())
                && !Objects.equals(normalize(query.getFailureType()), normalize(item.getFailureType()))) {
            return false;
        }
        if (StringUtils.hasText(query.getRequestId())
                && !containsNormalized(item.getRequestId(), normalize(query.getRequestId()))) {
            return false;
        }
        if (query.getAssignedAdminId() != null
                && !Objects.equals(query.getAssignedAdminId(), item.getAssignedAdminId())) {
            return false;
        }
        if (StringUtils.hasText(query.getEscalationRoleCode())
                && !Objects.equals(normalize(query.getEscalationRoleCode()), normalize(item.getEscalationRoleCode()))) {
            return false;
        }
        if (StringUtils.hasText(query.getCollaborationStatus())
                && !Objects.equals(normalize(query.getCollaborationStatus()), normalize(resolveFailureCollaborationStatus(item)))) {
            return false;
        }
        if (StringUtils.hasText(query.getNotificationStatus())
                && !Objects.equals(normalize(query.getNotificationStatus()), normalize(resolveFailureNotificationStatus(item)))) {
            return false;
        }
        if (StringUtils.hasText(query.getNotificationReceiptStatus())
                && !Objects.equals(normalize(query.getNotificationReceiptStatus()), normalize(resolveFailureNotificationReceiptStatus(item)))) {
            return false;
        }
        if (StringUtils.hasText(query.getAutoRemindStage())
                && !Objects.equals(normalize(query.getAutoRemindStage()), normalize(resolveFailureAutoRemindStage(item)))) {
            return false;
        }
        if (StringUtils.hasText(query.getSlaStatus())
                && !Objects.equals(normalize(query.getSlaStatus()), normalize(resolveFailureSlaStatus(item)))) {
            return false;
        }
        if (!StringUtils.hasText(query.getKeyword())) {
            return true;
        }
        String keyword = normalize(query.getKeyword());
        return containsNormalized(item.getFailureId(), keyword)
                || containsNormalized(item.getRequestId(), keyword)
                || containsNormalized(item.getInstruction(), keyword)
                || containsNormalized(item.getErrorMessage(), keyword)
                || containsNormalized(item.getHitKeyword(), keyword)
                || containsNormalized(item.getHandlingNote(), keyword)
                || containsNormalized(item.getAssignedAdminName(), keyword)
                || containsNormalized(item.getEscalationRoleCode(), keyword)
                || containsNormalized(item.getEscalationRoleName(), keyword);
    }

    private boolean isCurrentMonthHistory(HistoryRecord record) {
        LocalDateTime time = historySortTime(record);
        if (time == null) {
            return false;
        }
        LocalDate now = LocalDate.now();
        return time.getYear() == now.getYear() && time.getMonth() == now.getMonth();
    }

    private LocalDateTime historySortTime(HistoryRecord record) {
        return parseTime(StringUtils.hasText(record.history().getAppliedAt()) ? record.history().getAppliedAt() : record.history().getCreatedAt());
    }

    private LocalDateTime parseTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), TIME_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> scanKeys(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
        List<String> keys = redisTemplate.execute((RedisConnection connection) -> {
            List<String> values = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    String key = redisTemplate.getStringSerializer().deserialize(cursor.next());
                    if (StringUtils.hasText(key)) {
                        values.add(key);
                    }
                }
            } catch (Exception error) {
                throw new BizException(AiResumeErrorCode.RESPONSE_UNPARSABLE, "AI 治理键扫描失败");
            }
            return values;
        });
        return keys == null ? Collections.emptyList() : keys;
    }

    private Long parseUserIdFromHistoryKey(String key) {
        if (!StringUtils.hasText(key) || !key.startsWith(HISTORY_KEY_PREFIX)) {
            return null;
        }
        String raw = key.substring(HISTORY_KEY_PREFIX.length());
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseUserIdFromQuotaKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        int index = key.lastIndexOf(':');
        if (index < 0 || index >= key.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(key.substring(index + 1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int parseInteger(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String resolveUserName(UserContext context, Long userId) {
        if (context != null && context.actorProfile() != null) {
            if (StringUtils.hasText(context.actorProfile().getRealName())) {
                return context.actorProfile().getRealName().trim();
            }
            if (StringUtils.hasText(context.actorProfile().getNickName())) {
                return context.actorProfile().getNickName().trim();
            }
        }
        if (context != null && context.user() != null) {
            if (StringUtils.hasText(context.user().getUserName())) {
                return context.user().getUserName().trim();
            }
            if (StringUtils.hasText(context.user().getAccount())) {
                return context.user().getAccount().trim();
            }
        }
        return userId == null ? "--" : "用户 " + userId;
    }

    private String resolvePhone(UserContext context) {
        if (context != null && context.actorProfile() != null && StringUtils.hasText(context.actorProfile().getPhone())) {
            return context.actorProfile().getPhone().trim();
        }
        if (context != null && context.user() != null && StringUtils.hasText(context.user().getPhone())) {
            return context.user().getPhone().trim();
        }
        return null;
    }

    private boolean containsNormalized(String source, String keyword) {
        return StringUtils.hasText(source) && normalize(source).contains(keyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private int resolveFailureLimit(AdminAiResumeFailureQueryDTO query) {
        if (query == null || query.getLimit() == null) {
            return 20;
        }
        return Math.min(Math.max(query.getLimit(), 1), 100);
    }

    private boolean isSensitiveFailure(AiResumeFailureRecordDTO record) {
        return record != null
                && record.getErrorCode() != null
                && record.getErrorCode() == AiResumeErrorCode.CONTENT_BLOCKED;
    }

    private List<AdminAiResumeFailureEscalationRoleOptionDTO> loadEligibleEscalationRoleOptions() {
        return safeList(adminRoleService.aiGovernanceMatrix().getList()).stream()
                .filter(Objects::nonNull)
                .filter(item -> Integer.valueOf(1).equals(item.getStatus()))
                .filter(this::canCollaborateOnFailureRole)
                .sorted(Comparator.comparing((AdminRoleAiGovernanceMatrixItemDTO item) -> !Boolean.TRUE.equals(item.getAiReady()))
                        .thenComparing(item -> defaultString(item.getRoleName()))
                        .thenComparing(item -> defaultString(item.getRoleCode())))
                .map(item -> {
                    AdminAiResumeFailureEscalationRoleOptionDTO dto = new AdminAiResumeFailureEscalationRoleOptionDTO();
                    dto.setAdminRoleId(item.getAdminRoleId());
                    dto.setRoleCode(item.getRoleCode());
                    dto.setRoleName(item.getRoleName());
                    dto.setRolloutStage(item.getRolloutStage());
                    return dto;
                })
                .toList();
    }

    private List<AdminAiResumeFailureAssigneeOptionDTO> loadEligibleAssigneeOptions(
            List<AdminAiResumeFailureEscalationRoleOptionDTO> roleOptions) {
        Map<Long, AdminAiResumeFailureEscalationRoleOptionDTO> roleOptionMap = safeList(roleOptions).stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getAdminRoleId() != null)
                .collect(Collectors.toMap(
                        AdminAiResumeFailureEscalationRoleOptionDTO::getAdminRoleId,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        if (roleOptionMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<AdminUserRole> bindings = adminUserRoleService.lambdaQuery()
                .in(AdminUserRole::getAdminRoleId, roleOptionMap.keySet())
                .list();
        if (bindings.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<AdminAiResumeFailureEscalationRoleOptionDTO>> roleOptionsByUserId = new LinkedHashMap<>();
        for (AdminUserRole binding : bindings) {
            if (binding == null || binding.getAdminUserId() == null) {
                continue;
            }
            AdminAiResumeFailureEscalationRoleOptionDTO roleOption = roleOptionMap.get(binding.getAdminRoleId());
            if (roleOption == null) {
                continue;
            }
            roleOptionsByUserId.computeIfAbsent(binding.getAdminUserId(), key -> new ArrayList<>()).add(roleOption);
        }
        if (roleOptionsByUserId.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, AdminUser> userMap = adminUserService.lambdaQuery()
                .eq(AdminUser::getStatus, 1)
                .in(AdminUser::getAdminUserId, roleOptionsByUserId.keySet())
                .list()
                .stream()
                .collect(Collectors.toMap(AdminUser::getAdminUserId, item -> item, (left, right) -> left, LinkedHashMap::new));
        return roleOptionsByUserId.entrySet().stream()
                .map(entry -> {
                    AdminUser user = userMap.get(entry.getKey());
                    if (user == null) {
                        return null;
                    }
                    LinkedHashSet<String> roleCodes = new LinkedHashSet<>();
                    LinkedHashSet<String> roleNames = new LinkedHashSet<>();
                    for (AdminAiResumeFailureEscalationRoleOptionDTO roleOption : entry.getValue()) {
                        if (roleOption == null) {
                            continue;
                        }
                        if (StringUtils.hasText(roleOption.getRoleCode())) {
                            roleCodes.add(roleOption.getRoleCode());
                        }
                        if (StringUtils.hasText(roleOption.getRoleName())) {
                            roleNames.add(roleOption.getRoleName());
                        }
                    }
                    AdminAiResumeFailureAssigneeOptionDTO dto = new AdminAiResumeFailureAssigneeOptionDTO();
                    dto.setAdminUserId(user.getAdminUserId());
                    dto.setUserName(resolveAdminUserName(user));
                    dto.setAccount(user.getAccount());
                    dto.setRoleCodes(new ArrayList<>(roleCodes));
                    dto.setRoleNames(new ArrayList<>(roleNames));
                    return dto;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((AdminAiResumeFailureAssigneeOptionDTO item) -> defaultString(item.getUserName()))
                        .thenComparing(item -> defaultString(item.getAccount())))
                .toList();
    }

    private boolean canCollaborateOnFailureRole(AdminRoleAiGovernanceMatrixItemDTO item) {
        return item != null
                && (Boolean.TRUE.equals(item.getHasAiReviewAction()) || Boolean.TRUE.equals(item.getHasAiResolveAction()));
    }

    private AdminAiResumeFailureAssigneeOptionDTO requireEligibleAssignee(Long assignedAdminId) {
        return loadEligibleAssigneeOptions(loadEligibleEscalationRoleOptions()).stream()
                .filter(item -> Objects.equals(item.getAdminUserId(), assignedAdminId))
                .findFirst()
                .orElseThrow(() -> new BizException("目标处理人不存在或不具备 AI 治理处置资格"));
    }

    private AdminAiResumeFailureEscalationRoleOptionDTO requireEligibleEscalationRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            throw new BizException("请选择升级目标角色");
        }
        String normalizedRoleCode = normalize(roleCode);
        return loadEligibleEscalationRoleOptions().stream()
                .filter(item -> Objects.equals(normalize(item.getRoleCode()), normalizedRoleCode))
                .findFirst()
                .orElseThrow(() -> new BizException("升级目标角色不存在或不具备 AI 治理处置资格"));
    }

    private String resolveAdminUserName(AdminUser user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getUserName())) {
            return user.getUserName().trim();
        }
        if (StringUtils.hasText(user.getAccount())) {
            return user.getAccount().trim();
        }
        return user.getAdminUserId() == null ? null : "后台账号 " + user.getAdminUserId();
    }

    private List<AiResumeFailureHandlingNoteDTO> appendHandlingNote(AiResumeFailureRecordDTO record,
                                                                    String actionType,
                                                                    String handlingStatus,
                                                                    String reason,
                                                                    AdminAuthenticatedUser admin,
                                                                    AdminAiResumeFailureAssigneeOptionDTO assignee,
                                                                    AdminAiResumeFailureEscalationRoleOptionDTO escalationRole) {
        List<AiResumeFailureHandlingNoteDTO> notes = copyHandlingNotes(record.getHandlingNotes());
        AiResumeFailureHandlingNoteDTO note = new AiResumeFailureHandlingNoteDTO();
        note.setActionType(actionType);
        note.setHandlingStatus(handlingStatus);
        note.setHandlingNote(reason);
        note.setHandledByAdminId(admin.getAdminUserId());
        note.setHandledByAdminName(admin.getUserName());
        if (assignee != null) {
            note.setAssignedAdminId(assignee.getAdminUserId());
            note.setAssignedAdminName(assignee.getUserName());
        } else {
            note.setAssignedAdminId(record.getAssignedAdminId());
            note.setAssignedAdminName(record.getAssignedAdminName());
        }
        note.setAssignedAt(record.getAssignedAt());
        if (escalationRole != null) {
            note.setEscalationRoleCode(escalationRole.getRoleCode());
            note.setEscalationRoleName(escalationRole.getRoleName());
        } else {
            note.setEscalationRoleCode(record.getEscalationRoleCode());
            note.setEscalationRoleName(record.getEscalationRoleName());
        }
        note.setAssignmentAcknowledgedByAdminId(record.getAssignmentAcknowledgedByAdminId());
        note.setAssignmentAcknowledgedByAdminName(record.getAssignmentAcknowledgedByAdminName());
        note.setAssignmentAcknowledgedAt(record.getAssignmentAcknowledgedAt());
        note.setReminderCount(record.getReminderCount());
        note.setLastRemindedByAdminId(record.getLastRemindedByAdminId());
        note.setLastRemindedByAdminName(record.getLastRemindedByAdminName());
        note.setLastRemindedAt(record.getLastRemindedAt());
        note.setManualTakeoverByAdminId(record.getManualTakeoverByAdminId());
        note.setManualTakeoverByAdminName(record.getManualTakeoverByAdminName());
        note.setManualTakeoverAt(record.getManualTakeoverAt());
        note.setAutoRemindSkippedByAdminId(record.getAutoRemindSkippedByAdminId());
        note.setAutoRemindSkippedByAdminName(record.getAutoRemindSkippedByAdminName());
        note.setAutoRemindSkippedAt(record.getAutoRemindSkippedAt());
        note.setHandledAt(record.getHandledAt());
        notes.add(0, note);
        return notes;
    }

    private List<AiResumeFailureHandlingNoteDTO> copyHandlingNotes(List<AiResumeFailureHandlingNoteDTO> values) {
        List<AiResumeFailureHandlingNoteDTO> copies = new ArrayList<>();
        for (AiResumeFailureHandlingNoteDTO value : safeList(values)) {
            if (value == null) {
                continue;
            }
            AiResumeFailureHandlingNoteDTO copy = new AiResumeFailureHandlingNoteDTO();
            copy.setActionType(value.getActionType());
            copy.setHandlingStatus(value.getHandlingStatus());
            copy.setHandlingNote(value.getHandlingNote());
            copy.setHandledByAdminId(value.getHandledByAdminId());
            copy.setHandledByAdminName(value.getHandledByAdminName());
            copy.setAssignedAdminId(value.getAssignedAdminId());
            copy.setAssignedAdminName(value.getAssignedAdminName());
            copy.setAssignedAt(value.getAssignedAt());
            copy.setEscalationRoleCode(value.getEscalationRoleCode());
            copy.setEscalationRoleName(value.getEscalationRoleName());
            copy.setAssignmentAcknowledgedByAdminId(value.getAssignmentAcknowledgedByAdminId());
            copy.setAssignmentAcknowledgedByAdminName(value.getAssignmentAcknowledgedByAdminName());
            copy.setAssignmentAcknowledgedAt(value.getAssignmentAcknowledgedAt());
            copy.setReminderCount(value.getReminderCount());
            copy.setLastRemindedByAdminId(value.getLastRemindedByAdminId());
            copy.setLastRemindedByAdminName(value.getLastRemindedByAdminName());
            copy.setLastRemindedAt(value.getLastRemindedAt());
            copy.setManualTakeoverByAdminId(value.getManualTakeoverByAdminId());
            copy.setManualTakeoverByAdminName(value.getManualTakeoverByAdminName());
            copy.setManualTakeoverAt(value.getManualTakeoverAt());
            copy.setAutoRemindSkippedByAdminId(value.getAutoRemindSkippedByAdminId());
            copy.setAutoRemindSkippedByAdminName(value.getAutoRemindSkippedByAdminName());
            copy.setAutoRemindSkippedAt(value.getAutoRemindSkippedAt());
            copy.setHandledAt(value.getHandledAt());
            copies.add(copy);
        }
        return copies;
    }

    private String resolveFailureActionType(String handlingStatus) {
        if ("retry_advised".equals(handlingStatus)) {
            return "suggest_retry";
        }
        if ("ignored".equals(handlingStatus)) {
            return "ignore";
        }
        if ("escalated".equals(handlingStatus)) {
            return "escalate";
        }
        if ("closed".equals(handlingStatus)) {
            return "close";
        }
        return "review";
    }

    private String resolveFailureOperationCode(String actionType) {
        if ("assign".equals(actionType)) {
            return "ai_resume_assign";
        }
        if ("acknowledge".equals(actionType)) {
            return "ai_resume_acknowledge";
        }
        if ("remind".equals(actionType)) {
            return "ai_resume_remind";
        }
        if ("manual_takeover".equals(actionType)) {
            return "ai_resume_manual_takeover";
        }
        if ("skip_auto_remind".equals(actionType)) {
            return "ai_resume_skip_auto_remind";
        }
        if ("suggest_retry".equals(actionType)) {
            return "ai_resume_suggest_retry";
        }
        if ("ignore".equals(actionType)) {
            return "ai_resume_ignore";
        }
        if ("escalate".equals(actionType)) {
            return "ai_resume_escalate";
        }
        if ("close".equals(actionType)) {
            return "ai_resume_close";
        }
        return "ai_resume_review";
    }

    private void ensureFailureTransitionAllowed(String currentStatus, String targetStatus) {
        if (Objects.equals(currentStatus, targetStatus)) {
            throw new BizException("失败样本当前状态已是目标状态");
        }
        List<String> allowedTargets = FAILURE_ALLOWED_TRANSITIONS.getOrDefault(currentStatus, FAILURE_ALLOWED_TRANSITIONS.get("pending"));
        if (!allowedTargets.contains(targetStatus)) {
            throw new BizException("当前失败样本状态不允许执行该处置动作");
        }
    }

    private void ensureFailureOpenForCollaboration(String currentStatus) {
        if (isFailureTerminal(currentStatus)) {
            throw new BizException("终态失败样本不允许再执行责任分派");
        }
    }

    private void clearFailureAutoRemindSkip(AiResumeFailureRecordDTO record) {
        if (record == null) {
            return;
        }
        record.setAutoRemindSkippedByAdminId(null);
        record.setAutoRemindSkippedByAdminName(null);
        record.setAutoRemindSkippedAt(null);
    }

    private void clearFailureManualTakeover(AiResumeFailureRecordDTO record) {
        if (record == null) {
            return;
        }
        record.setManualTakeoverByAdminId(null);
        record.setManualTakeoverByAdminName(null);
        record.setManualTakeoverAt(null);
    }

    private String resolveFailureClaimDeadlineAt(AiResumeFailureRecordDTO record) {
        if (record == null || !StringUtils.hasText(record.getAssignedAt())) {
            return null;
        }
        LocalDateTime assignedAt = parseTime(record.getAssignedAt());
        if (assignedAt == null) {
            return null;
        }
        return assignedAt.plusHours(FAILURE_ASSIGN_ACK_SLA_HOURS).format(TIME_FORMATTER);
    }

    private String resolveFailureCollaborationStatus(AiResumeFailureRecordDTO record) {
        if (record == null || record.getAssignedAdminId() == null) {
            return "unassigned";
        }
        String handlingStatus = normalizeFailureHandlingStatus(record.getHandlingStatus());
        if (isFailureTerminal(handlingStatus)) {
            return "resolved";
        }
        if (StringUtils.hasText(record.getAssignmentAcknowledgedAt())) {
            return "acknowledged";
        }
        LocalDateTime deadline = parseTime(resolveFailureClaimDeadlineAt(record));
        if (deadline != null && LocalDateTime.now().isAfter(deadline)) {
            return "ack_overdue";
        }
        return "pending_ack";
    }

    private String resolveFailureNotificationStatus(AiResumeFailureRecordDTO record) {
        String notificationSentAt = resolveFailureNotificationSentAt(record);
        if (!StringUtils.hasText(notificationSentAt)) {
            return isFailureTerminal(record == null ? null : normalizeFailureHandlingStatus(record.getHandlingStatus()))
                    ? "not_required"
                    : "pending_send";
        }
        Integer reminderCount = record == null ? null : record.getReminderCount();
        if (StringUtils.hasText(record == null ? null : record.getLastRemindedAt())
                && (reminderCount == null ? 0 : reminderCount) > 0) {
            return "resent";
        }
        return "sent";
    }

    private String resolveFailureNotificationSentAt(AiResumeFailureRecordDTO record) {
        if (record == null) {
            return null;
        }
        if (StringUtils.hasText(record.getLastRemindedAt())) {
            return record.getLastRemindedAt();
        }
        if (StringUtils.hasText(record.getAssignedAt())) {
            return record.getAssignedAt();
        }
        return null;
    }

    private String resolveFailureNotificationReceiptStatus(AiResumeFailureRecordDTO record) {
        String notificationSentAt = resolveFailureNotificationSentAt(record);
        if (!StringUtils.hasText(notificationSentAt)) {
            return isFailureTerminal(record == null ? null : normalizeFailureHandlingStatus(record.getHandlingStatus()))
                    ? "not_required"
                    : "not_sent";
        }
        if (StringUtils.hasText(resolveFailureNotificationReceiptAt(record))) {
            return "received";
        }
        LocalDateTime deadline = parseTime(resolveFailureClaimDeadlineAt(record));
        if (deadline != null && LocalDateTime.now().isAfter(deadline)) {
            return "receipt_overdue";
        }
        return "pending_receipt";
    }

    private String resolveFailureNotificationReceiptAt(AiResumeFailureRecordDTO record) {
        return record == null ? null : record.getAssignmentAcknowledgedAt();
    }

    private String resolveFailureAutoRemindStage(AiResumeFailureRecordDTO record) {
        String handlingStatus = record == null ? null : normalizeFailureHandlingStatus(record.getHandlingStatus());
        if (isFailureTerminal(handlingStatus)) {
            return "completed";
        }
        if (record != null && StringUtils.hasText(record.getManualTakeoverAt())) {
            return "manual_takeover";
        }
        if (record != null && StringUtils.hasText(record.getAssignmentAcknowledgedAt())) {
            return "completed";
        }
        if (record != null && StringUtils.hasText(record.getAutoRemindSkippedAt())) {
            return "skipped";
        }
        if (!StringUtils.hasText(resolveFailureNotificationSentAt(record))) {
            return "idle";
        }
        Integer reminderCount = record.getReminderCount();
        int resolvedReminderCount = reminderCount == null ? 0 : reminderCount;
        LocalDateTime deadline = parseTime(resolveFailureClaimDeadlineAt(record));
        boolean overdue = deadline != null && LocalDateTime.now().isAfter(deadline);
        if (overdue && resolvedReminderCount >= FAILURE_AUTO_REMIND_MAX_COUNT) {
            return "escalation_due";
        }
        if (overdue) {
            return resolvedReminderCount > 0 ? "escalation_due" : "ready";
        }
        if (resolvedReminderCount > 0) {
            return "manual_intervened";
        }
        return "watching";
    }

    private String resolveFailureSlaStatus(AiResumeFailureRecordDTO record) {
        if (!StringUtils.hasText(resolveFailureNotificationSentAt(record))) {
            return "not_started";
        }
        LocalDateTime deadline = parseTime(resolveFailureClaimDeadlineAt(record));
        if (deadline == null) {
            return "not_started";
        }
        String handlingStatus = record == null ? null : normalizeFailureHandlingStatus(record.getHandlingStatus());
        LocalDateTime evaluatedAt = parseTime(resolveFailureNotificationReceiptAt(record));
        if (evaluatedAt == null && isFailureTerminal(handlingStatus)) {
            evaluatedAt = parseTime(record.getHandledAt());
        }
        if (evaluatedAt == null) {
            evaluatedAt = LocalDateTime.now();
        }
        if (evaluatedAt.isAfter(deadline)) {
            return "breached";
        }
        if (StringUtils.hasText(resolveFailureNotificationReceiptAt(record)) || isFailureTerminal(handlingStatus)) {
            return "within_sla";
        }
        return "active";
    }

    private String normalizeFailureHandlingStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : "pending";
    }

    private boolean isFailureTerminal(String status) {
        return "ignored".equals(status) || "closed".equals(status);
    }

    private AiResumeFailureRecordDTO copyFailure(AiResumeFailureRecordDTO record) {
        AiResumeFailureRecordDTO copy = new AiResumeFailureRecordDTO();
        copy.setFailureId(record.getFailureId());
        copy.setUserId(record.getUserId());
        copy.setRequestId(record.getRequestId());
        copy.setConversationId(record.getConversationId());
        copy.setInstruction(record.getInstruction());
        copy.setErrorCode(record.getErrorCode());
        copy.setErrorMessage(record.getErrorMessage());
        copy.setFailureType(record.getFailureType());
        copy.setHitKeyword(record.getHitKeyword());
        copy.setHandlingStatus(record.getHandlingStatus());
        copy.setHandlingNote(record.getHandlingNote());
        copy.setHandledByAdminId(record.getHandledByAdminId());
        copy.setHandledByAdminName(record.getHandledByAdminName());
        copy.setAssignedAdminId(record.getAssignedAdminId());
        copy.setAssignedAdminName(record.getAssignedAdminName());
        copy.setAssignedAt(record.getAssignedAt());
        copy.setEscalationRoleCode(record.getEscalationRoleCode());
        copy.setEscalationRoleName(record.getEscalationRoleName());
        copy.setAssignmentAcknowledgedByAdminId(record.getAssignmentAcknowledgedByAdminId());
        copy.setAssignmentAcknowledgedByAdminName(record.getAssignmentAcknowledgedByAdminName());
        copy.setAssignmentAcknowledgedAt(record.getAssignmentAcknowledgedAt());
        copy.setReminderCount(record.getReminderCount());
        copy.setLastRemindedByAdminId(record.getLastRemindedByAdminId());
        copy.setLastRemindedByAdminName(record.getLastRemindedByAdminName());
        copy.setLastRemindedAt(record.getLastRemindedAt());
        copy.setManualTakeoverByAdminId(record.getManualTakeoverByAdminId());
        copy.setManualTakeoverByAdminName(record.getManualTakeoverByAdminName());
        copy.setManualTakeoverAt(record.getManualTakeoverAt());
        copy.setAutoRemindSkippedByAdminId(record.getAutoRemindSkippedByAdminId());
        copy.setAutoRemindSkippedByAdminName(record.getAutoRemindSkippedByAdminName());
        copy.setAutoRemindSkippedAt(record.getAutoRemindSkippedAt());
        copy.setHandledAt(record.getHandledAt());
        copy.setCreatedAt(record.getCreatedAt());
        copy.setHandlingNotes(copyHandlingNotes(record.getHandlingNotes()));
        return copy;
    }

    private Map<String, Object> buildFailureActionContext(AiResumeFailureRecordDTO record, String reason,
                                                          String previousHandlingStatus, String handlingStatus,
                                                          String actionType) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("failure_id", record.getFailureId());
        context.put("request_id", record.getRequestId());
        context.put("action_type", actionType);
        context.put("handling_status_before", previousHandlingStatus);
        context.put("handling_status", handlingStatus);
        context.put("reason", reason);
        context.put("assigned_admin_id", record.getAssignedAdminId());
        context.put("assigned_admin_name", record.getAssignedAdminName());
        context.put("assigned_at", record.getAssignedAt());
        context.put("escalation_role_code", record.getEscalationRoleCode());
        context.put("escalation_role_name", record.getEscalationRoleName());
        context.put("assignment_acknowledged_by_admin_id", record.getAssignmentAcknowledgedByAdminId());
        context.put("assignment_acknowledged_by_admin_name", record.getAssignmentAcknowledgedByAdminName());
        context.put("assignment_acknowledged_at", record.getAssignmentAcknowledgedAt());
        context.put("reminder_count", record.getReminderCount());
        context.put("last_reminded_by_admin_id", record.getLastRemindedByAdminId());
        context.put("last_reminded_by_admin_name", record.getLastRemindedByAdminName());
        context.put("last_reminded_at", record.getLastRemindedAt());
        context.put("claim_deadline_at", resolveFailureClaimDeadlineAt(record));
        context.put("collaboration_status", resolveFailureCollaborationStatus(record));
        context.put("notification_status", resolveFailureNotificationStatus(record));
        context.put("notification_sent_at", resolveFailureNotificationSentAt(record));
        context.put("notification_receipt_status", resolveFailureNotificationReceiptStatus(record));
        context.put("notification_receipt_at", resolveFailureNotificationReceiptAt(record));
        context.put("auto_remind_stage", resolveFailureAutoRemindStage(record));
        context.put("sla_status", resolveFailureSlaStatus(record));
        context.put("manual_takeover_by_admin_id", record.getManualTakeoverByAdminId());
        context.put("manual_takeover_by_admin_name", record.getManualTakeoverByAdminName());
        context.put("manual_takeover_at", record.getManualTakeoverAt());
        context.put("auto_remind_skipped_by_admin_id", record.getAutoRemindSkippedByAdminId());
        context.put("auto_remind_skipped_by_admin_name", record.getAutoRemindSkippedByAdminName());
        context.put("auto_remind_skipped_at", record.getAutoRemindSkippedAt());
        context.put("error_code", record.getErrorCode());
        context.put("failure_type", record.getFailureType());
        return context;
    }

    private record HistoryRecord(Long userId, AiResumeHistoryItemDTO history) {
    }

    private record QuotaUsageRecord(Long userId, int usedCount) {
    }

    private record UserContext(User user, ActorProfile actorProfile) {
    }
}
