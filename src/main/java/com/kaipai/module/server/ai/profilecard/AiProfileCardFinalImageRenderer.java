package com.kaipai.module.server.ai.profilecard;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.kaipai.common.exception.BizException;
import com.kaipai.module.model.actor.dto.ActorPhotoCategoriesDTO;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.actor.dto.ActorWorkExperienceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiProfileCardFinalImageRenderer {

    static final int WIDTH = 2160;
    static final int HEIGHT = 3840;

    private static final Duration DOWNLOAD_CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration DOWNLOAD_READ_TIMEOUT = Duration.ofSeconds(45);
    private static final int MAX_OPTIONAL_IMAGE_BYTES = 12 * 1024 * 1024;
    private static final String FALLBACK_TEXT = "待完善";

    private final AiGeneratedImageStorage generatedImageStorage;

    public String renderAndUpload(String backgroundImageUrl,
                                  ActorProfileDTO profile,
                                  String templateSceneCode,
                                  String styleCode,
                                  String taskId,
                                  Long shareCardId) {
        BufferedImage background = downloadRequiredImage(backgroundImageUrl);
        byte[] bytes = renderToPngBytes(background, profile, templateSceneCode, styleCode, taskId, shareCardId);
        return generatedImageStorage.upload(bytes, "image/png", "ai-profile-card-final");
    }

    byte[] renderToPngBytes(BufferedImage background,
                            ActorProfileDTO profile,
                            String templateSceneCode,
                            String styleCode,
                            String taskId,
                            Long shareCardId) {
        if (profile == null) {
            throw new BizException("缺少演员档案，无法生成 AI 分享资料图");
        }

        Theme theme = resolveTheme(templateSceneCode, styleCode);
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            configureGraphics(g);
            drawBackground(g, background, theme);
            drawHero(g, profile, theme);
            drawProfileFacts(g, profile, theme);
            drawSkills(g, profile, theme);
            drawWorks(g, profile, theme);
            drawMorePhotos(g, profile, theme);
            drawAbout(g, profile, theme);
            drawStats(g, profile, theme);
            drawFooter(g, profile, theme, taskId, shareCardId);
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(canvas, "png", output);
            return output.toByteArray();
        } catch (Exception error) {
            throw new BizException("AI 分享资料图渲染失败：" + error.getMessage());
        }
    }

    private void configureGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private void drawBackground(Graphics2D g, BufferedImage background, Theme theme) {
        if (background != null) {
            drawImageCover(g, background, 0, 0, WIDTH, HEIGHT);
        } else {
            g.setPaint(new GradientPaint(0, 0, theme.backgroundTop(), 0, HEIGHT, theme.backgroundBottom()));
            g.fillRect(0, 0, WIDTH, HEIGHT);
        }

        g.setPaint(new GradientPaint(0, 860, new Color(255, 252, 244, 42), 0, 1650, theme.paper()));
        g.fillRect(0, 760, WIDTH, 1180);
        g.setColor(theme.paper());
        g.fillRect(0, 1370, WIDTH, 2170);
        g.setColor(theme.footer());
        g.fillRect(0, 3540, WIDTH, 300);
        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 70), 980, 0, new Color(255, 255, 255, 0)));
        g.fillRect(0, 0, 1180, 1420);
    }

    private void drawHero(Graphics2D g, ActorProfileDTO profile, Theme theme) {
        g.setColor(theme.muted());
        g.setFont(font(Font.PLAIN, 34));
        g.drawString("演员个人资料", 150, 180);
        g.setFont(font(Font.PLAIN, 22));
        g.drawString("AI ACTOR PROFILE", 155, 218);

        g.setColor(theme.ink());
        g.setFont(serifFont(Font.BOLD, 152));
        drawStringWithFallback(g, defaultText(profile.getName(), "演员"), 150, 410);

        fillRound(g, 650, 272, 78, 118, 36, theme.accent());
        g.setColor(Color.WHITE);
        g.setFont(font(Font.BOLD, 35));
        drawCenteredString(g, "演员", 650, 300, 78, 52);

        g.setColor(theme.ink());
        g.setFont(font(Font.BOLD, 40));
        drawStringWithFallback(g, buildHeroCopy(profile), 150, 560);

        String skillLine = buildSkillLine(profile);
        if (StringUtils.hasText(skillLine)) {
            fillRound(g, 150, 635, 700, 78, 22, theme.highlight());
            g.setColor(theme.ink());
            g.setFont(font(Font.BOLD, 34));
            drawStringWithFallback(g, skillLine, 186, 686);
        }
    }

    private void drawProfileFacts(Graphics2D g, ActorProfileDTO profile, Theme theme) {
        int x = 135;
        int y = 770;
        int w = 1000;
        int h = 570;
        fillRound(g, x, y, w, h, 34, theme.panel());
        strokeRound(g, x, y, w, h, 34, theme.line(), 3f);

        drawFact(g, theme, "身高", formatMeasure(profile.getHeight(), "cm"), x + 90, y + 112);
        drawFact(g, theme, "体重", formatMeasure(profile.getWeight(), "kg"), x + 570, y + 112);
        drawFact(g, theme, "常驻地", defaultText(profile.getCity(), FALLBACK_TEXT), x + 90, y + 275);
        drawFact(g, theme, "发型", defaultText(profile.getHairStyle(), FALLBACK_TEXT), x + 570, y + 275);
        drawFact(g, theme, "形象", defaultText(profile.getBodyType(), "可塑性强"), x + 90, y + 438);
        drawFact(g, theme, "技能", buildShortSkills(profile), x + 570, y + 438);
    }

    private void drawFact(Graphics2D g, Theme theme, String label, String value, int x, int y) {
        fillCircle(g, x, y - 52, 76, theme.iconBg());
        g.setColor(theme.ink());
        g.setFont(font(Font.BOLD, 34));
        drawStringWithFallback(g, label, x + 100, y - 28);
        g.setColor(theme.text());
        g.setFont(font(Font.PLAIN, 32));
        drawWrappedText(g, value, x + 100, y + 18, 330, 42, 2, font(Font.PLAIN, 32), theme.text());
    }

    private void drawSkills(Graphics2D g, ActorProfileDTO profile, Theme theme) {
        int x = 120;
        int y = 1450;
        int w = 850;
        int h = 760;
        drawSectionPanel(g, theme, x, y, w, h, "技能特长", "SKILLS");

        List<String> skills = safeList(profile.getSkillTypes()).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(5)
                .toList();
        if (skills.isEmpty()) {
            skills = List.of("镜头表现", "角色塑造", "台词理解");
        }

        int rowY = y + 155;
        for (String skill : skills) {
            fillCircle(g, x + 80, rowY - 24, 72, theme.iconBg());
            g.setColor(theme.ink());
            g.setFont(font(Font.BOLD, 36));
            drawStringWithFallback(g, skill, x + 150, rowY - 30);
            g.setColor(theme.text());
            g.setFont(font(Font.PLAIN, 28));
            drawWrappedText(g, describeSkill(skill), x + 150, rowY + 12, w - 220, 38, 1, font(Font.PLAIN, 28), theme.text());
            rowY += 112;
        }
    }

    private void drawWorks(Graphics2D g, ActorProfileDTO profile, Theme theme) {
        int x = 1080;
        int y = 1450;
        int w = 930;
        int h = 760;
        drawSectionPanel(g, theme, x, y, w, h, "代表作品", "WORKS");

        List<ActorWorkExperienceDTO> works = safeList(profile.getWorkExperiences()).stream()
                .filter(item -> item != null && (StringUtils.hasText(item.getProjectName()) || StringUtils.hasText(item.getRoleName())))
                .limit(3)
                .toList();
        if (works.isEmpty()) {
            works = List.of(new ActorWorkExperienceDTO(), new ActorWorkExperienceDTO(), new ActorWorkExperienceDTO());
        }

        int rowY = y + 160;
        for (ActorWorkExperienceDTO work : works) {
            String photoUrl = firstText(safeList(work.getPhotos()));
            BufferedImage photo = downloadOptionalImage(photoUrl);
            if (photo != null) {
                drawImageCoverRounded(g, photo, x + 45, rowY - 80, 230, 150, 18);
            } else {
                fillRound(g, x + 45, rowY - 80, 230, 150, 18, theme.iconBg());
                g.setColor(theme.muted());
                g.setFont(font(Font.BOLD, 28));
                drawCenteredString(g, "作品", x + 45, rowY - 80, 230, 150);
            }

            g.setColor(theme.ink());
            g.setFont(font(Font.BOLD, 34));
            drawWrappedText(g, formatWorkTitle(work), x + 310, rowY - 54, 540, 42, 1, font(Font.BOLD, 34), theme.ink());
            g.setColor(theme.text());
            g.setFont(font(Font.PLAIN, 28));
            drawWrappedText(g, formatWorkMeta(work), x + 310, rowY + 2, 540, 38, 2, font(Font.PLAIN, 28), theme.text());
            rowY += 190;
        }
    }

    private void drawMorePhotos(Graphics2D g, ActorProfileDTO profile, Theme theme) {
        int x = 120;
        int y = 2340;
        int w = 1920;
        drawSectionHeader(g, theme, x, y, w, "更多形象", "MORE PHOTOS");

        List<String> photos = collectDisplayPhotos(profile).stream().limit(6).toList();
        int photoX = x;
        int photoY = y + 90;
        int photoW = 286;
        int photoH = 360;
        for (int i = 0; i < 6; i++) {
            BufferedImage photo = i < photos.size() ? downloadOptionalImage(photos.get(i)) : null;
            if (photo != null) {
                drawImageCoverRounded(g, photo, photoX, photoY, photoW, photoH, 20);
            } else {
                fillRound(g, photoX, photoY, photoW, photoH, 20, theme.panel());
                strokeRound(g, photoX, photoY, photoW, photoH, 20, theme.line(), 2f);
                g.setColor(theme.muted());
                g.setFont(font(Font.BOLD, 30));
                drawCenteredString(g, "形象照", photoX, photoY, photoW, photoH);
            }
            photoX += 326;
        }
    }

    private void drawAbout(Graphics2D g, ActorProfileDTO profile, Theme theme) {
        int x = 120;
        int y = 2860;
        int w = 1920;
        drawSectionHeader(g, theme, x, y, w, "个人简介", "ABOUT ME");
        String intro = defaultText(profile.getIntro(), "热爱表演，镜头前稳定自然，能够快速进入状态，适应不同类型的角色挑战。");
        drawWrappedText(g, intro, x + 5, y + 120, 1620, 52, 4, font(Font.PLAIN, 34), theme.text());
    }

    private void drawStats(Graphics2D g, ActorProfileDTO profile, Theme theme) {
        int x = 120;
        int y = 3220;
        int w = 1920;
        int h = 220;
        fillRound(g, x, y, w, h, 20, theme.stats());

        drawStat(g, theme, x, y, w / 4, "作品", String.valueOf(safeList(profile.getWorkExperiences()).size()) + "+");
        drawStat(g, theme, x + w / 4, y, w / 4, "技能", String.valueOf(safeList(profile.getSkillTypes()).size()) + "+");
        drawStat(g, theme, x + w / 2, y, w / 4, "形象", String.valueOf(collectDisplayPhotos(profile).size()) + "+");
        drawStat(g, theme, x + w * 3 / 4, y, w / 4, "资料", profile.getIsCertified() != null && profile.getIsCertified() ? "已认证" : "已完善");
    }

    private void drawStat(Graphics2D g, Theme theme, int x, int y, int w, String label, String value) {
        g.setColor(theme.ink());
        g.setFont(font(Font.BOLD, 54));
        drawCenteredString(g, value, x, y + 54, w, 62);
        g.setColor(theme.text());
        g.setFont(font(Font.PLAIN, 30));
        drawCenteredString(g, label, x, y + 128, w, 42);
    }

    private void drawFooter(Graphics2D g,
                            ActorProfileDTO profile,
                            Theme theme,
                            String taskId,
                            Long shareCardId) {
        g.setColor(theme.footer());
        g.fillRect(0, 3540, WIDTH, 300);
        g.setColor(new Color(255, 255, 255, 220));
        g.setFont(font(Font.BOLD, 36));
        drawStringWithFallback(g, "联系方式", 440, 3658);
        g.setFont(font(Font.PLAIN, 30));
        drawStringWithFallback(g, "进入页面申请查看", 440, 3712);

        g.setFont(font(Font.BOLD, 36));
        drawStringWithFallback(g, "视频简历", 870, 3658);
        g.setFont(font(Font.PLAIN, 30));
        drawStringWithFallback(g, StringUtils.hasText(profile.getVideoUrl()) ? "已上传，可在详情页播放" : "暂未上传", 870, 3712);

        BufferedImage qr = createQrImage(buildShareQrContent(taskId, shareCardId), 180);
        g.setColor(Color.WHITE);
        g.fillRoundRect(1450, 3590, 210, 210, 18, 18);
        g.drawImage(qr, 1465, 3605, 180, 180, null);
        g.setColor(new Color(255, 255, 255, 230));
        g.setFont(font(Font.BOLD, 32));
        drawStringWithFallback(g, "扫码查看", 1700, 3654);
        g.setFont(font(Font.PLAIN, 30));
        drawStringWithFallback(g, "AI 分享详情", 1700, 3710);
    }

    private void drawSectionPanel(Graphics2D g, Theme theme, int x, int y, int w, int h, String title, String english) {
        fillRound(g, x, y, w, h, 24, theme.panelLight());
        strokeRound(g, x, y, w, h, 24, theme.line(), 2f);
        drawSectionHeader(g, theme, x + 30, y + 36, w - 60, title, english);
    }

    private void drawSectionHeader(Graphics2D g, Theme theme, int x, int y, int w, String title, String english) {
        g.setColor(theme.ink());
        g.setFont(serifFont(Font.BOLD, 48));
        drawStringWithFallback(g, title, x, y + 44);
        g.setColor(theme.muted());
        g.setFont(font(Font.PLAIN, 22));
        g.drawString(english, x + 210, y + 42);
        g.setColor(theme.line());
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x + 330, y + 32, x + w, y + 32);
    }

    private List<String> collectDisplayPhotos(ActorProfileDTO profile) {
        LinkedHashSet<String> photos = new LinkedHashSet<>();
        addText(photos, profile.getAvatar());
        ActorPhotoCategoriesDTO categories = profile.getPhotoCategories();
        if (categories != null) {
            addTexts(photos, categories.getPortrait());
            addTexts(photos, categories.getLifestyle());
            addTexts(photos, categories.getProduction());
        }
        addTexts(photos, profile.getPhotos());
        for (ActorWorkExperienceDTO work : safeList(profile.getWorkExperiences())) {
            if (work != null) {
                addTexts(photos, work.getPhotos());
            }
        }
        return new ArrayList<>(photos);
    }

    private void addTexts(LinkedHashSet<String> result, List<String> values) {
        for (String value : safeList(values)) {
            addText(result, value);
        }
    }

    private void addText(LinkedHashSet<String> result, String value) {
        if (StringUtils.hasText(value)) {
            result.add(value.trim());
        }
    }

    private String buildHeroCopy(ActorProfileDTO profile) {
        List<String> parts = new ArrayList<>();
        if (profile.getHeight() != null) {
            parts.add(profile.getHeight() + "cm");
        }
        if (StringUtils.hasText(profile.getCity())) {
            parts.add(profile.getCity().trim());
        }
        if (!safeList(profile.getSkillTypes()).isEmpty()) {
            parts.add("技能多元");
        }
        return parts.isEmpty() ? "镜头表现稳定，角色适配度高" : String.join(" · ", parts);
    }

    private String buildSkillLine(ActorProfileDTO profile) {
        List<String> skills = safeList(profile.getSkillTypes()).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(3)
                .toList();
        if (skills.isEmpty()) {
            return "可塑性强";
        }
        return "擅长 " + String.join(" / ", skills);
    }

    private String buildShortSkills(ActorProfileDTO profile) {
        List<String> skills = safeList(profile.getSkillTypes()).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(3)
                .toList();
        return skills.isEmpty() ? FALLBACK_TEXT : String.join("、", skills);
    }

    private String describeSkill(String skill) {
        if (!StringUtils.hasText(skill)) {
            return "表演基础稳定，镜头适应度高";
        }
        return switch (skill.trim()) {
            case "威亚" -> "高空吊威亚经验丰富";
            case "游泳" -> "水性良好，动作自然";
            case "国画" -> "擅长写意表达与古风气质";
            case "舞蹈" -> "肢体协调，节奏感好";
            case "模特" -> "镜头表现与平面拍摄经验";
            default -> skill.trim() + "能力稳定，可配合角色需求";
        };
    }

    private String formatWorkTitle(ActorWorkExperienceDTO work) {
        String project = defaultText(work.getProjectName(), "代表作品");
        return "《" + project.replace("《", "").replace("》", "") + "》";
    }

    private String formatWorkMeta(ActorWorkExperienceDTO work) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(work.getRoleName())) {
            parts.add("饰 " + work.getRoleName().trim());
        }
        if (StringUtils.hasText(work.getShootDate())) {
            parts.add(work.getShootDate().trim());
        }
        if (StringUtils.hasText(work.getDescription())) {
            parts.add(work.getDescription().trim());
        }
        return parts.isEmpty() ? "拍摄经历待完善" : String.join(" · ", parts);
    }

    private String formatMeasure(Integer value, String unit) {
        return value == null || value <= 0 ? FALLBACK_TEXT : value + unit;
    }

    private BufferedImage createQrImage(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF1F2A22 : 0xFFFFFFFF);
                }
            }
            return image;
        } catch (Exception error) {
            throw new BizException("AI 分享资料图二维码生成失败：" + error.getMessage());
        }
    }

    private String buildShareQrContent(String taskId, Long shareCardId) {
        return "/pkg-card/ai-profile-card-detail/index?shareCardId="
                + (shareCardId == null ? "" : shareCardId)
                + "&shared=1&taskId="
                + defaultText(taskId, "");
    }

    private BufferedImage downloadRequiredImage(String imageUrl) {
        BufferedImage image = downloadOptionalImage(imageUrl);
        if (image == null) {
            throw new BizException("AI 底图下载失败，无法合成资料长图");
        }
        return image;
    }

    private BufferedImage downloadOptionalImage(String imageUrl) {
        if (!StringUtils.hasText(imageUrl) || !imageUrl.trim().startsWith("http")) {
            return null;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(DOWNLOAD_CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl.trim()))
                    .timeout(DOWNLOAD_READ_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || bytes == null || bytes.length == 0 || bytes.length > MAX_OPTIONAL_IMAGE_BYTES) {
                return null;
            }
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception error) {
            log.debug("AI profile card optional image download failed: {}", imageUrl, error);
            return null;
        }
    }

    private void drawImageCover(Graphics2D g, BufferedImage image, int x, int y, int w, int h) {
        double scale = Math.max(w / (double) image.getWidth(), h / (double) image.getHeight());
        int sw = (int) Math.round(image.getWidth() * scale);
        int sh = (int) Math.round(image.getHeight() * scale);
        int sx = x + (w - sw) / 2;
        int sy = y + (h - sh) / 2;
        g.drawImage(image, sx, sy, sw, sh, null);
    }

    private void drawImageCoverRounded(Graphics2D g, BufferedImage image, int x, int y, int w, int h, int radius) {
        Shape oldClip = g.getClip();
        RoundRectangle2D clip = new RoundRectangle2D.Float(x, y, w, h, radius, radius);
        g.setClip(clip);
        drawImageCover(g, image, x, y, w, h);
        g.setClip(oldClip);
    }

    private void fillRound(Graphics2D g, int x, int y, int w, int h, int radius, Color color) {
        g.setColor(color);
        g.fillRoundRect(x, y, w, h, radius, radius);
    }

    private void strokeRound(Graphics2D g, int x, int y, int w, int h, int radius, Color color, float width) {
        g.setColor(color);
        g.setStroke(new BasicStroke(width));
        g.drawRoundRect(x, y, w, h, radius, radius);
    }

    private void fillCircle(Graphics2D g, int centerX, int centerY, int size, Color color) {
        g.setColor(color);
        g.fillOval(centerX - size / 2, centerY - size / 2, size, size);
    }

    private void drawWrappedText(Graphics2D g,
                                 String text,
                                 int x,
                                 int y,
                                 int width,
                                 int lineHeight,
                                 int maxLines,
                                 Font font,
                                 Color color) {
        g.setFont(font);
        g.setColor(color);
        List<String> lines = wrapText(defaultText(text, ""), font, width, maxLines);
        int currentY = y;
        for (String line : lines) {
            drawStringWithFallback(g, line, x, currentY);
            currentY += lineHeight;
        }
    }

    private List<String> wrapText(String text, Font font, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scratch.createGraphics();
        try {
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            String normalized = text.replace("\r", "").replace("\n", " ").trim();
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < normalized.length(); offset++) {
                String next = normalized.substring(offset, offset + 1);
                String candidate = line + next;
                if (metrics.stringWidth(candidate) > width && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(next.trim());
                    if (lines.size() == maxLines) {
                        break;
                    }
                } else {
                    line.append(next);
                }
            }
            if (lines.size() < maxLines && !line.isEmpty()) {
                lines.add(line.toString());
            }
            if (lines.size() > maxLines) {
                return lines.subList(0, maxLines);
            }
            if (lines.size() == maxLines && normalized.length() > String.join("", lines).length()) {
                int last = lines.size() - 1;
                lines.set(last, ellipsize(lines.get(last), metrics, width));
            }
            return lines;
        } finally {
            g.dispose();
        }
    }

    private String ellipsize(String value, FontMetrics metrics, int width) {
        String result = value;
        while (result.length() > 1 && metrics.stringWidth(result + "...") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private void drawStringWithFallback(Graphics2D g, String value, int x, int y) {
        g.drawString(defaultText(value, ""), x, y);
    }

    private void drawCenteredString(Graphics2D g, String value, int x, int y, int w, int h) {
        FontMetrics metrics = g.getFontMetrics();
        int tx = x + Math.max(0, (w - metrics.stringWidth(value)) / 2);
        int ty = y + Math.max(metrics.getAscent(), (h - metrics.getHeight()) / 2 + metrics.getAscent());
        g.drawString(value, tx, ty);
    }

    private Font font(int style, int size) {
        return new Font("SansSerif", style, size);
    }

    private Font serifFont(int style, int size) {
        return new Font("Serif", style, size);
    }

    private Theme resolveTheme(String templateSceneCode, String styleCode) {
        if ("urban".equals(templateSceneCode)) {
            return new Theme(
                    new Color(236, 241, 245),
                    new Color(223, 229, 234),
                    new Color(249, 251, 252, 235),
                    new Color(246, 249, 251, 232),
                    new Color(34, 42, 52),
                    new Color(68, 78, 90),
                    new Color(109, 124, 140),
                    new Color(58, 85, 116),
                    new Color(206, 222, 235, 225),
                    new Color(222, 230, 237, 220),
                    new Color(40, 55, 70),
                    new Color(230, 234, 238, 130),
                    new Color(36, 54, 68)
            );
        }
        if ("commercial".equals(templateSceneCode)) {
            return new Theme(
                    new Color(248, 249, 250),
                    new Color(233, 236, 241),
                    new Color(255, 255, 255, 238),
                    new Color(250, 250, 250, 236),
                    new Color(30, 31, 35),
                    new Color(75, 78, 84),
                    new Color(124, 128, 136),
                    new Color(176, 145, 86),
                    new Color(240, 228, 205, 230),
                    new Color(235, 238, 242, 220),
                    new Color(45, 48, 54),
                    new Color(230, 226, 214, 130),
                    new Color(48, 56, 62)
            );
        }
        return new Theme(
                new Color(246, 241, 229),
                new Color(232, 224, 207),
                new Color(253, 249, 238, 235),
                new Color(250, 246, 235, 232),
                new Color(35, 28, 21),
                new Color(86, 80, 70),
                new Color(143, 132, 113),
                new Color(148, 50, 44),
                new Color(230, 198, 126, 230),
                new Color(230, 223, 207, 230),
                new Color(78, 95, 70),
                new Color(176, 168, 132, 155),
                new Color(43, 72, 51)
        );
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String defaultText(String value) {
        return defaultText(value, "");
    }

    private String firstText(List<String> values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record Theme(
            Color backgroundTop,
            Color backgroundBottom,
            Color paper,
            Color panel,
            Color ink,
            Color text,
            Color muted,
            Color accent,
            Color highlight,
            Color iconBg,
            Color footer,
            Color stats,
            Color line
    ) {
        Color panelLight() {
            return new Color(panel.getRed(), panel.getGreen(), panel.getBlue(), 190);
        }
    }
}
