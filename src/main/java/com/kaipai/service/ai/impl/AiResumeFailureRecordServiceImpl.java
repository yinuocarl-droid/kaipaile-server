package com.kaipai.service.ai.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.ai.dto.AiResumeErrorCode;
import com.kaipai.model.ai.dto.AiResumeFailureRecordDTO;
import com.kaipai.service.ai.AiResumeFailureRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiResumeFailureRecordServiceImpl implements AiResumeFailureRecordService {

    private static final int MAX_RECORD_COUNT = 100;
    private static final TypeReference<List<AiResumeFailureRecordDTO>> FAILURE_LIST_TYPE = new TypeReference<>() {
    };
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void recordFailure(AiResumeFailureRecordDTO record) {
        if (record == null) {
            return;
        }
        List<AiResumeFailureRecordDTO> records = loadAll();
        records.removeIf(item -> StringUtils.hasText(record.getFailureId()) && record.getFailureId().equals(item.getFailureId()));
        records.add(0, record);
        if (records.size() > MAX_RECORD_COUNT) {
            records = new ArrayList<>(records.subList(0, MAX_RECORD_COUNT));
        }
        saveAll(records);
    }

    @Override
    public AiResumeFailureRecordDTO findFailure(String failureId) {
        if (!StringUtils.hasText(failureId)) {
            return null;
        }
        return loadAll().stream()
                .filter(item -> failureId.trim().equals(item.getFailureId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<AiResumeFailureRecordDTO> listAllRecords() {
        return loadAll();
    }

    @Override
    public List<AiResumeFailureRecordDTO> recentFailures(int limit) {
        return limit(loadAll(), limit);
    }

    @Override
    public List<AiResumeFailureRecordDTO> recentSensitiveHits(int limit) {
        return limit(loadAll().stream()
                .filter(item -> item.getErrorCode() != null && item.getErrorCode() == AiResumeErrorCode.CONTENT_BLOCKED)
                .toList(), limit);
    }

    private List<AiResumeFailureRecordDTO> loadAll() {
        String raw = redisTemplate.opsForValue().get(AiResumeFailureRedisKeys.recordsKey());
        if (!StringUtils.hasText(raw)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(raw, FAILURE_LIST_TYPE)).stream()
                    .sorted(Comparator.comparing(this::parseTime, Comparator.nullsLast(LocalDateTime::compareTo)).reversed()
                            .thenComparing(item -> defaultString(item.getFailureId()), Comparator.reverseOrder()))
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        } catch (Exception error) {
            throw new BizException(AiResumeErrorCode.RESPONSE_UNPARSABLE, "AI 失败样本读取失败");
        }
    }

    private void saveAll(List<AiResumeFailureRecordDTO> records) {
        try {
            redisTemplate.opsForValue().set(AiResumeFailureRedisKeys.recordsKey(), objectMapper.writeValueAsString(records));
        } catch (Exception error) {
            throw new BizException(AiResumeErrorCode.RESPONSE_UNPARSABLE, "AI 失败样本写入失败");
        }
    }

    private List<AiResumeFailureRecordDTO> limit(List<AiResumeFailureRecordDTO> records, int limit) {
        if (limit <= 0 || records.size() <= limit) {
            return new ArrayList<>(records);
        }
        return new ArrayList<>(records.subList(0, limit));
    }

    private LocalDateTime parseTime(AiResumeFailureRecordDTO record) {
        if (record == null || !StringUtils.hasText(record.getCreatedAt())) {
            return null;
        }
        try {
            return LocalDateTime.parse(record.getCreatedAt().trim(), TIME_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
