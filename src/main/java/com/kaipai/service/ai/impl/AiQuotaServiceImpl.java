package com.kaipai.service.ai.impl;

import com.kaipai.common.exception.BizException;
import com.kaipai.model.ai.dto.ActorAiQuotaRespDTO;
import com.kaipai.model.ai.dto.AiResumeErrorCode;
import com.kaipai.model.user.entity.User;
import com.kaipai.service.ai.AiQuotaService;
import com.kaipai.service.capability.CapabilityAccountService;
import com.kaipai.mapper.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AiQuotaServiceImpl implements AiQuotaService {

    private static final String QUOTA_TYPE_RESUME_POLISH = "resume_polish";
    private static final String PERIOD_TYPE_MONTHLY = "monthly";
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final CapabilityAccountService capabilityAccountService;

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
            throw new BizException(AiResumeErrorCode.QUOTA_EXHAUSTED, "当前等级暂无 AI 润色额度");
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
            throw new BizException(AiResumeErrorCode.QUOTA_EXHAUSTED, "本月 AI 润色次数已用完，邀请好友升级可获得更多次数");
        }
        return buildQuota(userId, QUOTA_TYPE_RESUME_POLISH, totalQuota, next.intValue());
    }

    private String normalizeQuotaType(String quotaType) {
        if (!QUOTA_TYPE_RESUME_POLISH.equals(quotaType)) {
            throw new BizException(AiResumeErrorCode.CONTEXT_INVALID, "暂不支持该 AI 配额类型");
        }
        return quotaType;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(AiResumeErrorCode.AUTH_REQUIRED, "未登录或登录态失效");
        }
        return user;
    }

    private int resolveTotalQuota(User user) {
        var levelInfo = capabilityAccountService.actorLevelInfo(user.getUserId());
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
        return AiQuotaRedisKeys.quotaKey(periodStart, userId);
    }
}
