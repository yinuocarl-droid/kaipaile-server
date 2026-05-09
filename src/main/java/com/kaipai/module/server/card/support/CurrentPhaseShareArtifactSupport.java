package com.kaipai.module.server.card.support;

import com.kaipai.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.List;

public final class CurrentPhaseShareArtifactSupport {

    public static final String MINI_PROGRAM_CARD = "miniProgramCard";
    public static final String POSTER = "poster";
    public static final List<String> CURRENT_PHASE_ARTIFACTS = List.of(MINI_PROGRAM_CARD, POSTER);

    private CurrentPhaseShareArtifactSupport() {
    }

    public static String requirePreferredArtifact(String value) {
        return requireArtifactType(value, "preferredArtifact");
    }

    public static String requireArtifactType(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(fieldName + " 缺失");
        }
        String normalized = value.trim();
        if (!CURRENT_PHASE_ARTIFACTS.contains(normalized)) {
            throw new BizException(fieldName + " 不是当前阶段分享产物");
        }
        return normalized;
    }
}



