package com.kaipai.service.ai.job;

import com.kaipai.model.ai.dto.AdminAiResumeGovernanceSweepRequestDTO;
import com.kaipai.model.ai.dto.AdminAiResumeGovernanceSweepResultDTO;
import com.kaipai.service.ai.config.AiResumeGovernanceSchedulerProperties;
import com.kaipai.service.ai.AdminAiResumeGovernanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiResumeGovernanceSweepScheduler {

    private static final String LOCK_KEY = "ai:resume:governance:sweep:scheduler:lock";
    private static final DateTimeFormatter REQUEST_ID_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminAiResumeGovernanceService adminAiResumeGovernanceService;
    private final AiResumeGovernanceSchedulerProperties properties;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(
            initialDelayString = "#{@aiResumeGovernanceSchedulerProperties.initialDelay.toMillis()}",
            fixedDelayString = "#{@aiResumeGovernanceSchedulerProperties.fixedDelay.toMillis()}"
    )
    public void executeScheduledSweep() {
        if (!properties.isEnabled()) {
            return;
        }
        String lockToken = UUID.randomUUID().toString();
        boolean locked = tryAcquireLock(lockToken);
        if (!locked) {
            log.debug("skip ai governance scheduled sweep because lock is held by another runner");
            return;
        }
        try {
            AdminAiResumeGovernanceSweepRequestDTO request = new AdminAiResumeGovernanceSweepRequestDTO();
            request.setLimit(Math.max(properties.getLimit(), 1));
            request.setReason(resolveReason());
            request.setRequestId(buildRequestId());

            AdminAiResumeGovernanceSweepResultDTO result = adminAiResumeGovernanceService.executeGovernanceSweep(request);
            log.info("ai governance scheduled sweep finished requestId={}, dueCount={}, executedCount={}, skippedCount={}",
                    result.getRequestId(), result.getDueCount(), result.getExecutedCount(), result.getSkippedCount());
        } catch (Exception ex) {
            log.error("ai governance scheduled sweep failed", ex);
        } finally {
            releaseLock(lockToken);
        }
    }

    private boolean tryAcquireLock(String lockToken) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockToken, properties.getLockTtl()));
    }

    private void releaseLock(String lockToken) {
        redisTemplate.execute((RedisConnection connection) -> {
            byte[] key = LOCK_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] current = connection.stringCommands().get(key);
            byte[] expected = lockToken.getBytes(StandardCharsets.UTF_8);
            if (Arrays.equals(current, expected)) {
                connection.keyCommands().del(key);
            }
            return null;
        });
    }

    private String buildRequestId() {
        String timestamp = LocalDateTime.now().format(REQUEST_ID_TIME_FORMATTER);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "ai-governance-scheduler-" + timestamp + "-" + suffix;
    }

    private String resolveReason() {
        if (StringUtils.hasText(properties.getReason())) {
            return properties.getReason().trim();
        }
        return "AI治理定时sweep";
    }
}
