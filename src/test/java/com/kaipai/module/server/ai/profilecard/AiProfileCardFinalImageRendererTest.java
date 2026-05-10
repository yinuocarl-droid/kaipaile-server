package com.kaipai.module.server.ai.profilecard;

import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.actor.dto.ActorWorkExperienceDTO;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProfileCardFinalImageRendererTest {

    @Test
    void renderToPngBytesShouldCreateFullSizeProfileCard() throws Exception {
        AiProfileCardFinalImageRenderer renderer = new AiProfileCardFinalImageRenderer(null);
        BufferedImage background = new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = background.createGraphics();
        try {
            g.setPaint(new GradientPaint(0, 0, new Color(230, 221, 204), 0, 1920, new Color(80, 102, 74)));
            g.fillRect(0, 0, 1080, 1920);
        } finally {
            g.dispose();
        }

        ActorProfileDTO profile = new ActorProfileDTO();
        profile.setName("许金铭");
        profile.setHeight(168);
        profile.setWeight(45);
        profile.setCity("横店");
        profile.setHairStyle("长发");
        profile.setBodyType("古装气质");
        profile.setIntro("热爱表演，镜头前真诚而有力量，能够快速进入状态。");
        profile.setSkillTypes(List.of("威亚", "游泳", "国画", "舞蹈", "模特"));
        ActorWorkExperienceDTO work = new ActorWorkExperienceDTO();
        work.setProjectName("长安风华");
        work.setRoleName("赵清妍");
        work.setShootDate("2024");
        profile.setWorkExperiences(List.of(work));

        byte[] bytes = renderer.renderToPngBytes(
                background,
                profile,
                "costume",
                "costume_actor_profile_full_card",
                "aipf_test",
                18L);

        assertTrue(bytes.length > 100_000);
        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(rendered);
        assertEquals(AiProfileCardFinalImageRenderer.WIDTH, rendered.getWidth());
        assertEquals(AiProfileCardFinalImageRenderer.HEIGHT, rendered.getHeight());
    }
}
