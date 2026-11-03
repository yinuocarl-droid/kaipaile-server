package com.kaipai.service.actor.support;

import com.kaipai.common.exception.BizException;

/** Validation shared by actor profile writes and explicit card snapshots. */
public final class HeroBadgeText {
    private HeroBadgeText() { }

    /** Normalize one explicitly present string; null/presence semantics belong to callers. */
    public static String normalize(String value) {
        if (value == null || hasForbiddenCharacters(value)) throw new BizException("首图胶囊文案仅支持无换行的纯文本");
        String normalized = value.trim();
        if (normalized.length() > 40) throw new BizException("首图胶囊文案最多40个字符");
        return normalized;
    }

    /** Do not permit HTML delimiters, controls, or Unicode line separators. */
    public static boolean hasForbiddenCharacters(String value) {
        return value.codePoints().anyMatch(c -> c == '<' || c == '>' || Character.isISOControl(c)
                || c == 0x2028 || c == 0x2029);
    }
}
