package com.kaipai.module.server.ai.profilecard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.module.model.actor.dto.ActorPhotoCategoriesDTO;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.actor.dto.ActorWorkExperienceDTO;
import com.kaipai.module.server.ai.provider.AiProfileImageProvider;
import com.kaipai.module.server.ai.provider.AiProfileImageProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiProfileCardPromptAgent {

    private static final int DESIGN_CANVAS_WIDTH = 750;
    private static final int DESIGN_CANVAS_HEIGHT = 1334;
    private static final int PROVIDER_CANVAS_WIDTH = 2160;
    private static final int PROVIDER_CANVAS_HEIGHT = 3840;
    private static final String FULL_BLEED_BACKGROUND_POLICY = "full-bleed edge-to-edge background layer only; no visible frame, border, paper sheet edge, card outline, document page, scroll edge, poster mat, boxed background, corner bracket, corner ornament, or enclosing decorative box";
    private static final String TEXT_FREE_BACKGROUND_POLICY = "no typography anywhere: no Chinese characters, English letters, numbers, captions, labels, AI-generated disclosure, signature, seal text, calligraphy, poster title, placeholder text, watermark, logo, QR code, or UI words";

    private final ObjectMapper objectMapper;
    private final AiProfileImageProviderRegistry providerRegistry;

    public AiProfileCardProviderDescriptor resolveProvider(String providerCode) {
        AiProfileImageProvider provider = providerRegistry.resolve(providerCode);
        return new AiProfileCardProviderDescriptor(provider.providerCode(), provider.modelCode());
    }

    public AiProfileCardGeneration generate(ActorProfileDTO profile,
                                            String taskId,
                                            String providerCode,
                                            String templateSceneCode,
                                            String styleCode,
                                            String sourceImageUrl) {
        return generateInternal(profile, taskId, providerCode, templateSceneCode, styleCode, sourceImageUrl);
    }

    private AiProfileCardGeneration generateInternal(ActorProfileDTO profile,
                                                     String taskId,
                                                     String providerCode,
                                                     String templateSceneCode,
                                                     String styleCode,
                                                     String sourceImageUrl) {
        AiProfileImageProvider provider = providerRegistry.resolve(providerCode);
        String effectiveSourceImageUrl = resolveProviderSourceImageUrl(sourceImageUrl);
        AiProfileCardPrompt prompt = build(profile, templateSceneCode, styleCode, effectiveSourceImageUrl, provider.modelCode());
        AiProfileImageGenerationResult imageResult = provider.generate(new AiProfileImageGenerationRequest(
                taskId,
                provider.modelCode(),
                templateSceneCode,
                styleCode,
                effectiveSourceImageUrl,
                prompt.promptText(),
                prompt.negativePrompt(),
                prompt.promptJson()
        ));
        return new AiProfileCardGeneration(provider.providerCode(), provider.modelCode(), prompt, imageResult);
    }

    private AiProfileCardPrompt build(ActorProfileDTO profile,
                                      String templateSceneCode,
                                      String styleCode,
                                      String sourceImageUrl,
                                      String modelCode) {
        String resolvedStyleCode = StringUtils.hasText(styleCode) ? styleCode.trim() : templateSceneCode;
        StyleBrief style = resolveStyle(templateSceneCode, resolvedStyleCode);
        AiProfileCardThemeResolver.Theme flowTheme = AiProfileCardThemeResolver.resolve(templateSceneCode, resolvedStyleCode);
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("task", "image_to_image_actor_profile_card_single_cover_background");
        brief.put("modelCode", modelCode);
        brief.put("templateSceneCode", templateSceneCode);
        brief.put("styleCode", resolvedStyleCode);
        brief.put("promptLocale", "zh-CN");
        brief.put("canvas", Map.of(
                "ratio", "9:16 vertical",
                "targetSize", "2160x3840",
                "designCanvas", designCanvas(),
                "providerCanvas", providerCanvas(),
                "coordinatePolicy", "750x1334 is the authoritative mini-program design coordinate system; scale every fixed region proportionally to 2160x3840 provider pixels",
                "renderIntent", "text-free visual background asset for mini program native actor detail rendering",
                "layoutPreset", style.layoutPreset()
        ));
        brief.put("singleCover", Map.of(
                "pageType", "cover",
                "role", "single generated text-free cover background for the first screen; all later profile sections use deterministic native content flow",
                "composition", "actor identity background plate only; bottom and outer edges must naturally settle into the fixed theme background color",
                "subjectPolicy", style.subjectBox(),
                "sourceImageMode", sourceImageMode(sourceImageUrl)
        ));
        brief.put("flowTheme", Map.of(
                "backgroundColor", flowTheme.backgroundColor(),
                "surfaceColor", flowTheme.surfaceColor(),
                "surfaceStrongColor", flowTheme.surfaceStrongColor(),
                "accentColor", flowTheme.accentColor(),
                "textColor", flowTheme.textColor(),
                "mutedTextColor", flowTheme.mutedTextColor(),
                "borderColor", flowTheme.borderColor(),
                "usage", "mini program uses this fixed theme background below the cover so long content can extend without generated-image seams"
        ));
        brief.put("referenceQuality", Map.of(
                "benchmark", style.referenceBenchmark(),
                "qualityBar", style.qualityBar(),
                "importantConstraint", "match the reference quality and atmosphere, but leave all final profile structure to deterministic mini-program rendering",
                "layoutCompliance", "quiet render-safe zones are mandatory in every style; do not draw hard business panels, final section titles, rows, thumbnails or UI components that frontend content must align to"
        ));
        brief.put("backgroundFramePolicy", FULL_BLEED_BACKGROUND_POLICY);
        brief.put("textFreePolicy", TEXT_FREE_BACKGROUND_POLICY);
        brief.put("fixedLayout", buildFixedLayout(style, flowTheme, sourceImageUrl));
        brief.put("moduleAesthetics", style.moduleAesthetics());
        brief.put("profileSignals", buildProfileSignals(profile));
        brief.put("style", Map.of(
                "title", style.title(),
                "visualDirection", style.visualDirection(),
                "palette", style.palette(),
                "lighting", style.lighting(),
                "wardrobe", style.wardrobe(),
                "layoutPreset", style.layoutPreset(),
                "textTheme", style.textTheme(),
                "panelTheme", style.panelTheme()
        ));
        List<String> qualityChecklist = new ArrayList<>();
        qualityChecklist.add("portrait identity is consistent with source image");
        qualityChecklist.add("hands, eyes, hairline, clothing edges are clean");
        qualityChecklist.add("cover bottom and outer edges naturally transition into the fixed theme background color " + flowTheme.backgroundColor());
        qualityChecklist.addAll(List.of(
                TEXT_FREE_BACKGROUND_POLICY,
                "single cover role is followed: the generated asset is only the first-screen background",
                "all fixed layout regions remain open for deterministic mini-program component rendering",
                style.subjectBox(),
                "lower native content sections stay calm, low contrast, and readable",
                "style-specific texture and visual details are premium but never compete with native foreground panels",
                "background stays full-bleed from edge to edge with no frame, border, card shell, page edge or corner ornament"
        ));
        brief.put("qualityChecklist", qualityChecklist);

        String promptJson = writeJson(brief);
        String promptText = buildPromptText(profile, style, templateSceneCode, resolvedStyleCode, sourceImageUrl, flowTheme);
        String negativePrompt = String.join(", ",
                "readable text",
                "Chinese characters",
                "Chinese text",
                "可识别汉字",
                "中文字符",
                "English letters",
                "英文字母",
                "numbers",
                "数字",
                "typography",
                "caption",
                "subtitle",
                "poster title",
                "海报标题",
                "姓名文字",
                "phone number",
                "QR code",
                "watermark",
                "图片由AI生成",
                "AI生成",
                "AI generated",
                "AI GENERATED SHARE",
                "brand logo",
                "signature",
                "seal text",
                "印章文字",
                "书法字",
                "extra face",
                "distorted face",
                "deformed hands",
                "low resolution",
                "over-smoothed plastic skin",
                "cropped head",
                "subject outside hero-right layout box",
                "busy native content regions",
                "low-contrast blocks behind native text regions",
                "visible frame",
                "outer border",
                "paper sheet edge",
                "card outline",
                "document page",
                "scroll edge",
                "poster mat",
                "boxed background",
                "corner bracket",
                "corner ornament",
                "decorative border",
                "hard information card frames",
                "bordered lower native content modules",
                "drawn thumbnail frames",
                "drawn video player",
                "full-bleed photo covering information modules",
                "fake UI labels",
                "fake QR code",
                "random readable calligraphy",
                "filled profile text",
                "dense decorations covering component regions",
                "cheap fantasy costume",
                "overly modern gradient poster"
        );
        return new AiProfileCardPrompt(promptJson, promptText, negativePrompt);
    }

    private String resolveProviderSourceImageUrl(String sourceImageUrl) {
        if (!StringUtils.hasText(sourceImageUrl)) {
            return "";
        }
        return sourceImageUrl.trim();
    }

    private String sourceImageMode(String sourceImageUrl) {
        return StringUtils.hasText(sourceImageUrl) ? "identity_reference" : "none";
    }

    private String buildPromptText(ActorProfileDTO profile,
                                   StyleBrief style,
                                   String templateSceneCode,
                                   String styleCode,
                                   String sourceImageUrl,
                                   AiProfileCardThemeResolver.Theme flowTheme) {
        return """
                生成一张 9:16 全幅无字演员详情页背景底图，输出 2160x3840。
                %s
                构图：演员位于右侧，左侧保持干净、低细节、无字符的纹理背景；封面底部和外侧边缘自然过渡到固定主题背景色 %s，方便下方资料内容继续延展。
                页面职责：只提供第一屏视觉背景底图；姓名、资料、照片、视频入口和后续内容全部由小程序原生组件渲染，图片内不要预留、书写或模拟任何文字区域。
                风格：%s
                背景：%s
                人物气质参考（仅用于人物外观，不得写入画面）：%s
                安全要求：背景必须全幅铺满；全图禁止出现任何可读字符或疑似字符，包括中文、英文、数字、标题、姓名、海报字、书法字、印章字、签名、AI生成/图片由AI生成、Logo、水印、标签、二维码、联系方式、假 UI 或任何前景组件。
                Plain background image only, no typography, no captions, no watermark, no logo.
                """.formatted(
                sourceReferenceInstruction(sourceImageUrl),
                flowTheme.backgroundColor(),
                chineseStyleHint(templateSceneCode, styleCode, style),
                chineseBackgroundHint(templateSceneCode, styleCode, style.backgroundMaterial()),
                buildChineseProfileSignals(profile)
        );
    }

    private String sourceReferenceInstruction(String sourceImageUrl) {
        if (!StringUtils.hasText(sourceImageUrl)) {
            return "当前没有可用身份参考图时，按封面人物气质生成，但不要加入任何文字、数字、水印、Logo、标签、二维码或多余前景组件。";
        }
        return "参考图1是用户源图，只用于人物身份与自然气质参考，保留脸型、年龄感、发型方向和身体比例，不要复制或生成背景文字、标签、水印或版式。";
    }

    private String chineseStyleHint(String templateSceneCode, String styleCode, StyleBrief style) {
        String normalized = ((styleCode == null ? "" : styleCode) + " " + (templateSceneCode == null ? "" : templateSceneCode))
                .toLowerCase(Locale.ROOT);
        if (normalized.contains("costume")) {
            return "古风电影感，暖象牙和墨绿为主，丝绸衣料、薄雾江南、木桥与竹影作为背景气氛，整体克制而高级。";
        }
        if (normalized.contains("urban")) {
            return "都市时装感，深灰冷调，柔和城市或影棚深度，克制霓虹边光，整体干净利落。";
        }
        if (normalized.contains("classic")) {
            return "经典棚拍感，暖灰和米白为主，真实柔光，稳重干净，带一点胶片质感。";
        }
        if (normalized.contains("commercial")) {
            return "商业棚拍感，明亮中性，清爽软盒光，现代广告气质，画面保持简洁。";
        }
        if (normalized.contains("artistic")) {
            return "艺术电影感，画廊氛围、戏剧性阴影、低饱和石墨与橄榄调，表达克制。";
        }
        return StringUtils.hasText(style.title())
                ? style.title() + "；保持真实、克制、干净的演员详情页背景底图气质。"
                : "高级演员详情页背景底图，真实、克制、干净。";
    }

    private String chineseBackgroundHint(String templateSceneCode, String styleCode, String background) {
        String normalized = ((styleCode == null ? "" : styleCode) + " " + (templateSceneCode == null ? "" : templateSceneCode))
                .toLowerCase(Locale.ROOT);
        if (normalized.contains("costume")) {
            return "暖象牙色的墨色纹理、薄雾山水和低饱和暖红抽象点缀，背景要和固定主题底色自然融合。";
        }
        if (normalized.contains("urban")) {
            return "深灰、钢蓝和冷白的层次，带柔和城市或影棚深度，避免厚重纸感和过多装饰。";
        }
        if (normalized.contains("classic")) {
            return "暖灰、米白和淡香槟的柔和层次，带轻微胶片颗粒，背景要安静、自然、耐看。";
        }
        if (normalized.contains("commercial")) {
            return "干净的白灰和浅香槟层次，保持明亮、通透、低噪点。";
        }
        if (normalized.contains("artistic")) {
            return "画廊感的纹理墙面、克制阴影和低饱和色块，背景只保留气氛，不要装饰性文字。";
        }
        if (StringUtils.hasText(background)) {
            return background.trim();
        }
        return "低细节、全幅铺开的中性色背景，只保留稳定氛围。";
    }

    private String buildChineseProfileSignals(ActorProfileDTO profile) {
        List<String> parts = new ArrayList<>();
        addChinesePart(parts, "性别", profile.getGender());
        addChinesePart(parts, "年龄", profile.getAge() == null ? null : String.valueOf(profile.getAge()));
        addChinesePart(parts, "身高", profile.getHeight() == null ? null : profile.getHeight() + "cm");
        addChinesePart(parts, "体重", profile.getWeight() == null ? null : profile.getWeight() + "kg");
        addChinesePart(parts, "城市", profile.getCity());
        addChinesePart(parts, "体型", profile.getBodyType());
        addChinesePart(parts, "发型", profile.getHairStyle());
        if (!safeList(profile.getSkillTypes()).isEmpty()) {
            parts.add("技能=" + String.join("、", safeList(profile.getSkillTypes()).stream().limit(6).toList()));
        }
        if (!safeList(profile.getLanguages()).isEmpty()) {
            parts.add("语言=" + String.join("、", safeList(profile.getLanguages()).stream().limit(4).toList()));
        }
        return parts.isEmpty() ? "专业演员气质" : String.join("；", parts);
    }

    private void addChinesePart(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "=" + value.trim());
        }
    }

    private Map<String, Object> buildFixedLayout(StyleBrief style,
                                                 AiProfileCardThemeResolver.Theme flowTheme,
                                                 String sourceImageUrl) {
        Map<String, Object> fixedLayout = new LinkedHashMap<>();
        fixedLayout.put("layoutPreset", style.layoutPreset());
        fixedLayout.put("pageNo", 1);
        fixedLayout.put("pageType", "cover");
        fixedLayout.put("pageRole", "single generated cover background only; all later content is native flow on fixed theme background");
        fixedLayout.put("textTheme", style.textTheme());
        fixedLayout.put("panelTheme", style.panelTheme());
        fixedLayout.put("designCanvas", designCanvas());
        fixedLayout.put("providerCanvas", providerCanvas());
        fixedLayout.put("coordinatePolicy", "design coordinates are the single source of truth; provider coordinates are scaled descriptions only");
        fixedLayout.put("primaryReferenceSlot", "reference image #1 is the actor identity source; preserve facial identity and natural proportions");
        fixedLayout.put("sourceImageMode", sourceImageMode(sourceImageUrl));
        fixedLayout.put("subjectBox", style.subjectBox());
        fixedLayout.put("identitySafeArea", style.identitySafeArea());
        fixedLayout.put("safeSurfaceTone", style.safeSurfaceTone());
        fixedLayout.put("flowBackgroundColor", flowTheme.backgroundColor());
        fixedLayout.put("backgroundFramePolicy", FULL_BLEED_BACKGROUND_POLICY);
        fixedLayout.put("textFreePolicy", TEXT_FREE_BACKGROUND_POLICY);
        fixedLayout.put("regions", style.layoutRegions());
        fixedLayout.put("background", style.backgroundMaterial());
        fixedLayout.put("finalTextPolicy", "do not render any final business text, Chinese characters, English letters, numbers, labels, captions, signatures, seal text, watermarks, AI-generated disclosure, rows, thumbnails, video controls, QR code, phone, contact UI, or fake app components");
        return fixedLayout;
    }

    private Map<String, Object> buildProfileSignals(ActorProfileDTO profile) {
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("gender", defaultText(profile.getGender()));
        signals.put("age", profile.getAge() == null ? 0 : profile.getAge());
        signals.put("height", profile.getHeight() == null ? 0 : profile.getHeight());
        signals.put("weight", profile.getWeight() == null ? 0 : profile.getWeight());
        signals.put("city", defaultText(profile.getCity()));
        signals.put("bodyType", defaultText(profile.getBodyType()));
        signals.put("hairStyle", defaultText(profile.getHairStyle()));
        signals.put("skills", safeList(profile.getSkillTypes()).stream().limit(8).toList());
        signals.put("languages", safeList(profile.getLanguages()).stream().limit(4).toList());
        signals.put("recentRoles", safeList(profile.getWorkExperiences()).stream()
                .limit(3)
                .map(this::experienceSummary)
                .filter(StringUtils::hasText)
                .toList());
        signals.put("sourcePhotoGroups", buildPhotoGroupSummary(profile.getPhotoCategories()));
        return signals;
    }

    private String buildReadableProfileSignals(ActorProfileDTO profile) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "gender", profile.getGender());
        addPart(parts, "age", profile.getAge() == null ? null : String.valueOf(profile.getAge()));
        addPart(parts, "height", profile.getHeight() == null ? null : profile.getHeight() + "cm");
        addPart(parts, "city", profile.getCity());
        addPart(parts, "body type", profile.getBodyType());
        addPart(parts, "hair", profile.getHairStyle());
        if (!safeList(profile.getSkillTypes()).isEmpty()) {
            parts.add("skills=" + String.join("/", safeList(profile.getSkillTypes()).stream().limit(6).toList()));
        }
        return parts.isEmpty() ? "professional actor portrait mood" : String.join("; ", parts);
    }

    private Map<String, Integer> buildPhotoGroupSummary(ActorPhotoCategoriesDTO categories) {
        if (categories == null) {
            return Map.of("portrait", 0, "lifestyle", 0, "production", 0);
        }
        return Map.of(
                "portrait", safeList(categories.getPortrait()).size(),
                "lifestyle", safeList(categories.getLifestyle()).size(),
                "production", safeList(categories.getProduction()).size()
        );
    }

    private String experienceSummary(ActorWorkExperienceDTO experience) {
        if (experience == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        addPart(parts, "project", experience.getProjectName());
        addPart(parts, "role", experience.getRoleName());
        return String.join(" ", parts);
    }

    private void addPart(List<String> parts, String key, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(key + "=" + value.trim());
        }
    }

    private StyleBrief resolveStyle(String templateSceneCode, String styleCode) {
        if ("costume_actor_profile_full_card".equals(styleCode)) {
            return styleBrief(
                    "古风演员背景底图",
                    "premium Chinese period actor full-bleed background layer: right-side realistic actor portrait in elegant Han/Tang costume, warm ivory ink-wash atmosphere, misty Jiangnan mountains and bridge, subtle pavilion and bamboo silhouettes, soft abstract warm-red motifs, calm lower render-safe matte surfaces, no visible frame, no border, no card shell, no readable text",
                    "warm ivory atmosphere, dark ink green-black, antique gold linework, muted cinnabar accent, pale jade-grey washes, soft tea-stained matte texture",
                    "soft cinematic daylight, gentle rim light on hair and robe, translucent ink-wash haze, low contrast inside native render-safe zones, crisp face detail",
                    "period-drama robe silhouette, layered silk gauze fabric, understated embroidery, elegant hair ornament or hairpin if natural, refined and realistic rather than fantasy",
                    "costume_profile_v3",
                    "paper-dark",
                    "period-paper",
                    "premium period actor full-bleed background with refined ink-wash depth, realistic portrait, and calm lower safe zones without any enclosing frame",
                    "commercial casting finish, crisp facial realism, restrained antique-gold atmosphere, elegant empty surfaces, no cheap fantasy graphics, no page or card edge",
                    "hero right side, x=1120-2050, y=120-1420; face center near x=1580,y=520; upper body must stay inside this box; costume silhouette may overlap softly into the center but never cover the left overlay-safe area",
                    "hero left side, x=120-1080, y=120-1320 must remain clean warm ink-wash empty overlay space",
                    "warm low-detail ink-wash matte safe surfaces for deterministic native panels",
                    "warm ivory full-bleed ink-wash texture, abstract warm-red motifs, misty Jiangnan mountains, bridge, garden architecture and bamboo silhouettes only as background material, no sheet border or page edge",
                    periodModuleAesthetics()
            );
        }
        return switch (templateSceneCode) {
            case "costume" -> styleBrief(
                    "古风演员背景底图",
                    "cinematic Chinese period-drama actor full-bleed background layer, elegant Han/Tang inspired wardrobe, refined fabric texture, ink-wash atmospheric depth, palace corridor or misty garden background, premium realistic portrait, calm warm lower render-safe surfaces, no visible frame, no border, no page edge, no fantasy exaggeration",
                    "warm ivory, dark ink, warm red, antique gold, jade green",
                    "soft directional key light, gentle rim light on hair and shoulders, calm lower-section lighting",
                    "period-drama robe silhouette, layered fabric, understated embroidery",
                    "costume_profile_v3",
                    "paper-dark",
                    "period-paper",
                    "premium period actor full-bleed background with realistic face, warm ink-wash texture, and calm render-safe lower zones without any enclosing frame",
                    "commercial casting finish with refined period-drama mood, clean face detail, low-noise background texture, no card shell",
                    "hero right side, x=1120-2050, y=120-1420; face center near x=1580,y=520; robe may overlap softly but not cover the left overlay-safe zones",
                    "hero left side, x=120-1080, y=120-1320 must remain clean warm ink-wash empty overlay space",
                    "warm low-detail ink-wash matte safe surfaces for deterministic native panels",
                    "warm ivory full-bleed ink-wash texture, misty period architecture, bridge, bamboo and abstract warm-red motifs without readable characters, no paper sheet edge",
                    periodModuleAesthetics()
            );
            case "urban" -> styleBrief(
                    "都市演员背景底图",
                    "modern cinematic actor background layer, quiet city or studio hero scene, polished fashion editorial tone, confident natural expression, realistic face detail, dark low-detail render-safe lower regions compatible with native glass panels",
                    "charcoal, steel blue, porcelain white, restrained neon accent, soft grey",
                    "large softbox key light with cool rim light, dim but readable lower safe zones, no bright parchment surfaces",
                    "modern fitted coat or clean fashion styling",
                    "urban_profile_v3",
                    "cinema-light",
                    "cinema-glass",
                    "premium dark cinematic casting profile background with editorial portrait, controlled charcoal safe zones, and no beige paper modules",
                    "high-end fashion editorial finish, crisp realistic face detail, restrained city/studio atmosphere, clean dark surfaces for native glass panels",
                    "hero right side, x=1080-2050, y=120-1500; face center near x=1580,y=560; hair and coat must not cover the left overlay-safe area",
                    "hero left side, x=100-1060, y=120-1320 must remain low-detail dark gradient or soft studio haze with enough contrast for light native text",
                    "dark charcoal low-detail safe surfaces for deterministic glass panels and light native text",
                    "controlled city/studio depth, soft smoke or bokeh, charcoal gradients, restrained blue rim light; no parchment, no古风 scenery, no framed document look",
                    List.of(
                            "dark cinematic background zones must remain low detail behind every foreground panel",
                            "portrait can be editorial but must not cover business slots",
                            "subtle city or studio depth is allowed only as background texture",
                            "avoid parchment, antique borders, paper cards, UI rows, chips, thumbnails and video-player shapes",
                            "visual texture should support dark glass deterministic foreground panels"
                    )
            );
            case "classic" -> styleBrief(
                    "经典演员背景底图",
                    "timeless film-still actor full-bleed background layer, warm studio hero backdrop, analog cinema texture, elegant facial lighting, professional casting atmosphere, clean lower render-safe matte surfaces, no visible frame, no border, no document page",
                    "warm grey, sepia brown, ivory, muted black, champagne",
                    "classic three-point portrait lighting, soft falloff, readable lower safe-zone lighting",
                    "simple tailored neutral wardrobe",
                    "classic_profile_v3",
                    "paper-dark",
                    "paper",
                    "premium classic actor full-bleed background with warm studio texture, realistic portrait, and clean light lower safe zones without any enclosing frame",
                    "timeless film-still finish, crisp facial realism, restrained warm matte surfaces, no cheap graphic effects, no page or card edge",
                    "hero right side, x=1120-2050, y=120-1420; face center near x=1580,y=520; upper body must not cover the left overlay-safe area",
                    "hero left side, x=120-1080, y=120-1320 must remain warm low-detail studio empty space",
                    "warm low-detail studio matte safe surfaces for deterministic native panels",
                    "warm studio texture, soft analog film grain, subtle neutral matte surfaces, restrained atmosphere without readable text, no border or page edge",
                    List.of(
                            "warm ivory or champagne low-detail continuous surfaces behind component regions",
                            "classic studio depth behind hero portrait and identity area",
                            "lower render-safe regions should be calm background surfaces, not app cards or framed boxes",
                            "avoid visible frames, borders, page edges, hard module borders, rows, chip shapes, thumbnails and video-player shapes",
                            "visual texture should support deterministic foreground panels drawn by the mini program"
                    )
            );
            case "commercial" -> styleBrief(
                    "商业演员背景底图",
                    "clean commercial actor background layer, bright premium studio hero scene, approachable expression, polished natural skin texture, advertising-ready composition, lower render-safe regions clean and minimal",
                    "white, graphite, muted champagne, soft blue, silver",
                    "bright clean softbox lighting, low-contrast lower panels",
                    "minimal contemporary wardrobe",
                    "commercial_profile_v3",
                    "paper-dark",
                    "studio-light",
                    "premium clean studio casting profile background with bright neutral safe zones and realistic approachable portrait",
                    "advertising-ready studio finish, crisp skin detail, clean neutral surfaces, no clutter or fake UI",
                    "hero right side, x=1080-2050, y=120-1400; face center near x=1580,y=520; body must not cover the left overlay-safe area",
                    "hero left side, x=100-1060, y=120-1300 must remain clean light studio empty space",
                    "clean white/soft grey low-detail safe surfaces for deterministic studio-light panels",
                    "premium studio backdrop, soft grey/white gradients, restrained champagne or blue accent light, no ancient paper texture, no city clutter",
                    List.of(
                            "clean studio gradients behind component regions",
                            "low contrast surfaces must remain readable for dark native text",
                            "avoid decorative clutter and fake app cards",
                            "do not draw rows, chips, thumbnails or video-player shapes",
                            "visual texture should support neutral studio deterministic foreground panels"
                    )
            );
            case "artistic" -> styleBrief(
                    "艺术演员背景底图",
                    "art-house actor background layer, cinematic shadow in hero area, expressive but realistic mood, textured backdrop, restrained gallery mood, calm lower render-safe surfaces",
                    "off-white, ink black, olive grey, muted rust, stone grey",
                    "controlled dramatic side light, readable lower-panel falloff",
                    "minimal expressive wardrobe with texture",
                    "artistic_profile_v3",
                    "cinema-light",
                    "gallery-glass",
                    "premium art-house casting profile background with expressive portrait, controlled shadows, and gallery-like readable safe zones",
                    "restrained gallery finish, realistic face detail, textured but quiet surfaces, no overdecorated graphics",
                    "hero right side, x=1080-2050, y=120-1480; face center near x=1580,y=560; expressive shadow must not cover the left overlay-safe area",
                    "hero left side, x=100-1060, y=120-1320 must remain low-detail gallery wall or shadow gradient for light native text",
                    "muted dark/gallery low-detail safe surfaces for deterministic glass panels and light native text",
                    "textured gallery wall, controlled dramatic shadows, olive/stone/rust accents, soft film grain; no古风 scenery, no parchment page look, no fake typography",
                    List.of(
                            "expressive texture is allowed only outside required component readability",
                            "lower safe zones must stay quiet enough for deterministic glass panels",
                            "portrait must not cover business slots",
                            "avoid hard UI rows, chips, thumbnails and video-player shapes",
                            "visual texture should support gallery-glass deterministic foreground panels"
                    )
            );
            default -> styleBrief(
                    "演员背景底图",
                    "premium cinematic actor full-bleed background layer, realistic face, professional casting composition, clean lower render-safe regions",
                    "neutral warm palette, ivory, graphite",
                    "soft professional portrait lighting with readable lower regions",
                    "clean actor wardrobe",
                    "classic_profile_v3",
                    "paper-dark",
                    "paper",
                    "premium neutral actor casting background with realistic portrait and clean safe zones",
                    "professional casting finish, crisp face detail, restrained visual surfaces, no enclosing frame",
                    "hero right side, x=1120-2050, y=120-1420; face center near x=1580,y=520",
                    "hero left side, x=120-1080, y=120-1320 must remain clean empty overlay space",
                    "clean low-detail safe surfaces for deterministic native panels",
                    "neutral studio texture without readable text, logos or fake UI, no border or page edge",
                    List.of(
                            "clean low-detail surfaces behind component regions",
                            "portrait must not cover business slots",
                            "avoid hard UI rows, chips, thumbnails and video-player shapes",
                            "visual texture should support deterministic foreground panels"
                    )
            );
        };
    }

    private StyleBrief styleBrief(String title,
                                  String visualDirection,
                                  String palette,
                                  String lighting,
                                  String wardrobe,
                                  String layoutPreset,
                                  String textTheme,
                                  String panelTheme,
                                  String referenceBenchmark,
                                  String qualityBar,
                                  String subjectBox,
                                  String identitySafeArea,
                                  String safeSurfaceTone,
                                  String backgroundMaterial,
                                  List<String> moduleAesthetics) {
        return new StyleBrief(
                title,
                visualDirection,
                palette,
                lighting,
                wardrobe,
                layoutPreset,
                textTheme,
                panelTheme,
                referenceBenchmark,
                qualityBar,
                subjectBox,
                identitySafeArea,
                safeSurfaceTone,
                backgroundMaterial,
                layoutRegions(layoutPreset),
                moduleAesthetics
        );
    }

    private List<String> periodModuleAesthetics() {
        return List.of(
                "warm ivory continuous ink-wash matte texture with subtle stains and fibers, never a separate paper sheet",
                "misty Jiangnan ink-wash landscape depth behind the actor and top overlay area",
                "delicate warm-red abstract motifs are allowed only as abstract shapes without readable characters",
                "lower render-safe regions should be calm background surfaces, not app cards or framed boxes",
                "avoid visible frames, borders, paper sheet edges, corner ornaments, hard module borders, rows, chip shapes, thumbnails and video-player shapes",
                "visual texture should support deterministic foreground panels drawn by the mini program"
        );
    }

    private Map<String, String> layoutRegions(String layoutPreset) {
        if (layoutPreset.startsWith("urban")) {
            return formatLayoutRegions(Map.of(
                    "identity", new LayoutRegion(74, 203, 264, 290, "low-detail dark overlay-safe hero area"),
                    "facts", new LayoutRegion(75, 586, 295, 184, "quiet dark safe surface, no fake labels"),
                    "skills", new LayoutRegion(404, 586, 271, 184, "quiet dark safe surface, no fake chips"),
                    "works", new LayoutRegion(75, 809, 600, 113, "quiet dark wide surface, no rows"),
                    "photos", new LayoutRegion(75, 938, 600, 116, "quiet dark strip, no thumbnail frames"),
                    "intro", new LayoutRegion(75, 1085, 288, 149, "quiet dark intro surface"),
                    "video", new LayoutRegion(415, 1085, 260, 149, "quiet dark video surface, no video-player UI")
            ));
        }
        if (layoutPreset.startsWith("commercial")) {
            return formatLayoutRegions(Map.of(
                    "identity", new LayoutRegion(74, 203, 257, 278, "clean light overlay-safe hero area"),
                    "facts", new LayoutRegion(75, 570, 296, 181, "quiet light studio surface, no fake labels"),
                    "skills", new LayoutRegion(403, 570, 272, 181, "quiet light studio surface, no fake chips"),
                    "works", new LayoutRegion(75, 787, 600, 113, "quiet light wide surface, no rows"),
                    "photos", new LayoutRegion(75, 916, 600, 119, "quiet light strip, no thumbnail frames"),
                    "intro", new LayoutRegion(75, 1068, 292, 155, "quiet light intro surface"),
                    "video", new LayoutRegion(413, 1068, 262, 155, "quiet light video surface, no video-player UI")
            ));
        }
        if (layoutPreset.startsWith("artistic")) {
            return formatLayoutRegions(Map.of(
                    "identity", new LayoutRegion(74, 203, 264, 290, "low-detail gallery overlay-safe hero area"),
                    "facts", new LayoutRegion(82, 593, 285, 181, "quiet gallery safe surface, no fake labels"),
                    "skills", new LayoutRegion(412, 593, 263, 181, "quiet gallery safe surface, no fake chips"),
                    "works", new LayoutRegion(82, 812, 586, 113, "quiet gallery wide surface, no rows"),
                    "photos", new LayoutRegion(82, 941, 586, 116, "quiet gallery strip, no thumbnail frames"),
                    "intro", new LayoutRegion(82, 1089, 285, 149, "quiet gallery intro surface"),
                    "video", new LayoutRegion(412, 1089, 256, 149, "quiet gallery video surface, no video-player UI")
            ));
        }
        if (layoutPreset.startsWith("costume")) {
            return formatLayoutRegions(Map.of(
                    "identity", new LayoutRegion(81, 203, 240, 291, "warm ink-wash overlay-safe hero area"),
                    "facts", new LayoutRegion(83, 579, 285, 184, "quiet warm matte surface, no fake labels"),
                    "skills", new LayoutRegion(420, 579, 255, 184, "quiet warm matte surface, no fake chips"),
                    "works", new LayoutRegion(84, 802, 582, 112, "quiet warm matte wide surface, no rows"),
                    "photos", new LayoutRegion(80, 929, 591, 116, "quiet warm matte strip, no thumbnail frames"),
                    "intro", new LayoutRegion(81, 1077, 281, 159, "quiet warm matte intro surface"),
                    "video", new LayoutRegion(416, 1077, 263, 159, "quiet warm matte video surface, no video-player UI")
            ));
        }
        return formatLayoutRegions(Map.of(
                "identity", new LayoutRegion(81, 203, 233, 291, "warm studio overlay-safe hero area"),
                "facts", new LayoutRegion(83, 579, 285, 184, "quiet light surface, no fake labels"),
                "skills", new LayoutRegion(420, 579, 255, 184, "quiet light surface, no fake chips"),
                "works", new LayoutRegion(84, 802, 582, 112, "quiet light wide surface, no rows"),
                "photos", new LayoutRegion(80, 929, 591, 116, "quiet light strip, no thumbnail frames"),
                "intro", new LayoutRegion(81, 1077, 281, 159, "quiet light intro surface"),
                "video", new LayoutRegion(416, 1077, 263, 159, "quiet light video surface, no video-player UI")
        ));
    }

    private Map<String, Object> designCanvas() {
        return Map.of(
                "width", DESIGN_CANVAS_WIDTH,
                "height", DESIGN_CANVAS_HEIGHT,
                "unit", "mini-program rpx logical design coordinate"
        );
    }

    private Map<String, Object> providerCanvas() {
        return Map.of(
                "width", PROVIDER_CANVAS_WIDTH,
                "height", PROVIDER_CANVAS_HEIGHT,
                "unit", "provider output pixels"
        );
    }

    private Map<String, String> formatLayoutRegions(Map<String, LayoutRegion> regions) {
        Map<String, String> formatted = new LinkedHashMap<>();
        regions.forEach((key, region) -> formatted.put(key, formatLayoutRegion(region)));
        return formatted;
    }

    private String formatLayoutRegion(LayoutRegion region) {
        int designRight = region.x() + region.w();
        int designBottom = region.y() + region.h();
        int providerLeft = scaleProviderX(region.x());
        int providerRight = scaleProviderX(designRight);
        int providerTop = scaleProviderY(region.y());
        int providerBottom = scaleProviderY(designBottom);
        return "design x=%d-%d y=%d-%d on 750x1334; provider x=%d-%d y=%d-%d on 2160x3840; %s".formatted(
                region.x(),
                designRight,
                region.y(),
                designBottom,
                providerLeft,
                providerRight,
                providerTop,
                providerBottom,
                region.note()
        );
    }

    private int scaleProviderX(int designX) {
        return Math.round(designX * PROVIDER_CANVAS_WIDTH / (float) DESIGN_CANVAS_WIDTH);
    }

    private int scaleProviderY(int designY) {
        return Math.round(designY * PROVIDER_CANVAS_HEIGHT / (float) DESIGN_CANVAS_HEIGHT);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("AI 分享图提示词序列化失败", error);
        }
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private record StyleBrief(
            String title,
            String visualDirection,
            String palette,
            String lighting,
            String wardrobe,
            String layoutPreset,
            String textTheme,
            String panelTheme,
            String referenceBenchmark,
            String qualityBar,
            String subjectBox,
            String identitySafeArea,
            String safeSurfaceTone,
            String backgroundMaterial,
            Map<String, String> layoutRegions,
            List<String> moduleAesthetics
    ) {
    }

    private record LayoutRegion(
            int x,
            int y,
            int w,
            int h,
            String note
    ) {
    }
}
