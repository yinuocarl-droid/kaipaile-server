package com.kaipai.service.actor.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 00-267: 演员卡风格 = 后台风格模板场景码；旧值 ancient 视为 costume。 */
class ActorCardStylesTest {

    @Test void legacyAncientNormalizesToTheCostumeTemplate() {
        assertEquals("costume", ActorCardStyles.normalize("ancient"));
        assertEquals("costume", ActorCardStyles.normalize(" ancient "));
        assertEquals("urban", ActorCardStyles.normalize("urban"));
        assertEquals("fresh", ActorCardStyles.normalize("fresh"), "无对应模板的旧值原样保留，不猜测归属");
        assertNull(ActorCardStyles.normalize("  "));
        assertNull(ActorCardStyles.normalize(null));
    }

    @Test void storedValuesAlsoMatchRowsNotYetMigrated() {
        assertEquals(List.of("costume", "ancient"), ActorCardStyles.storedValues("costume"));
        assertEquals(List.of("costume", "ancient"), ActorCardStyles.storedValues("ancient"));
        assertEquals(List.of("classic"), ActorCardStyles.storedValues("classic"));
        assertEquals(List.of(), ActorCardStyles.storedValues(""));
    }
}
