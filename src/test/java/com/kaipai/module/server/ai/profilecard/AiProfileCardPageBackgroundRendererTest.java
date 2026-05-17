package com.kaipai.module.server.ai.profilecard;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProfileCardPageBackgroundRendererTest {

    @Test
    void renderShouldCreateVerticalNeutralBackgroundPng() throws Exception {
        AiProfileCardPageBackgroundRenderer renderer = new AiProfileCardPageBackgroundRenderer();

        byte[] bytes = renderer.render("costume", "resume");

        assertTrue(bytes.length > 10000);
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image);
        assertEquals(2160, image.getWidth());
        assertEquals(3840, image.getHeight());
    }
}
