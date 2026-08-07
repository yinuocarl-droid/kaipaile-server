package com.kaipai.service.actor.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ActorWorkDeduplicationSupportTest {

    @Test
    void normalizationUsesNfkcLowercaseAndLettersOrDigitsOnly() {
        assertEquals("abc123演员", ActorWorkDeduplicationSupport.normalizeName(
                "《Ａb-C_１２３ 演员》"));
    }

    @Test
    void dedupeKeyIsStableSha256OfNormalizedProjectAndRole() {
        String key = ActorWorkDeduplicationSupport.dedupeKey(
                "《ＡＢＣ１２３演员》", " 女 主 ");

        assertEquals(
                "1eff000b7599cbfb0fb0be937c39b95a468bac62b096dbf61a6aad1250d96386",
                key);
        assertEquals(key, ActorWorkDeduplicationSupport.dedupeKey(
                "abc123演员", "女主"));
    }

    @Test
    void differentNormalizedRoleProducesDifferentKey() {
        assertNotEquals(
                ActorWorkDeduplicationSupport.dedupeKey("同一作品", "女主"),
                ActorWorkDeduplicationSupport.dedupeKey("同一作品", "女二"));
    }
}
