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
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiProfileCardPromptAgent {

    private static final int DESIGN_CANVAS_WIDTH = 750;
    private static final int DESIGN_CANVAS_HEIGHT = 1334;
    private static final int PROVIDER_CANVAS_WIDTH = 2160;
    private static final int PROVIDER_CANVAS_HEIGHT = 3840;
    private static final String FULL_BLEED_BACKGROUND_POLICY = "full-bleed edge-to-edge background layer only; no visible frame, border, paper sheet edge, card outline, document page, scroll edge, poster mat, boxed background, corner bracket, corner ornament, or enclosing decorative box";

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
        return generateInternal(profile, taskId, providerCode, templateSceneCode, styleCode, sourceImageUrl, "cover", 1);
    }

    public AiProfileCardGeneration generatePage(ActorProfileDTO profile,
                                                String taskId,
                                                String providerCode,
                                                String templateSceneCode,
                                                String styleCode,
                                                String sourceImageUrl,
                                                String pageType,
                                                int pageNo) {
        return generateInternal(profile, taskId, providerCode, templateSceneCode, styleCode, sourceImageUrl, pageType, pageNo);
    }

    private AiProfileCardGeneration generateInternal(ActorProfileDTO profile,
                                                     String taskId,
                                                     String providerCode,
                                                     String templateSceneCode,
                                                     String styleCode,
                                                     String sourceImageUrl,
                                                     String pageType,
                                                     int pageNo) {
        AiProfileImageProvider provider = providerRegistry.resolve(providerCode);
        AiProfileCardPrompt prompt = build(profile, templateSceneCode, styleCode, sourceImageUrl, provider.modelCode(), pageType, pageNo);
        AiProfileImageGenerationResult imageResult = provider.generate(new AiProfileImageGenerationRequest(
                taskId,
                provider.modelCode(),
                templateSceneCode,
                styleCode,
                sourceImageUrl,
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
                                      String modelCode,
                                      String pageType,
                                      int pageNo) {
        String resolvedStyleCode = StringUtils.hasText(styleCode) ? styleCode.trim() : templateSceneCode;
        StyleBrief style = resolveStyle(templateSceneCode, resolvedStyleCode);
        PageBrief page = resolvePageBrief(style, pageType, pageNo);
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("task", "image_to_image_actor_profile_card_album_background");
        brief.put("modelCode", modelCode);
        brief.put("templateSceneCode", templateSceneCode);
        brief.put("styleCode", resolvedStyleCode);
        brief.put("canvas", Map.of(
                "ratio", "9:16 vertical",
                "targetSize", "2160x3840",
                "designCanvas", designCanvas(),
                "providerCanvas", providerCanvas(),
                "coordinatePolicy", "750x1334 is the authoritative mini-program design coordinate system; scale every fixed region proportionally to 2160x3840 provider pixels",
                "renderIntent", "visual background asset for mini program native actor detail rendering",
                "layoutPreset", style.layoutPreset(),
                "albumPage", page.pageType()
        ));
        brief.put("album", Map.of(
                "pageCount", 3,
                "fixedPages", List.of("cover", "resume", "gallery"),
                "currentPage", Map.of(
                        "pageNo", page.pageNo(),
                        "pageType", page.pageType(),
                        "role", page.roleDescription(),
                        "composition", page.composition(),
                        "subjectPolicy", page.subjectPolicy()
                )
        ));
        brief.put("referenceQuality", Map.of(
                "benchmark", style.referenceBenchmark(),
                "qualityBar", style.qualityBar(),
                "importantConstraint", "match the reference quality and atmosphere, but leave all final profile structure to deterministic mini-program rendering",
                "layoutCompliance", "quiet render-safe zones are mandatory in every style; do not draw hard business panels, final section titles, rows, thumbnails or UI components that frontend content must align to"
        ));
        brief.put("backgroundFramePolicy", FULL_BLEED_BACKGROUND_POLICY);
        brief.put("fixedLayout", buildFixedLayout(style, page));
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
        brief.put("qualityChecklist", List.of(
                "portrait identity is consistent with source image",
                "hands, eyes, hairline, clothing edges are clean",
                "no readable words, phone numbers, QR codes, logos or watermarks",
                "current album page role is followed: " + page.roleDescription(),
                "all fixed layout regions remain open for deterministic mini-program component rendering",
                page.subjectPolicy(),
                "lower profile-card sections stay calm, low contrast, and readable",
                "style-specific texture and visual details are premium but never compete with native foreground panels",
                "background stays full-bleed from edge to edge with no frame, border, card shell, page edge or corner ornament"
        ));

        String promptJson = writeJson(brief);
        String promptText = buildPromptText(profile, style, page, templateSceneCode, resolvedStyleCode, sourceImageUrl, promptJson);
        String negativePrompt = String.join(", ",
                "readable text",
                "Chinese characters",
                "English letters",
                "phone number",
                "QR code",
                "watermark",
                "brand logo",
                "extra face",
                "distorted face",
                "deformed hands",
                "low resolution",
                "over-smoothed plastic skin",
                "cropped head",
                "subject outside hero-right layout box",
                "busy profile-card text regions",
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
                "bordered lower profile-card modules",
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

    private String buildPromptText(ActorProfileDTO profile,
                                   StyleBrief style,
                                   PageBrief page,
                                   String templateSceneCode,
                                   String styleCode,
                                   String sourceImageUrl,
                                   String promptJson) {
        return """
                Create a high-end vertical actor profile-card album background layer using image-to-image generation.
                Use reference image #1 as the actor identity source: %s
                Preserve the actor's recognizable face, age impression, hairstyle direction, body proportion and natural skin texture.

                Fixed composition:
                - Canvas: 9:16 vertical poster, target 2160x3840.
                - Album page: pageNo=%d/3, pageType=%s, role=%s.
                - Authoritative layout coordinate system: mini-program design canvas 750x1334. All safe zones below are designed in that coordinate system and scaled to the 2160x3840 provider output.
                - This is only the visual background layer. Mini program native components will render all final text, photos, QR code and contact UI later.
                - layoutPreset=%s, textTheme=%s, panelTheme=%s.
                - Background boundary policy: %s.
                - The image must read as one continuous full-screen scene behind mini-program foreground components, like the urban style reference, without any enclosing visual shell.
                - Subject policy: %s.
                - Keep the page safe areas clean for mini-program-rendered foreground modules: %s.
                - Keep the deterministic content regions as %s:
                  %s
                - Page composition goal: %s.
                - Do not draw hard information cards, final borders, section labels, rows, chip shapes, thumbnails, video-player UI or other foreground components. The mini program will render those deterministically.
                - Background material: %s.
                - Do not render any words, Chinese characters, letters, numbers, QR code, watermark, logo, contact info or UI labels inside the image.

                Visual style:
                templateSceneCode=%s, styleCode=%s
                %s

                Actor profile signals to guide styling, wardrobe and mood only, not as rendered text:
                %s

                Model-independent brief:
                %s
                """.formatted(
                sourceImageUrl,
                page.pageNo(),
                page.pageType(),
                page.roleDescription(),
                style.layoutPreset(),
                style.textTheme(),
                style.panelTheme(),
                FULL_BLEED_BACKGROUND_POLICY,
                page.subjectPolicy(),
                page.identitySafeArea(),
                page.safeSurfaceTone(),
                buildReadableLayoutRegions(page),
                page.composition(),
                style.backgroundMaterial(),
                templateSceneCode,
                styleCode,
                style.visualDirection(),
                buildReadableProfileSignals(profile),
                promptJson
        );
    }

    private Map<String, Object> buildFixedLayout(StyleBrief style, PageBrief page) {
        Map<String, Object> fixedLayout = new LinkedHashMap<>();
        fixedLayout.put("layoutPreset", style.layoutPreset());
        fixedLayout.put("pageNo", page.pageNo());
        fixedLayout.put("pageType", page.pageType());
        fixedLayout.put("pageRole", page.roleDescription());
        fixedLayout.put("textTheme", style.textTheme());
        fixedLayout.put("panelTheme", style.panelTheme());
        fixedLayout.put("designCanvas", designCanvas());
        fixedLayout.put("providerCanvas", providerCanvas());
        fixedLayout.put("coordinatePolicy", "design coordinates are the single source of truth; provider coordinates are scaled descriptions only");
        fixedLayout.put("primaryReferenceSlot", "reference image #1 is the actor identity source; preserve facial identity and natural proportions");
        fixedLayout.put("subjectBox", page.subjectPolicy());
        fixedLayout.put("identitySafeArea", page.identitySafeArea());
        fixedLayout.put("safeSurfaceTone", page.safeSurfaceTone());
        fixedLayout.put("backgroundFramePolicy", FULL_BLEED_BACKGROUND_POLICY);
        fixedLayout.put("regions", page.layoutRegions());
        fixedLayout.put("background", style.backgroundMaterial());
        fixedLayout.put("finalTextPolicy", "do not render final business text, labels, rows, thumbnails, video controls, QR code, phone, contact UI, or fake app components");
        return fixedLayout;
    }

    private String buildReadableLayoutRegions(PageBrief page) {
        return page.layoutRegions().entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .reduce((left, right) -> left + "; " + right)
                .orElse("all deterministic component regions must remain calm and readable");
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

    private PageBrief resolvePageBrief(StyleBrief style, String pageType, int pageNo) {
        String normalizedPageType = StringUtils.hasText(pageType) ? pageType.trim() : "cover";
        if ("resume".equals(normalizedPageType)) {
            return new PageBrief(
                    2,
                    "resume",
                    "resume information expansion page for full profile facts, language, skills, intro and work timeline",
                    "use the reference identity only for consistent atmosphere; prefer environment-only or very soft distant actor silhouette, no strong hero face and no duplicated cover portrait",
                    "nearly the full page must stay calm and low-detail for mini-program-rendered title, basics, appearance, language, skill, intro and timeline modules",
                    style.safeSurfaceTone(),
                    "orderly editorial resume background with large quiet surfaces and subtle scene depth, not a document page and not drawn cards",
                    layoutRegions(style.layoutPreset(), "resume")
            );
        }
        if ("gallery".equals(normalizedPageType)) {
            return new PageBrief(
                    3,
                    "gallery",
                    "gallery page for real profile photos, production stills, work photos and video resume entry",
                    "use the reference identity only for color, lighting and casting mood; keep background light and avoid a large face competing with real photo grids",
                    "central and lower areas must remain low-detail for mini-program-rendered photo grids and video entry",
                    style.safeSurfaceTone(),
                    "premium film-gallery or studio background with restrained depth, no fake thumbnails, no labels and no video player controls",
                    layoutRegions(style.layoutPreset(), "gallery")
            );
        }
        return new PageBrief(
                pageNo > 0 ? pageNo : 1,
                "cover",
                "cover summary page for share entry and first-screen actor identity",
                "place the actor in the style-specific hero subject area only: " + style.subjectBox(),
                "identity text area must remain clean for mini-program-rendered title, actor name and selling points: " + style.identitySafeArea(),
                style.safeSurfaceTone(),
                "share cover with clear actor identity, controlled hero-right composition and readable lower deterministic modules",
                style.layoutRegions()
        );
    }

    private StyleBrief resolveStyle(String templateSceneCode, String styleCode) {
        if ("costume_actor_profile_full_card".equals(styleCode)) {
            return styleBrief(
                    "古风演员资料长图",
                    "premium Chinese period actor full-bleed background layer: right-side realistic actor portrait in elegant Han/Tang costume, warm ivory ink-wash atmosphere, misty Jiangnan mountains and bridge, subtle pavilion and bamboo silhouettes, cinnabar seal-like abstract accents, calm lower render-safe matte surfaces, no visible frame, no border, no card shell, no readable text",
                    "warm ivory atmosphere, dark ink green-black, antique gold linework, muted cinnabar accent, pale jade-grey washes, soft tea-stained matte texture",
                    "soft cinematic daylight, gentle rim light on hair and robe, translucent ink-wash haze, low contrast inside native render-safe zones, crisp face detail",
                    "period-drama robe silhouette, layered silk gauze fabric, understated embroidery, elegant hair ornament or hairpin if natural, refined and realistic rather than fantasy",
                    "costume_profile_v3",
                    "paper-dark",
                    "period-paper",
                    "premium period actor full-bleed background with refined ink-wash depth, realistic portrait, and calm lower safe zones without any enclosing frame",
                    "commercial casting finish, crisp facial realism, restrained antique-gold atmosphere, elegant empty surfaces, no cheap fantasy poster effects, no page or card edge",
                    "hero right side, x=1120-2050, y=120-1420; face center near x=1580,y=520; upper body must stay inside this box; costume silhouette may overlap softly into the center but never cover left text-safe area",
                    "hero left side, x=120-1080, y=120-1320 must remain clean warm ink-wash negative space",
                    "warm low-detail ink-wash matte safe surfaces for deterministic native panels",
                    "warm ivory full-bleed ink-wash texture, abstract seal shapes, misty Jiangnan mountains, bridge, garden architecture and bamboo silhouettes only as background material, no sheet border or page edge",
                    periodModuleAesthetics()
            );
        }
        return switch (templateSceneCode) {
            case "costume" -> styleBrief(
                    "古风演员资料长图",
                    "cinematic Chinese period-drama actor full-bleed background layer, elegant Han/Tang inspired wardrobe, refined fabric texture, ink-wash atmospheric depth, palace corridor or misty garden background, premium realistic portrait, calm warm lower render-safe surfaces, no visible frame, no border, no page edge, no fantasy exaggeration",
                    "warm ivory, dark ink, muted cinnabar, antique gold, jade green",
                    "soft directional key light, gentle rim light on hair and shoulders, calm lower-section lighting",
                    "period-drama robe silhouette, layered fabric, understated embroidery",
                    "costume_profile_v3",
                    "paper-dark",
                    "period-paper",
                    "premium period actor full-bleed background with realistic face, warm ink-wash texture, and calm render-safe lower zones without any enclosing frame",
                    "commercial casting finish with refined period-drama mood, clean face detail, low-noise background texture, no card shell",
                    "hero right side, x=1120-2050, y=120-1420; face center near x=1580,y=520; robe may overlap softly but not cover text-safe zones",
                    "hero left side, x=120-1080, y=120-1320 must remain clean warm ink-wash negative space",
                    "warm low-detail ink-wash matte safe surfaces for deterministic native panels",
                    "warm ivory full-bleed ink-wash texture, misty period architecture, bridge, bamboo and abstract seal accents without readable characters, no paper sheet edge",
                    periodModuleAesthetics()
            );
            case "urban" -> styleBrief(
                    "都市演员资料长图",
                    "modern cinematic actor profile-card background, quiet city or studio hero scene, polished fashion editorial tone, confident natural expression, realistic face detail, dark low-detail render-safe lower regions compatible with glass panels",
                    "charcoal, steel blue, porcelain white, restrained neon accent, soft grey",
                    "large softbox key light with cool rim light, dim but readable lower safe zones, no bright parchment surfaces",
                    "modern fitted coat or clean fashion styling",
                    "urban_profile_v3",
                    "cinema-light",
                    "cinema-glass",
                    "premium dark cinematic casting profile background with editorial portrait, controlled charcoal safe zones, and no beige paper modules",
                    "high-end fashion editorial finish, crisp realistic face detail, restrained city/studio atmosphere, clean dark surfaces for native glass panels",
                    "hero right side, x=1080-2050, y=120-1500; face center near x=1580,y=560; hair and coat must not cover left identity text-safe area",
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
                    "经典演员资料长图",
                    "timeless film-still actor full-bleed background layer, warm studio hero backdrop, analog cinema texture, elegant facial lighting, professional casting atmosphere, clean lower render-safe matte surfaces, no visible frame, no border, no document page",
                    "warm grey, sepia brown, ivory, muted black, champagne",
                    "classic three-point portrait lighting, soft falloff, readable lower safe-zone lighting",
                    "simple tailored neutral wardrobe",
                    "classic_profile_v3",
                    "paper-dark",
                    "paper",
                    "premium classic actor full-bleed background with warm studio texture, realistic portrait, and clean light lower safe zones without any enclosing frame",
                    "timeless film-still finish, crisp facial realism, restrained warm matte surfaces, no cheap poster effects, no page or card edge",
                    "hero right side, x=1120-2050, y=120-1420; face center near x=1580,y=520; upper body must not cover left text-safe area",
                    "hero left side, x=120-1080, y=120-1320 must remain warm low-detail studio negative space",
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
                    "商业演员资料长图",
                    "clean commercial actor profile-card background, bright premium studio hero scene, approachable expression, polished natural skin texture, advertising-ready composition, lower render-safe regions clean and minimal",
                    "white, graphite, muted champagne, soft blue, silver",
                    "bright clean softbox lighting, low-contrast lower panels",
                    "minimal contemporary wardrobe",
                    "commercial_profile_v3",
                    "paper-dark",
                    "studio-light",
                    "premium clean studio casting profile background with bright neutral safe zones and realistic approachable portrait",
                    "advertising-ready studio finish, crisp skin detail, clean neutral surfaces, no clutter or fake UI",
                    "hero right side, x=1080-2050, y=120-1400; face center near x=1580,y=520; body must not cover left identity text-safe area",
                    "hero left side, x=100-1060, y=120-1300 must remain clean light studio negative space",
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
                    "艺术演员资料长图",
                    "art-house actor profile-card background, cinematic shadow in hero area, expressive but realistic mood, textured backdrop, restrained gallery poster feeling, calm lower render-safe surfaces",
                    "off-white, ink black, olive grey, muted rust, stone grey",
                    "controlled dramatic side light, readable lower-panel falloff",
                    "minimal expressive wardrobe with texture",
                    "artistic_profile_v3",
                    "cinema-light",
                    "gallery-glass",
                    "premium art-house casting profile background with expressive portrait, controlled shadows, and gallery-like readable safe zones",
                    "restrained gallery poster finish, realistic face detail, textured but quiet surfaces, no overdecorated poster graphics",
                    "hero right side, x=1080-2050, y=120-1480; face center near x=1580,y=560; expressive shadow must not cover left identity text-safe area",
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
                    "演员资料长图",
                    "premium cinematic actor full-bleed background layer, realistic face, professional share poster composition, clean lower render-safe regions",
                    "neutral warm palette, ivory, graphite",
                    "soft professional portrait lighting with readable lower regions",
                    "clean actor wardrobe",
                    "classic_profile_v3",
                    "paper-dark",
                    "paper",
                    "premium neutral actor casting background with realistic portrait and clean safe zones",
                    "professional casting finish, crisp face detail, restrained visual surfaces, no enclosing frame",
                    "hero right side, x=1120-2050, y=120-1420; face center near x=1580,y=520",
                    "hero left side, x=120-1080, y=120-1320 must remain clean negative space",
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
                "misty Jiangnan ink-wash landscape depth behind the actor and title-safe area",
                "delicate cinnabar seal-like ornaments are allowed only as abstract shapes without readable characters",
                "lower render-safe regions should be calm background surfaces, not app cards or framed boxes",
                "avoid visible frames, borders, paper sheet edges, corner ornaments, hard module borders, rows, chip shapes, thumbnails and video-player shapes",
                "visual texture should support deterministic foreground panels drawn by the mini program"
        );
    }

    private Map<String, String> layoutRegions(String layoutPreset) {
        return layoutRegions(layoutPreset, "cover");
    }

    private Map<String, String> layoutRegions(String layoutPreset, String pageType) {
        if ("resume".equals(pageType)) {
            return formatLayoutRegions(Map.of(
                    "title", new LayoutRegion(56, 92, 638, 116, "quiet title surface, no fake heading text"),
                    "basics", new LayoutRegion(56, 240, 304, 188, "quiet facts surface, no fake labels or rows"),
                    "appearance", new LayoutRegion(390, 240, 304, 188, "quiet appearance surface, no fake labels or rows"),
                    "languages", new LayoutRegion(56, 456, 304, 136, "quiet language surface, no fake chips"),
                    "skills", new LayoutRegion(390, 456, 304, 136, "quiet skills surface, no fake chips"),
                    "intro", new LayoutRegion(56, 626, 638, 190, "quiet intro surface, no fake paragraphs"),
                    "workTimeline", new LayoutRegion(56, 850, 638, 368, "quiet work timeline surface, no fake timeline rows")
            ));
        }
        if ("gallery".equals(pageType)) {
            return formatLayoutRegions(Map.of(
                    "title", new LayoutRegion(56, 84, 638, 96, "quiet gallery title surface, no fake heading text"),
                    "portraitPhotos", new LayoutRegion(56, 214, 638, 264, "quiet portrait photo grid surface, no drawn thumbnail frames"),
                    "lifestylePhotos", new LayoutRegion(56, 512, 304, 252, "quiet lifestyle photo grid surface, no drawn thumbnails"),
                    "productionPhotos", new LayoutRegion(390, 512, 304, 252, "quiet production photo grid surface, no drawn thumbnails"),
                    "workPhotos", new LayoutRegion(56, 798, 400, 306, "quiet work photo surface, no fake captions"),
                    "video", new LayoutRegion(482, 798, 212, 306, "quiet video resume surface, no drawn video player")
            ));
        }
        if (layoutPreset.startsWith("urban")) {
            return formatLayoutRegions(Map.of(
                    "identity", new LayoutRegion(74, 203, 264, 290, "low-detail dark text-safe hero area"),
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
                    "identity", new LayoutRegion(74, 203, 257, 278, "clean light text-safe hero area"),
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
                    "identity", new LayoutRegion(74, 203, 264, 290, "low-detail gallery text-safe hero area"),
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
                    "identity", new LayoutRegion(81, 203, 240, 291, "warm ink-wash text-safe hero area"),
                    "facts", new LayoutRegion(83, 579, 285, 184, "quiet warm matte surface, no fake labels"),
                    "skills", new LayoutRegion(420, 579, 255, 184, "quiet warm matte surface, no fake chips"),
                    "works", new LayoutRegion(84, 802, 582, 112, "quiet warm matte wide surface, no rows"),
                    "photos", new LayoutRegion(80, 929, 591, 116, "quiet warm matte strip, no thumbnail frames"),
                    "intro", new LayoutRegion(81, 1077, 281, 159, "quiet warm matte intro surface"),
                    "video", new LayoutRegion(416, 1077, 263, 159, "quiet warm matte video surface, no video-player UI")
            ));
        }
        return formatLayoutRegions(Map.of(
                "identity", new LayoutRegion(81, 203, 233, 291, "warm studio text-safe hero area"),
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

    private record PageBrief(
            int pageNo,
            String pageType,
            String roleDescription,
            String subjectPolicy,
            String identitySafeArea,
            String safeSurfaceTone,
            String composition,
            Map<String, String> layoutRegions
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
