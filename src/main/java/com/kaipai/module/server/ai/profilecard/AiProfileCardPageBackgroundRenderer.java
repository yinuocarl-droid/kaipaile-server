package com.kaipai.module.server.ai.profilecard;

import com.kaipai.common.exception.BizException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Legacy local background renderer for the retired multi-page album flow.
 * The current profile-card path generates and persists a single provider image on the task/artifact.
 */
@Deprecated(since = "Phase 5", forRemoval = false)
@Component
public class AiProfileCardPageBackgroundRenderer {

    private static final int WIDTH = 2160;
    private static final int HEIGHT = 3840;

    /**
     * Renders a placeholder page background for historical compatibility only.
     * New generation should use the provider-backed cover image path.
     */
    @Deprecated(since = "Phase 5", forRemoval = false)
    public byte[] render(String templateSceneCode, String pageType) {
        Palette palette = resolvePalette(templateSceneCode);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            graphics.setPaint(new GradientPaint(0, 0, palette.top(), 0, HEIGHT, palette.bottom()));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            paintSoftWash(graphics, palette);
            paintSubtleFibers(graphics, palette, Objects.hash(templateSceneCode, pageType));
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception error) {
            throw new BizException("AI 分享图版式背景生成失败：" + error.getMessage());
        }
    }

    private void paintSoftWash(Graphics2D graphics, Palette palette) {
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.18f));
        Path2D leftWash = new Path2D.Double();
        leftWash.moveTo(0, 240);
        leftWash.curveTo(520, 520, 280, 1220, 0, 1600);
        leftWash.lineTo(0, 240);
        graphics.setColor(palette.accent());
        graphics.fill(leftWash);

        graphics.setComposite(AlphaComposite.SrcOver.derive(0.12f));
        Path2D lowerWash = new Path2D.Double();
        lowerWash.moveTo(0, 2680);
        lowerWash.curveTo(620, 2480, 1280, 2790, WIDTH, 2460);
        lowerWash.lineTo(WIDTH, HEIGHT);
        lowerWash.lineTo(0, HEIGHT);
        lowerWash.closePath();
        graphics.setColor(palette.shadow());
        graphics.fill(lowerWash);

        graphics.setComposite(AlphaComposite.SrcOver);
    }

    private void paintSubtleFibers(Graphics2D graphics, Palette palette, int seed) {
        Random random = new Random(seed);
        graphics.setStroke(new java.awt.BasicStroke(2f));
        for (int index = 0; index < 360; index++) {
            int alpha = 12 + random.nextInt(20);
            Color color = withAlpha(index % 3 == 0 ? palette.shadow() : palette.accent(), alpha);
            graphics.setColor(color);
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            int length = 80 + random.nextInt(260);
            graphics.drawLine(x, y, Math.min(WIDTH, x + length), Math.max(0, y - random.nextInt(42)));
        }
    }

    private Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private Palette resolvePalette(String templateSceneCode) {
        String normalized = templateSceneCode == null ? "" : templateSceneCode.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("urban")) {
            return new Palette(
                    new Color(231, 235, 237),
                    new Color(210, 218, 222),
                    new Color(131, 150, 160),
                    new Color(77, 90, 100)
            );
        }
        if (normalized.contains("commercial")) {
            return new Palette(
                    new Color(248, 249, 247),
                    new Color(232, 235, 232),
                    new Color(182, 195, 205),
                    new Color(126, 136, 145)
            );
        }
        if (normalized.contains("artistic")) {
            return new Palette(
                    new Color(229, 226, 217),
                    new Color(205, 202, 191),
                    new Color(147, 139, 121),
                    new Color(86, 83, 73)
            );
        }
        return new Palette(
                new Color(252, 247, 235),
                new Color(229, 222, 207),
                new Color(205, 169, 111),
                new Color(124, 94, 62)
        );
    }

    private record Palette(Color top, Color bottom, Color accent, Color shadow) {
    }
}
