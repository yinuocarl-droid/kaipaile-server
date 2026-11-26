package com.kaipai.service.ai.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kaipai.common.exception.BizException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisProfileImportRateLimiterTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisProfileImportRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        limiter = new RedisProfileImportRateLimiter(redis);
    }

    @Test
    void onlyARealCountOverageReturnsFalse() {
        when(values.increment(anyString())).thenReturn(11L);

        assertFalse(limiter.allow(7L, 10));

        when(values.increment(anyString())).thenReturn(2L);
        assertTrue(limiter.allow(7L, 10));
    }

    @Test
    void nullIncrementResultReturns46002InsteadOfLookingRateLimited() {
        when(values.increment(anyString())).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> limiter.allow(7L, 10));

        assertEquals(46002, error.getCode());
    }

    @Test
    void incrementFailureReturns46002InsteadOfLookingRateLimited() {
        when(values.increment(anyString())).thenThrow(new IllegalStateException("redis unavailable"));

        BizException error = assertThrows(BizException.class, () -> limiter.allow(7L, 10));

        assertEquals(46002, error.getCode());
    }

    @Test
    void firstIncrementRequiresSuccessfulExpiry() {
        when(values.increment(anyString())).thenReturn(1L);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(false);

        BizException error = assertThrows(BizException.class, () -> limiter.allow(7L, 10));

        assertEquals(46002, error.getCode());
    }

    @Test
    void expiryFailureReturns46002() {
        when(values.increment(anyString())).thenReturn(1L);
        when(redis.expire(anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        BizException error = assertThrows(BizException.class, () -> limiter.allow(7L, 10));

        assertEquals(46002, error.getCode());
    }
}
