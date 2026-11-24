package com.kaipai.service.actor.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 00-267: 演员卡风格词表 = 后台「风格模板」场景码（card_scene_template.template_scene_code）。
 * 旧版小程序写死的 ancient 对应模板 costume（名称「古风」），读写两侧都按别名归一；
 * 无对应模板的旧值（如 fresh）原样保留，不猜测归属。
 */
public final class ActorCardStyles {

    private static final Map<String, String> LEGACY_ALIASES = Map.of("ancient", "costume");

    private ActorCardStyles() {
    }

    public static String normalize(String style) {
        if (!StringUtils.hasText(style)) {
            return null;
        }
        String trimmed = style.trim();
        return LEGACY_ALIASES.getOrDefault(trimmed, trimmed);
    }

    /** 查询时需要匹配的库内取值：当前值 + 迁移前的旧别名，保证迁移与发版顺序无关。 */
    public static List<String> storedValues(String style) {
        String normalized = normalize(style);
        if (normalized == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>(List.of(normalized));
        LEGACY_ALIASES.forEach((legacy, current) -> {
            if (current.equals(normalized)) {
                values.add(legacy);
            }
        });
        return values;
    }
}
