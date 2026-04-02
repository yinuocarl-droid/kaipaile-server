package com.kaipai.module.server.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.ai.dto.AdminAiResumeFailureItemDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeHistoryItemDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeHistoryQueryDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeOverviewDTO;
import com.kaipai.module.model.ai.dto.AdminAiResumeQuotaUserDTO;
import com.kaipai.module.model.ai.dto.AiResumeErrorCode;
import com.kaipai.module.model.ai.dto.AiResumeFailureRecordDTO;
import com.kaipai.module.model.ai.dto.AiResumeHistoryItemDTO;
import com.kaipai.module.model.level.dto.ActorLevelInfoRespDTO;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.ai.service.AdminAiResumeGovernanceService;
import com.kaipai.module.server.ai.service.AiResumeFailureRecordService;
import com.kaipai.module.server.membership.service.MembershipAccountService;
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
    private static final String HISTORY_KEY_PREFIX = "ai:resume_polish:history:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final MembershipAccountService membershipAccountService;
    private final AiResumeFailureRecordService aiResumeFailureRecordService;

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
    public List<AdminAiResumeFailureItemDTO> failures() {
        return buildFailureItems(aiResumeFailureRecordService.recentFailures(20));
    }

    @Override
    public List<AdminAiResumeFailureItemDTO> sensitiveHits() {
        return buildFailureItems(aiResumeFailureRecordService.recentSensitiveHits(20));
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
            item.setCreatedAt(record.getCreatedAt());
            items.add(item);
        }
        return items;
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

    private record HistoryRecord(Long userId, AiResumeHistoryItemDTO history) {
    }

    private record QuotaUsageRecord(Long userId, int usedCount) {
    }

    private record UserContext(User user, ActorProfile actorProfile) {
    }
}
