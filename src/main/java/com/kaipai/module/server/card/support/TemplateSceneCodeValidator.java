package com.kaipai.module.server.card.support;

import com.kaipai.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.Set;

public final class TemplateSceneCodeValidator {

    private static final Set<String> ALLOWED_TEMPLATE_SCENE_CODES = Set.of(
            "classic",
            "urban",
            "costume",
            "commercial",
            "artistic"
    );

    private TemplateSceneCodeValidator() {
    }

    public static String requireAllowed(String templateSceneCode) {
        if (!StringUtils.hasText(templateSceneCode)) {
            throw new BizException("templateSceneCode 不能为空");
        }
        String normalized = templateSceneCode.trim();
        if (!ALLOWED_TEMPLATE_SCENE_CODES.contains(normalized)) {
            throw new BizException("templateSceneCode 不在当前枚举内");
        }
        return normalized;
    }

    public static String normalizeOptional(String templateSceneCode) {
        return StringUtils.hasText(templateSceneCode) ? requireAllowed(templateSceneCode) : null;
    }
}
