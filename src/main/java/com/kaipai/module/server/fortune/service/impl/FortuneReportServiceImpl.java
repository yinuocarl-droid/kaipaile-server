package com.kaipai.module.server.fortune.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.fortune.dto.FortuneReadingBlockRespDTO;
import com.kaipai.module.model.fortune.dto.FortuneReportRespDTO;
import com.kaipai.module.model.fortune.entity.FortuneReport;
import com.kaipai.module.model.fortune.dto.ZiweiProfileRespDTO;
import com.kaipai.module.server.fortune.mapper.FortuneReportMapper;
import com.kaipai.module.server.fortune.service.FortuneReportService;
import com.kaipai.module.server.card.service.ActorCardConfigService;
import com.kaipai.module.server.membership.service.MembershipAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FortuneReportServiceImpl extends ServiceImpl<FortuneReportMapper, FortuneReport> implements FortuneReportService {

    private final ObjectMapper objectMapper;
    private final MembershipAccountService membershipAccountService;
    private final ActorCardConfigService actorCardConfigService;

    @Override
    public FortuneReportRespDTO currentReport(Long userId) {
        FortuneReport report = latestReport(userId);
        if (report == null) {
            throw new BizException("命理报告暂未生成完成");
        }

        FortuneReportRespDTO dto = new FortuneReportRespDTO();
        dto.setMonth(report.getReportMonth() == null ? null : report.getReportMonth().toString().substring(0, 7));
        dto.setZodiacAnimal(report.getZodiacAnimal());
        dto.setZodiacFortune(parseReadingBlock(report.getZodiacFortune()));
        dto.setConstellation(report.getConstellation());
        dto.setConstellationFortune(parseReadingBlock(report.getConstellationFortune()));
        dto.setZiweiStar(report.getZiweiStar());
        dto.setZiweiProfile(parseZiweiProfile(report.getZiweiProfile()));
        dto.setLuckyColor(report.getLuckyColor());
        dto.setLuckyColorName(report.getLuckyColorName());
        dto.setLuckyColorInterpretation(report.getLuckyColorInterpretation());
        dto.setLuckyNumber(report.getLuckyNumber());
        dto.setLuckyNumberInterpretation(report.getLuckyNumberInterpretation());
        return dto;
    }

    @Override
    public void applyLuckyColor(Long userId, String sceneKey) {
        var levelInfo = membershipAccountService.actorLevelInfo(userId);
        if (levelInfo.getLevelCapability() == null || !Boolean.TRUE.equals(levelInfo.getLevelCapability().getCanUseLuckyColor())) {
            throw new BizException("Lv5 后才可把幸运色应用到名片");
        }
        FortuneReport report = latestReport(userId);
        if (report == null || !StringUtils.hasText(report.getLuckyColor())) {
            throw new BizException("命理报告暂未生成完成");
        }
        actorCardConfigService.applyLuckyColor(userId, sceneKey, report.getLuckyColor());
    }

    private FortuneReport latestReport(Long userId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<FortuneReport>()
                .eq(FortuneReport::getUserId, userId)
                .orderByDesc(FortuneReport::getReportMonth)
                .orderByDesc(FortuneReport::getCreateTime)
                .last("limit 1"));
    }

    private FortuneReadingBlockRespDTO parseReadingBlock(String raw) {
        FortuneReadingBlockRespDTO dto = new FortuneReadingBlockRespDTO();
        if (!StringUtils.hasText(raw)) {
            return dto;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            dto.setKeyword(asText(parsed.get("keyword")));
            Object readings = parsed.get("readings");
            if (readings instanceof List<?> list) {
                dto.setReadings(list.stream().map(this::asText).filter(StringUtils::hasText).toList());
            } else if (StringUtils.hasText(raw)) {
                dto.setReadings(List.of(raw.trim()));
            }
            if (!StringUtils.hasText(dto.getKeyword()) && !dto.getReadings().isEmpty()) {
                dto.setKeyword(dto.getReadings().get(0));
            }
            return dto;
        } catch (Exception ignored) {
            dto.setKeyword(raw.trim());
            dto.setReadings(List.of(raw.trim()));
            return dto;
        }
    }

    private ZiweiProfileRespDTO parseZiweiProfile(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            ZiweiProfileRespDTO dto = new ZiweiProfileRespDTO();
            dto.setTrait(asText(parsed.get("trait")));
            dto.setMonthlyAdvice(asText(parsed.get("monthlyAdvice")));
            if (!StringUtils.hasText(dto.getTrait()) && !StringUtils.hasText(dto.getMonthlyAdvice())) {
                dto.setTrait(raw.trim());
            }
            return dto;
        } catch (Exception ignored) {
            ZiweiProfileRespDTO dto = new ZiweiProfileRespDTO();
            dto.setTrait(raw.trim());
            return dto;
        }
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }
}
