package com.kaipai.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.ai.dto.AdminAiResumeFailureActionDTO;
import com.kaipai.model.ai.dto.AdminAiResumeFailureAssigneeOptionDTO;
import com.kaipai.model.ai.dto.AdminAiResumeFailureCollaborationCatalogDTO;
import com.kaipai.model.ai.dto.AdminAiResumeFailureEscalationRoleOptionDTO;
import com.kaipai.model.ai.dto.AdminAiResumeFailureItemDTO;
import com.kaipai.model.ai.dto.AdminAiResumeFailureQueryDTO;
import com.kaipai.model.ai.dto.AdminAiResumeGovernanceSweepItemDTO;
import com.kaipai.model.ai.dto.AdminAiResumeGovernanceSweepRequestDTO;
import com.kaipai.model.ai.dto.AdminAiResumeGovernanceSweepResultDTO;
import com.kaipai.model.ai.dto.AdminAiResumeHistoryItemDTO;
import com.kaipai.model.ai.dto.AdminAiResumeHistoryQueryDTO;
import com.kaipai.model.ai.dto.AdminAiResumeOverviewDTO;
import com.kaipai.model.ai.dto.AdminAiResumeQuotaUserDTO;
import com.kaipai.model.ai.dto.AiResumeErrorCode;
import com.kaipai.model.ai.dto.AiResumeFailureHandlingNoteDTO;
import com.kaipai.model.ai.dto.AiResumeFailureRecordDTO;
import com.kaipai.model.ai.dto.AiResumeHistoryItemDTO;
import com.kaipai.model.ai.dto.AiResumeNotificationDispatchResultDTO;
import com.kaipai.model.ai.dto.AiResumeNotificationSendCommand;
import com.kaipai.model.ai.entity.AiResumeNotificationDelivery;
import com.kaipai.model.level.dto.ActorLevelInfoRespDTO;
import com.kaipai.model.system.dto.AdminRoleAiGovernanceMatrixItemDTO;
import com.kaipai.model.system.entity.AdminUser;
import com.kaipai.model.system.entity.AdminUserRole;
import com.kaipai.model.user.entity.User;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.service.ai.AdminAiResumeGovernanceService;
import com.kaipai.service.ai.AiResumeFailureRecordService;
import com.kaipai.service.ai.AiResumeNotificationDeliveryService;
import com.kaipai.service.ai.AiResumeNotificationDispatchService;
import com.kaipai.service.capability.CapabilityAccountService;
import com.kaipai.service.system.AdminRoleService;
import com.kaipai.service.system.AdminUserRoleService;
import com.kaipai.service.system.AdminUserService;
import com.kaipai.mapper.user.UserMapper;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAiResumeGovernanceServiceImpl implements AdminAiResumeGovernanceService {

    private static final TypeReference<List<AiResumeHistoryItemDTO>> HISTORY_LIST_TYPE = new TypeReference<>() {
    };
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter SWEEP_REQUEST_ID_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int OVERVIEW_TOP_QUOTA_LIMIT = 5;
    private static final int OVERVIEW_RECENT_HISTORY_LIMIT = 5;
    private static final int FAILURE_ASSIGN_ACK_SLA_HOURS = 4;
    private static final int FAILURE_AUTO_REMIND_COOLDOWN_HOURS = 1;
    private static final int FAILURE_AUTO_REMIND_MAX_COUNT = 2;
    private static final String HISTORY_KEY_PREFIX = "ai:resume_polish:history:";
    private static final AdminAuthenticatedUser SYSTEM_GOVERNANCE_OPERATOR = AdminAuthenticatedUser.builder()
            .adminUserId(0L)
            .account("system")
            .userName("system")
            .roleCodes(Set.of("SYSTEM"))
            .permissions(Set.of())
            .build();
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
    private final CapabilityAccountService capabilityAccountService;
    private final AiResumeFailureRecordService aiResumeFailureRecordService;
    private final AiResumeNotificationDeliveryService aiResumeNotificationDeliveryService;
    private final AiResumeNotificationDispatchService aiResumeNotificationDispatchService;
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
        clearFailureNotificationEvidence(current);
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
        clearFailureNotificationEvidence(current);
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

    @Override
    public AdminAiResumeFailureItemDTO recordNotification(String failureId, AdminAiResumeFailureActionDTO action) {
        AiResumeFailureRecordDTO current = aiResumeFailureRecordService.findFailure(failureId);
        if (current == null) {
            throw new BizException(AiResumeErrorCode.HISTORY_NOT_FOUND, "AI 失败样本不存在");
        }
        String currentStatus = normalizeFailureHandlingStatus(current.getHandlingStatus());
        ensureFailureOpenForCollaboration(currentStatus);
        if (current.getAssignedAdminId() == null) {
            throw new BizException("失败样本尚未分派处理人");
        }

        String requestedStatus = resolveRequestedNotificationStatus(action);
        AiResumeFailureRecordDTO before = copyFailure(current);
        AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();
        String now = LocalDateTime.now().format(TIME_FORMATTER);

        current.setHandlingStatus(currentStatus);
        current.setHandlingNote(action == null ? null : action.getReason());
        current.setHandledByAdminId(admin.getAdminUserId());
        current.setHandledByAdminName(admin.getUserName());
        current.setHandledAt(now);
        AdminUser assignedAdmin = resolveAssignedAdminUser(current);
        AiResumeNotificationDelivery delivery;
        if ("send_failed".equals(requestedStatus)) {
            current.setNotificationStatus(requestedStatus);
            delivery = aiResumeNotificationDeliveryService.recordManualNotification(
                    current,
                    admin,
                    assignedAdmin,
                    requestedStatus,
                    action == null ? null : action.getReason()
            );
            current.setNotificationSentAt(null);
            current.setNotificationFailureReason(action == null ? null : action.getReason());
        } else {
            AiResumeNotificationDispatchResultDTO dispatchResult = aiResumeNotificationDispatchService.dispatch(
                    buildNotificationSendCommand(current, admin, assignedAdmin,
                            current.getRequestId(), "admin_dispatch", action == null ? null : action.getReason())
            );
            delivery = dispatchResult.getDelivery();
            current.setNotificationStatus(delivery == null ? "send_failed" : delivery.getSendStatus());
            current.setNotificationSentAt(formatTime(delivery == null ? null : delivery.getSentAt()));
            current.setNotificationFailureReason(delivery == null ? "notification_dispatch_missing" : delivery.getSendFailureReason());
            current.setNotificationReceiptStatus(null);
            current.setNotificationReceiptAt(null);
            current.setNotificationReceiptFailureReason(null);
            clearFailureAutoRemindSkip(current);
        }
        applyNotificationDeliverySummary(current, delivery);
        current.setHandlingNotes(appendHandlingNote(current, "record_notification", currentStatus,
                action == null ? null : action.getReason(), admin, null, null));
        aiResumeFailureRecordService.recordFailure(current);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("ai_resume_record_notification")
                .targetType("ai_resume_failure")
                .beforeSnapshot(before)
                .afterSnapshot(current)
                .extraContext(buildFailureActionContext(current,
                        action == null ? null : action.getReason(),
                        currentStatus,
                        currentStatus,
                        "record_notification"))
                .operationResult(1)
                .build());

        return buildFailureItems(List.of(current)).get(0);
    }

    @Override
    public AdminAiResumeFailureItemDTO recordNotificationReceipt(String failureId, AdminAiResumeFailureActionDTO action) {
        AiResumeFailureRecordDTO current = aiResumeFailureRecordService.findFailure(failureId);
        if (current == null) {
            throw new BizException(AiResumeErrorCode.HISTORY_NOT_FOUND, "AI 失败样本不存在");
        }
        String currentStatus = normalizeFailureHandlingStatus(current.getHandlingStatus());
        ensureFailureOpenForCollaboration(currentStatus);
        if (current.getAssignedAdminId() == null) {
            throw new BizException("失败样本尚未分派处理人");
        }
        if ("pending_send".equals(resolveFailureNotificationStatus(current))) {
            throw new BizException("当前失败样本尚未记录通知发送，不能先记回执");
        }

        String requestedStatus = resolveRequestedNotificationReceiptStatus(action);
        AiResumeFailureRecordDTO before = copyFailure(current);
        AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();
        String now = LocalDateTime.now().format(TIME_FORMATTER);

        current.setHandlingStatus(currentStatus);
        current.setHandlingNote(action == null ? null : action.getReason());
        current.setHandledByAdminId(admin.getAdminUserId());
        current.setHandledByAdminName(admin.getUserName());
        current.setHandledAt(now);
        current.setNotificationReceiptStatus(requestedStatus);
        AdminUser assignedAdmin = resolveAssignedAdminUser(current);
        AiResumeNotificationDelivery delivery = aiResumeNotificationDeliveryService.recordManualNotificationReceipt(
                current,
                admin,
                assignedAdmin,
                requestedStatus,
                action == null ? null : action.getReason()
        );
        applyNotificationDeliverySummary(current, delivery);
        current.setNotificationReceiptAt("receipt_failed".equals(requestedStatus) ? null : now);
        current.setNotificationReceiptFailureReason("receipt_failed".equals(requestedStatus) ? action == null ? null : action.getReason() : null);
        current.setHandlingNotes(appendHandlingNote(current, "record_notification_receipt", currentStatus,
                action == null ? null : action.getReason(), admin, null, null));
        aiResumeFailureRecordService.recordFailure(current);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("system")
                .operationCode("ai_resume_record_notification_receipt")
                .targetType("ai_resume_failure")
                .beforeSnapshot(before)
                .afterSnapshot(current)
                .extraContext(buildFailureActionContext(current,
                        action == null ? null : action.getReason(),
                        currentStatus,
                        currentStatus,
                        "record_notification_receipt"))
                .operationResult(1)
                .build());

        return buildFailureItems(List.of(current)).get(0);
    }

    @Override
    public AdminAiResumeGovernanceSweepResultDTO previewGovernanceSweep(AdminAiResumeGovernanceSweepRequestDTO request) {
        return sweepGovernance(request, true);
    }

    @Override
    public AdminAiResumeGovernanceSweepResultDTO executeGovernanceSweep(AdminAiResumeGovernanceSweepRequestDTO request) {
        return sweepGovernance(request, false);
    }

    private AdminAiResumeGovernanceSweepResultDTO sweepGovernance(AdminAiResumeGovernanceSweepRequestDTO request, boolean dryRun) {
        AdminAuthenticatedUser currentAdmin = adminAuthContext.getCurrentAdmin();
        AdminAuthenticatedUser operator = currentAdmin == null ? SYSTEM_GOVERNANCE_OPERATOR : currentAdmin;
        String triggerSource = currentAdmin == null ? "scheduled" : "admin";
        AdminAiResumeGovernanceSweepRequestDTO effectiveRequest = normalizeGovernanceSweepRequest(request);
        LocalDateTime evaluationTime = resolveGovernanceEvaluationTime(effectiveRequest);
        List<AiResumeFailureRecordDTO> records = loadGovernanceSweepRecords(effectiveRequest);
        List<AdminAiResumeGovernanceSweepItemDTO> items = new ArrayList<>();

        int dueCount = 0;
        int autoRemindCount = 0;
        int timeoutEscalationCount = 0;
        int executedCount = 0;
        int skippedCount = 0;
        for (AiResumeFailureRecordDTO record : records) {
            GovernanceSweepDecision decision = evaluateGovernanceSweepDecision(record, evaluationTime);
            if (decision.isDue()) {
                dueCount++;
                if ("auto_remind".equals(decision.actionType())) {
                    autoRemindCount++;
                } else if ("timeout_escalation".equals(decision.actionType())) {
                    timeoutEscalationCount++;
                }
            }

            AdminAiResumeGovernanceSweepItemDTO item;
            if (dryRun || !decision.isDue()) {
                if (!decision.isDue()) {
                    skippedCount++;
                }
                item = buildGovernanceSweepItem(record, evaluationTime, decision, null, null);
            } else if ("auto_remind".equals(decision.actionType())) {
                AiResumeFailureRecordDTO updated = executeAutoRemind(record, operator, effectiveRequest, decision);
                executedCount++;
                item = buildGovernanceSweepItem(updated, evaluationTime, decision, updated, "executed");
            } else if ("timeout_escalation".equals(decision.actionType())) {
                AiResumeFailureRecordDTO updated = executeTimeoutEscalation(record, operator, effectiveRequest, decision);
                executedCount++;
                item = buildGovernanceSweepItem(updated, evaluationTime, decision, updated, "executed");
            } else {
                skippedCount++;
                item = buildGovernanceSweepItem(record, evaluationTime, decision, null, "skipped");
            }
            items.add(item);
        }

        AdminAiResumeGovernanceSweepResultDTO dto = new AdminAiResumeGovernanceSweepResultDTO();
        dto.setDryRun(dryRun);
        dto.setRequestId(effectiveRequest.getRequestId());
        dto.setTriggerSource(triggerSource);
        dto.setOperatorName(operator.getUserName());
        dto.setEvaluatedAt(evaluationTime.format(TIME_FORMATTER));
        dto.setTotalCount(records.size());
        dto.setDueCount(dueCount);
        dto.setAutoRemindCount(autoRemindCount);
        dto.setTimeoutEscalationCount(timeoutEscalationCount);
        dto.setExecutedCount(executedCount);
        dto.setSkippedCount(skippedCount);
        dto.setItems(items);
        return dto;
    }

    private AdminAiResumeGovernanceSweepRequestDTO normalizeGovernanceSweepRequest(AdminAiResumeGovernanceSweepRequestDTO request) {
        AdminAiResumeGovernanceSweepRequestDTO effective = request == null ? new AdminAiResumeGovernanceSweepRequestDTO() : request;
        if (!StringUtils.hasText(effective.getRequestId())) {
            effective.setRequestId(buildGovernanceSweepRequestId());
        } else {
            effective.setRequestId(effective.getRequestId().trim());
        }
        return effective;
    }

    private List<AiResumeFailureRecordDTO> loadGovernanceSweepRecords(AdminAiResumeGovernanceSweepRequestDTO request) {
        List<AiResumeFailureRecordDTO> records = aiResumeFailureRecordService.listAllRecords();
        if (request != null && !safeList(request.getFailureIds()).isEmpty()) {
            Set<String> requestedIds = safeList(request.getFailureIds()).stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            records = records.stream()
                    .filter(item -> requestedIds.contains(item.getFailureId()))
                    .toList();
        }
        return records.stream()
                .limit(resolveGovernanceSweepLimit(request))
                .toList();
    }

    private String buildGovernanceSweepRequestId() {
        String timestamp = LocalDateTime.now().format(SWEEP_REQUEST_ID_TIME_FORMATTER);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "ai-governance-sweep-" + timestamp + "-" + suffix;
    }

    private int resolveGovernanceSweepLimit(AdminAiResumeGovernanceSweepRequestDTO request) {
        Integer limit = request == null ? null : request.getLimit();
        if (limit == null || limit <= 0) {
            return 20;
        }
        return Math.min(limit, 200);
    }

    private LocalDateTime resolveGovernanceEvaluationTime(AdminAiResumeGovernanceSweepRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getEvaluateAt())) {
            return LocalDateTime.now();
        }
        LocalDateTime evaluationTime = parseTime(request.getEvaluateAt());
        if (evaluationTime == null) {
            throw new BizException("evaluateAt 仅支持 yyyy-MM-dd'T'HH:mm:ss 格式");
        }
        return evaluationTime;
    }

    private GovernanceSweepDecision evaluateGovernanceSweepDecision(AiResumeFailureRecordDTO record, LocalDateTime evaluationTime) {
        String beforeCollaborationStatus = resolveFailureCollaborationStatus(record, evaluationTime);
        String beforeNotificationStatus = resolveFailureNotificationStatus(record, evaluationTime);
        String beforeNotificationReceiptStatus = resolveFailureNotificationReceiptStatus(record, evaluationTime);
        String beforeAutoRemindStage = resolveFailureAutoRemindStage(record, evaluationTime);
        String beforeSlaStatus = resolveFailureSlaStatus(record, evaluationTime);
        int beforeReminderCount = record == null || record.getReminderCount() == null ? 0 : record.getReminderCount();
        String handlingStatus = record == null ? null : normalizeFailureHandlingStatus(record.getHandlingStatus());

        if (record == null) {
            return new GovernanceSweepDecision("base", "skipped", "failure_missing",
                    beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                    beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
        }
        if (isFailureTerminal(handlingStatus)) {
            return new GovernanceSweepDecision("base", "skipped", "terminal_failure",
                    beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                    beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
        }
        if (record.getAssignedAdminId() == null) {
            return new GovernanceSweepDecision("base", "skipped", "unassigned_failure",
                    beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                    beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
        }
        if (StringUtils.hasText(record.getAssignmentAcknowledgedAt())) {
            return new GovernanceSweepDecision("base", "skipped", "already_acknowledged",
                    beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                    beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
        }
        if (StringUtils.hasText(record.getManualTakeoverAt())) {
            return new GovernanceSweepDecision("base", "skipped", "manual_takeover_already_recorded",
                    beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                    beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
        }
        if (StringUtils.hasText(record.getAutoRemindSkippedAt())) {
            return new GovernanceSweepDecision("base", "skipped", "auto_remind_skipped",
                    beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                    beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
        }
        if ("pending_send".equals(beforeNotificationStatus) || "send_failed".equals(beforeNotificationStatus)) {
            return new GovernanceSweepDecision("base", "skipped", "notification_not_ready",
                    beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                    beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
        }

        LocalDateTime deadline = parseTime(resolveFailureClaimDeadlineAt(record));
        if (deadline == null) {
            return new GovernanceSweepDecision("base", "skipped", "claim_deadline_missing",
                    beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                    beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
        }
        if (!evaluationTime.isAfter(deadline)) {
            return new GovernanceSweepDecision("base", "not_due", "within_ack_sla",
                    beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                    beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
        }

        LocalDateTime cooldownBase = resolveFailureLastGovernanceTouchTime(record);
        if (cooldownBase != null && evaluationTime.isBefore(cooldownBase.plusHours(FAILURE_AUTO_REMIND_COOLDOWN_HOURS))) {
            return new GovernanceSweepDecision("base", "not_due", "auto_remind_cooldown",
                    beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                    beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
        }

        if (beforeReminderCount >= FAILURE_AUTO_REMIND_MAX_COUNT) {
            if (!StringUtils.hasText(record.getEscalationRoleCode())) {
                return new GovernanceSweepDecision("timeout_escalation", "blocked", "escalation_role_missing",
                        beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                        beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
            }
            return new GovernanceSweepDecision("timeout_escalation", "ready", "ack_timeout_after_max_auto_remind",
                    beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                    beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
        }

        return new GovernanceSweepDecision("auto_remind", "ready", "ack_timeout_due_for_auto_remind",
                beforeCollaborationStatus, beforeNotificationStatus, beforeNotificationReceiptStatus,
                beforeAutoRemindStage, beforeSlaStatus, beforeReminderCount);
    }

    private LocalDateTime resolveFailureLastGovernanceTouchTime(AiResumeFailureRecordDTO record) {
        LocalDateTime lastRemindedAt = parseTime(record == null ? null : record.getLastRemindedAt());
        if (lastRemindedAt != null) {
            return lastRemindedAt;
        }
        return parseTime(resolveFailureNotificationSentAt(record));
    }

    private AiResumeFailureRecordDTO executeAutoRemind(AiResumeFailureRecordDTO current,
                                                       AdminAuthenticatedUser admin,
                                                       AdminAiResumeGovernanceSweepRequestDTO request,
                                                       GovernanceSweepDecision decision) {
        AiResumeFailureRecordDTO before = copyFailure(current);
        String currentStatus = normalizeFailureHandlingStatus(current.getHandlingStatus());
        String now = LocalDateTime.now().format(TIME_FORMATTER);
        String reason = resolveGovernanceSweepReason(request, "auto_remind", decision.detail());

        current.setHandlingStatus(currentStatus);
        current.setHandlingNote(reason);
        current.setHandledByAdminId(admin.getAdminUserId());
        current.setHandledByAdminName(admin.getUserName());
        current.setHandledAt(now);
        current.setReminderCount((current.getReminderCount() == null ? 0 : current.getReminderCount()) + 1);
        current.setLastRemindedByAdminId(admin.getAdminUserId());
        current.setLastRemindedByAdminName(admin.getUserName());
        current.setLastRemindedAt(now);
        AdminUser assignedAdmin = resolveAssignedAdminUser(current);
        AiResumeNotificationDispatchResultDTO dispatchResult = aiResumeNotificationDispatchService.dispatch(
                buildNotificationSendCommand(current, admin, assignedAdmin,
                        request == null ? null : request.getRequestId(), "governance_sweep", reason)
        );
        AiResumeNotificationDelivery delivery = dispatchResult.getDelivery();
        current.setNotificationStatus(delivery == null ? "send_failed" : delivery.getSendStatus());
        current.setNotificationSentAt(formatTime(delivery == null ? null : delivery.getSentAt()));
        current.setNotificationFailureReason(delivery == null ? "notification_dispatch_missing" : delivery.getSendFailureReason());
        current.setNotificationReceiptStatus(null);
        current.setNotificationReceiptAt(null);
        current.setNotificationReceiptFailureReason(null);
        applyNotificationDeliverySummary(current, delivery);
        clearFailureAutoRemindSkip(current);
        current.setHandlingNotes(appendHandlingNote(current, "auto_remind", currentStatus, reason, admin, null, null));
        aiResumeFailureRecordService.recordFailure(current);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .requestId(request.getRequestId())
                .moduleCode("system")
                .operationCode("ai_resume_auto_remind")
                .targetType("ai_resume_failure")
                .beforeSnapshot(before)
                .afterSnapshot(current)
                .extraContext(buildFailureActionContext(current, reason, currentStatus, currentStatus, "auto_remind"))
                .operationResult(1)
                .build());
        return current;
    }

    private AiResumeFailureRecordDTO executeTimeoutEscalation(AiResumeFailureRecordDTO current,
                                                              AdminAuthenticatedUser admin,
                                                              AdminAiResumeGovernanceSweepRequestDTO request,
                                                              GovernanceSweepDecision decision) {
        AiResumeFailureRecordDTO before = copyFailure(current);
        String currentStatus = normalizeFailureHandlingStatus(current.getHandlingStatus());
        String now = LocalDateTime.now().format(TIME_FORMATTER);
        String reason = resolveGovernanceSweepReason(request, "timeout_escalation", decision.detail());

        current.setHandlingStatus("escalated");
        current.setHandlingNote(reason);
        current.setHandledByAdminId(admin.getAdminUserId());
        current.setHandledByAdminName(admin.getUserName());
        current.setHandledAt(now);
        current.setHandlingNotes(appendHandlingNote(current, "timeout_escalation", "escalated", reason, admin, null, null));
        aiResumeFailureRecordService.recordFailure(current);

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .requestId(request.getRequestId())
                .moduleCode("system")
                .operationCode("ai_resume_timeout_escalation")
                .targetType("ai_resume_failure")
                .beforeSnapshot(before)
                .afterSnapshot(current)
                .extraContext(buildFailureActionContext(current, reason, currentStatus, "escalated", "timeout_escalation"))
                .operationResult(1)
                .build());
        return current;
    }

    private String resolveGovernanceSweepReason(AdminAiResumeGovernanceSweepRequestDTO request, String actionType, String detail) {
        if (request != null && StringUtils.hasText(request.getReason())) {
            return request.getReason().trim();
        }
        if ("auto_remind".equals(actionType)) {
            return "治理 sweep 自动催办：" + detail;
        }
        if ("timeout_escalation".equals(actionType)) {
            return "治理 sweep 超时升级：" + detail;
        }
        return "治理 sweep：" + detail;
    }

    private AdminAiResumeGovernanceSweepItemDTO buildGovernanceSweepItem(AiResumeFailureRecordDTO sourceRecord,
                                                                         LocalDateTime evaluationTime,
                                                                         GovernanceSweepDecision decision,
                                                                         AiResumeFailureRecordDTO resultRecord,
                                                                         String actionStatusOverride) {
        AiResumeFailureRecordDTO effectiveRecord = resultRecord == null ? sourceRecord : resultRecord;
        AdminAiResumeGovernanceSweepItemDTO item = new AdminAiResumeGovernanceSweepItemDTO();
        item.setFailureId(sourceRecord == null ? null : sourceRecord.getFailureId());
        item.setRequestId(sourceRecord == null ? null : sourceRecord.getRequestId());
        item.setAssignedAdminId(sourceRecord == null ? null : sourceRecord.getAssignedAdminId());
        item.setAssignedAdminName(sourceRecord == null ? null : sourceRecord.getAssignedAdminName());
        item.setEscalationRoleCode(sourceRecord == null ? null : sourceRecord.getEscalationRoleCode());
        item.setEscalationRoleName(sourceRecord == null ? null : sourceRecord.getEscalationRoleName());
        item.setActionType(decision.actionType());
        item.setActionStatus(StringUtils.hasText(actionStatusOverride) ? actionStatusOverride : decision.actionStatus());
        item.setDetail(decision.detail());
        item.setEvaluatedAt(evaluationTime.format(TIME_FORMATTER));
        item.setBeforeCollaborationStatus(decision.beforeCollaborationStatus());
        item.setBeforeNotificationStatus(decision.beforeNotificationStatus());
        item.setBeforeNotificationReceiptStatus(decision.beforeNotificationReceiptStatus());
        item.setBeforeAutoRemindStage(decision.beforeAutoRemindStage());
        item.setBeforeSlaStatus(decision.beforeSlaStatus());
        item.setBeforeReminderCount(decision.beforeReminderCount());
        item.setAfterHandlingStatus(effectiveRecord == null ? null : effectiveRecord.getHandlingStatus());
        item.setAfterCollaborationStatus(resolveFailureCollaborationStatus(effectiveRecord));
        item.setAfterNotificationStatus(resolveFailureNotificationStatus(effectiveRecord));
        item.setAfterNotificationReceiptStatus(resolveFailureNotificationReceiptStatus(effectiveRecord));
        item.setAfterAutoRemindStage(resolveFailureAutoRemindStage(effectiveRecord));
        item.setAfterSlaStatus(resolveFailureSlaStatus(effectiveRecord));
        item.setAfterReminderCount(effectiveRecord == null ? null : effectiveRecord.getReminderCount());
        item.setFailure(effectiveRecord == null ? null : buildFailureItems(List.of(effectiveRecord)).get(0));
        return item;
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
            item.setNotificationDeliveryId(record.getNotificationDeliveryId());
            item.setNotificationSourceType(record.getNotificationSourceType());
            item.setNotificationChannelCode(record.getNotificationChannelCode());
            item.setNotificationRecipient(record.getNotificationRecipient());
            item.setNotificationProviderCode(record.getNotificationProviderCode());
            item.setNotificationProviderMessageId(record.getNotificationProviderMessageId());
            item.setNotificationSentAt(resolveFailureNotificationSentAt(record));
            item.setNotificationFailureReason(resolveFailureNotificationFailureReason(record));
            item.setNotificationReceiptStatus(resolveFailureNotificationReceiptStatus(record));
            item.setNotificationReceiptSourceType(record.getNotificationReceiptSourceType());
            item.setNotificationReceiptAt(resolveFailureNotificationReceiptAt(record));
            item.setNotificationReceiptFailureReason(resolveFailureNotificationReceiptFailureReason(record));
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
                result.put(userId, capabilityAccountService.actorLevelInfo(userId));
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
                    dto.setPermissionStage(item.getPermissionStage());
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
        note.setNotificationStatus(record.getNotificationStatus());
        note.setNotificationDeliveryId(record.getNotificationDeliveryId());
        note.setNotificationSourceType(record.getNotificationSourceType());
        note.setNotificationChannelCode(record.getNotificationChannelCode());
        note.setNotificationRecipient(record.getNotificationRecipient());
        note.setNotificationProviderCode(record.getNotificationProviderCode());
        note.setNotificationProviderMessageId(record.getNotificationProviderMessageId());
        note.setNotificationSentAt(record.getNotificationSentAt());
        note.setNotificationFailureReason(record.getNotificationFailureReason());
        note.setNotificationReceiptStatus(record.getNotificationReceiptStatus());
        note.setNotificationReceiptSourceType(record.getNotificationReceiptSourceType());
        note.setNotificationReceiptAt(record.getNotificationReceiptAt());
        note.setNotificationReceiptFailureReason(record.getNotificationReceiptFailureReason());
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
            copy.setNotificationStatus(value.getNotificationStatus());
            copy.setNotificationDeliveryId(value.getNotificationDeliveryId());
            copy.setNotificationSourceType(value.getNotificationSourceType());
            copy.setNotificationChannelCode(value.getNotificationChannelCode());
            copy.setNotificationRecipient(value.getNotificationRecipient());
            copy.setNotificationProviderCode(value.getNotificationProviderCode());
            copy.setNotificationProviderMessageId(value.getNotificationProviderMessageId());
            copy.setNotificationSentAt(value.getNotificationSentAt());
            copy.setNotificationFailureReason(value.getNotificationFailureReason());
            copy.setNotificationReceiptStatus(value.getNotificationReceiptStatus());
            copy.setNotificationReceiptSourceType(value.getNotificationReceiptSourceType());
            copy.setNotificationReceiptAt(value.getNotificationReceiptAt());
            copy.setNotificationReceiptFailureReason(value.getNotificationReceiptFailureReason());
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
        if ("auto_remind".equals(actionType)) {
            return "ai_resume_auto_remind";
        }
        if ("manual_takeover".equals(actionType)) {
            return "ai_resume_manual_takeover";
        }
        if ("skip_auto_remind".equals(actionType)) {
            return "ai_resume_skip_auto_remind";
        }
        if ("record_notification".equals(actionType)) {
            return "ai_resume_record_notification";
        }
        if ("record_notification_receipt".equals(actionType)) {
            return "ai_resume_record_notification_receipt";
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
        if ("timeout_escalation".equals(actionType)) {
            return "ai_resume_timeout_escalation";
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

    private void clearFailureNotificationEvidence(AiResumeFailureRecordDTO record) {
        if (record == null) {
            return;
        }
        record.setNotificationStatus(null);
        record.setNotificationDeliveryId(null);
        record.setNotificationSourceType(null);
        record.setNotificationChannelCode(null);
        record.setNotificationRecipient(null);
        record.setNotificationProviderCode(null);
        record.setNotificationProviderMessageId(null);
        record.setNotificationSentAt(null);
        record.setNotificationFailureReason(null);
        record.setNotificationReceiptStatus(null);
        record.setNotificationReceiptSourceType(null);
        record.setNotificationReceiptAt(null);
        record.setNotificationReceiptFailureReason(null);
    }

    private AdminUser resolveAssignedAdminUser(AiResumeFailureRecordDTO record) {
        if (record == null || record.getAssignedAdminId() == null) {
            return null;
        }
        return adminUserService.getById(record.getAssignedAdminId());
    }

    private void applyNotificationDeliverySummary(AiResumeFailureRecordDTO record, AiResumeNotificationDelivery delivery) {
        if (record == null || delivery == null) {
            return;
        }
        record.setNotificationDeliveryId(delivery.getDeliveryId());
        record.setNotificationSourceType(delivery.getSendSourceType());
        record.setNotificationChannelCode(delivery.getChannelCode());
        record.setNotificationRecipient(resolveDeliveryRecipient(delivery));
        record.setNotificationProviderCode(delivery.getProviderCode());
        record.setNotificationProviderMessageId(delivery.getProviderMessageId());
        record.setNotificationReceiptSourceType(delivery.getReceiptSourceType());
    }

    private String resolveDeliveryRecipient(AiResumeNotificationDelivery delivery) {
        if (delivery == null) {
            return null;
        }
        if (StringUtils.hasText(delivery.getRecipientName()) && StringUtils.hasText(delivery.getRecipientPhone())) {
            return delivery.getRecipientName().trim() + " / " + delivery.getRecipientPhone().trim();
        }
        if (StringUtils.hasText(delivery.getRecipientName()) && StringUtils.hasText(delivery.getRecipientEmail())) {
            return delivery.getRecipientName().trim() + " / " + delivery.getRecipientEmail().trim();
        }
        if (StringUtils.hasText(delivery.getRecipientPhone())) {
            return delivery.getRecipientPhone().trim();
        }
        if (StringUtils.hasText(delivery.getRecipientEmail())) {
            return delivery.getRecipientEmail().trim();
        }
        return StringUtils.hasText(delivery.getRecipientName()) ? delivery.getRecipientName().trim() : null;
    }

    private void clearFailureManualTakeover(AiResumeFailureRecordDTO record) {
        if (record == null) {
            return;
        }
        record.setManualTakeoverByAdminId(null);
        record.setManualTakeoverByAdminName(null);
        record.setManualTakeoverAt(null);
    }

    private String resolveRequestedNotificationStatus(AdminAiResumeFailureActionDTO action) {
        String status = normalize(action == null ? null : action.getNotificationStatus());
        if (!StringUtils.hasText(status)) {
            return "sent";
        }
        if (!Objects.equals(status, "sent") && !Objects.equals(status, "send_failed")) {
            throw new BizException("通知结果仅支持 sent 或 send_failed");
        }
        return status;
    }

    private String resolveRequestedNotificationReceiptStatus(AdminAiResumeFailureActionDTO action) {
        String status = normalize(action == null ? null : action.getNotificationReceiptStatus());
        if (!StringUtils.hasText(status)) {
            return "delivered";
        }
        if (!Objects.equals(status, "delivered") && !Objects.equals(status, "receipt_failed")) {
            throw new BizException("通知回执结果仅支持 delivered 或 receipt_failed");
        }
        return status;
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
        return resolveFailureCollaborationStatus(record, LocalDateTime.now());
    }

    private String resolveFailureCollaborationStatus(AiResumeFailureRecordDTO record, LocalDateTime evaluationTime) {
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
        if (deadline != null && evaluationTime.isAfter(deadline)) {
            return "ack_overdue";
        }
        return "pending_ack";
    }

    private String resolveFailureNotificationStatus(AiResumeFailureRecordDTO record) {
        return resolveFailureNotificationStatus(record, LocalDateTime.now());
    }

    private String resolveFailureNotificationStatus(AiResumeFailureRecordDTO record, LocalDateTime evaluationTime) {
        if (record != null && StringUtils.hasText(record.getNotificationStatus())) {
            return record.getNotificationStatus();
        }
        String notificationSentAt = resolveFailureNotificationSentAt(record);
        if (!StringUtils.hasText(notificationSentAt)) {
            return isFailureTerminal(record == null ? null : normalizeFailureHandlingStatus(record.getHandlingStatus()))
                    ? "not_required"
                    : "pending_send";
        }
        Integer reminderCount = record == null ? null : record.getReminderCount();
        if (record != null
                && StringUtils.hasText(record.getLastRemindedAt())
                && (reminderCount == null ? 0 : reminderCount) > 0) {
            return "resent";
        }
        return "sent";
    }

    private String resolveFailureNotificationSentAt(AiResumeFailureRecordDTO record) {
        if (record == null) {
            return null;
        }
        if (StringUtils.hasText(record.getNotificationSentAt())) {
            return record.getNotificationSentAt();
        }
        if (Objects.equals(record.getNotificationStatus(), "send_failed")) {
            return null;
        }
        if (StringUtils.hasText(record.getLastRemindedAt())) {
            return record.getLastRemindedAt();
        }
        return null;
    }

    private String resolveFailureNotificationFailureReason(AiResumeFailureRecordDTO record) {
        return record == null ? null : record.getNotificationFailureReason();
    }

    private String resolveFailureNotificationReceiptStatus(AiResumeFailureRecordDTO record) {
        return resolveFailureNotificationReceiptStatus(record, LocalDateTime.now());
    }

    private String resolveFailureNotificationReceiptStatus(AiResumeFailureRecordDTO record, LocalDateTime evaluationTime) {
        if (StringUtils.hasText(record == null ? null : record.getAssignmentAcknowledgedAt())) {
            return "received";
        }
        if (record != null && StringUtils.hasText(record.getNotificationReceiptStatus())) {
            return record.getNotificationReceiptStatus();
        }
        String notificationSentAt = resolveFailureNotificationSentAt(record);
        if (!StringUtils.hasText(notificationSentAt)) {
            return isFailureTerminal(record == null ? null : normalizeFailureHandlingStatus(record.getHandlingStatus()))
                    ? "not_required"
                    : "not_sent";
        }
        LocalDateTime deadline = parseTime(resolveFailureClaimDeadlineAt(record));
        if (deadline != null && evaluationTime.isAfter(deadline)) {
            return "receipt_overdue";
        }
        return "pending_receipt";
    }

    private String resolveFailureNotificationReceiptAt(AiResumeFailureRecordDTO record) {
        if (record == null) {
            return null;
        }
        if (StringUtils.hasText(record.getAssignmentAcknowledgedAt())) {
            return record.getAssignmentAcknowledgedAt();
        }
        return record.getNotificationReceiptAt();
    }

    private String resolveFailureNotificationReceiptFailureReason(AiResumeFailureRecordDTO record) {
        return record == null ? null : record.getNotificationReceiptFailureReason();
    }

    private AiResumeNotificationSendCommand buildNotificationSendCommand(AiResumeFailureRecordDTO current,
                                                                         AdminAuthenticatedUser admin,
                                                                         AdminUser assignedAdmin,
                                                                         String requestId,
                                                                         String sendSourceType,
                                                                         String reason) {
        AiResumeNotificationSendCommand command = new AiResumeNotificationSendCommand();
        command.setFailureRecord(current);
        command.setOperator(admin);
        command.setRecipientAdmin(assignedAdmin);
        command.setRequestId(requestId);
        command.setSendSourceType(sendSourceType);
        command.setReason(reason);
        return command;
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? null : value.format(TIME_FORMATTER);
    }

    private String resolveFailureAutoRemindStage(AiResumeFailureRecordDTO record) {
        return resolveFailureAutoRemindStage(record, LocalDateTime.now());
    }

    private String resolveFailureAutoRemindStage(AiResumeFailureRecordDTO record, LocalDateTime evaluationTime) {
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
        boolean overdue = deadline != null && evaluationTime.isAfter(deadline);
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
        return resolveFailureSlaStatus(record, LocalDateTime.now());
    }

    private String resolveFailureSlaStatus(AiResumeFailureRecordDTO record, LocalDateTime evaluationTime) {
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
            evaluatedAt = evaluationTime;
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
        copy.setNotificationStatus(record.getNotificationStatus());
        copy.setNotificationDeliveryId(record.getNotificationDeliveryId());
        copy.setNotificationSourceType(record.getNotificationSourceType());
        copy.setNotificationChannelCode(record.getNotificationChannelCode());
        copy.setNotificationRecipient(record.getNotificationRecipient());
        copy.setNotificationProviderCode(record.getNotificationProviderCode());
        copy.setNotificationProviderMessageId(record.getNotificationProviderMessageId());
        copy.setNotificationSentAt(record.getNotificationSentAt());
        copy.setNotificationFailureReason(record.getNotificationFailureReason());
        copy.setNotificationReceiptStatus(record.getNotificationReceiptStatus());
        copy.setNotificationReceiptSourceType(record.getNotificationReceiptSourceType());
        copy.setNotificationReceiptAt(record.getNotificationReceiptAt());
        copy.setNotificationReceiptFailureReason(record.getNotificationReceiptFailureReason());
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
        context.put("notification_delivery_id", record.getNotificationDeliveryId());
        context.put("notification_source_type", record.getNotificationSourceType());
        context.put("notification_channel_code", record.getNotificationChannelCode());
        context.put("notification_recipient", record.getNotificationRecipient());
        context.put("notification_provider_code", record.getNotificationProviderCode());
        context.put("notification_provider_message_id", record.getNotificationProviderMessageId());
        context.put("notification_sent_at", resolveFailureNotificationSentAt(record));
        context.put("notification_failure_reason", resolveFailureNotificationFailureReason(record));
        context.put("notification_receipt_status", resolveFailureNotificationReceiptStatus(record));
        context.put("notification_receipt_source_type", record.getNotificationReceiptSourceType());
        context.put("notification_receipt_at", resolveFailureNotificationReceiptAt(record));
        context.put("notification_receipt_failure_reason", resolveFailureNotificationReceiptFailureReason(record));
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

    private record GovernanceSweepDecision(String actionType,
                                           String actionStatus,
                                           String detail,
                                           String beforeCollaborationStatus,
                                           String beforeNotificationStatus,
                                           String beforeNotificationReceiptStatus,
                                           String beforeAutoRemindStage,
                                           String beforeSlaStatus,
                                           int beforeReminderCount) {

        private boolean isDue() {
            return "ready".equals(actionStatus);
        }
    }
}
