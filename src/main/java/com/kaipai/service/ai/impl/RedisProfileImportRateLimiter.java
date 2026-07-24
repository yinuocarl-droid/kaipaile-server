package com.kaipai.service.ai.impl;

import com.kaipai.common.exception.BizException;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.service.ai.ProfileImportRateLimiter;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisProfileImportRateLimiter implements ProfileImportRateLimiter {
    private final StringRedisTemplate redis;

    public RedisProfileImportRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean allow(Long userId, int limit) {
        String key = "ai:profile-import:daily:" + LocalDate.now() + ":" + userId;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null || count < 1L) throw unavailable();
            if (count == 1L && !Boolean.TRUE.equals(redis.expire(key, Duration.ofDays(1)))) {
                throw unavailable();
            }
            return count <= limit;
        } catch (BizException error) {
            throw error;
        } catch (RuntimeException error) {
            throw unavailable();
        }
    }

    private BizException unavailable() {
        return ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
    }
}
