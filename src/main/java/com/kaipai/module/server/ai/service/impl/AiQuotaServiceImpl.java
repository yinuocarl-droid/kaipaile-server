package com.kaipai.module.server.ai.service.impl;

import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.ai.dto.ActorAiQuotaRespDTO;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.ai.service.AiQuotaService;
import com.kaipai.module.server.membership.service.MembershipAccountService;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AiQuotaServiceImpl implements AiQuotaService {

    private static final String QUOTA_TYPE_RESUME_POLISH = "resume_polish";
    private static final String PERIOD_TYPE_MONTHLY = "monthly";
    private static final String REDIS_KEY_PREFIX = "ai:quota:resume_polish:";

    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final MembershipAccountService membershipAccountService;

    @Override
    public ActorAiQuotaRespDTO quota(Long userId, String quotaType) {
        String normalizedType = normalizeQuotaType(quotaType);
        User user = requireUser(userId);
        int totalQuota = resolveTotalQuota(user);
        int usedCount = currentUsedCount(userId);
        return buildQuota(userId, normalizedType, totalQuota, usedCount);
    }

    @Override
    public ActorAiQuotaRespDTO consumeResumePolishQuota(Long userId) {
        User user = requireUser(userId);
        int totalQuota = resolveTotalQuota(user);
        if (totalQuota <= 0) {
            throw new BizException("当前等级暂无 AI 润色额度");
        }
        String key = redisKey(userId);
        Long next = redisTemplate.opsForValue().increment(key);
        if (next == null) {
            throw new BizException("AI 配额扣减失败");
        }
        if (next == 1L) {
            redisTemplate.expire(key, 62, TimeUnit.DAYS);
        }
        if (next > totalQuota) {
            redisTemplate.opsForValue().decrement(key);
            throw new BizException("本月 AI 润色次数已用完，邀请好友升级可获得更多次数");
        }
        return buildQuota(userId, QUOTA_TYPE_RESUME_POLISH, totalQuota, next.intValue());
    }

    private String normalizeQuotaType(String quotaType) {
        if (!QUOTA_TYPE_RESUME_POLISH.equals(quotaType)) {
            throw new BizException("暂不支持该 AI 配额类型");
        }
        return quotaType;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private int resolveTotalQuota(User user) {
        var levelInfo = membershipAccountService.actorLevelInfo(user.getUserId());
        if (levelInfo.getLevelCapability() == null || levelInfo.getLevelCapability().getAiQuotaPerMonth() == null) {
            return 0;
        }
        return levelInfo.getLevelCapability().getAiQuotaPerMonth();
    }

    private int currentUsedCount(Long userId) {
        String raw = redisTemplate.opsForValue().get(redisKey(userId));
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private ActorAiQuotaRespDTO buildQuota(Long userId, String quotaType, int totalQuota, int usedCount) {
        ActorAiQuotaRespDTO dto = new ActorAiQuotaRespDTO();
        dto.setUserId(userId);
        dto.setQuotaType(quotaType);
        dto.setTotalQuota(totalQuota);
        dto.setUsedCount(Math.max(0, usedCount));
        dto.setPeriodType(PERIOD_TYPE_MONTHLY);
        dto.setPeriodStart(LocalDate.now().withDayOfMonth(1).toString());
        return dto;
    }

    private String redisKey(Long userId) {
        LocalDate periodStart = LocalDate.now().withDayOfMonth(1);
        return REDIS_KEY_PREFIX + periodStart + ":" + userId;
    }
}
