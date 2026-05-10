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
        AiProfileImageProvider provider = providerRegistry.resolve(providerCode);
        AiProfileCardPrompt prompt = build(profile, templateSceneCode, styleCode, sourceImageUrl, provider.modelCode());
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
                                      String modelCode) {
        String resolvedStyleCode = StringUtils.hasText(styleCode) ? styleCode.trim() : templateSceneCode;
        StyleBrief style = resolveStyle(templateSceneCode, resolvedStyleCode);
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("task", "image_to_image_actor_profile_card_background");
        brief.put("modelCode", modelCode);
        brief.put("templateSceneCode", templateSceneCode);
        brief.put("styleCode", resolvedStyleCode);
        brief.put("canvas", Map.of(
                "ratio", "9:16 vertical",
                "targetSize", "2160x3840",
                "renderIntent", "background and actor portrait layer for deterministic profile card rendering"
        ));
        brief.put("fixedLayout", Map.of(
                "primaryReferenceSlot", "reference image #1 is the actor identity source; preserve facial identity and natural proportions",
                "subjectBox", "hero right side, x=1120-2050, y=120-1420; face center near x=1580,y=520; upper body must stay inside this box",
                "heroTextSafeArea", "hero left side, x=120-1080, y=120-1320 must remain clean for backend-rendered name and facts",
                "skillsRegion", "x=120-970, y=1450-2210 must remain light and readable for backend-rendered skills",
                "worksRegion", "x=1080-2010, y=1450-2210 must remain light and readable for backend-rendered works",
                "photoStripRegion", "x=120-2040, y=2340-2700 must remain clean for backend-rendered photo thumbnails",
                "aboutRegion", "x=120-2040, y=2860-3440 must remain clean for backend-rendered intro and stats",
                "footerRegion", "x=0-2160, y=3540-3840 should be a darker calm footer background without text",
                "background", "full bleed scenic background, document-like parchment or studio surface in lower regions, no readable signage"
        ));
        brief.put("profileSignals", buildProfileSignals(profile));
        brief.put("style", Map.of(
                "title", style.title(),
                "visualDirection", style.visualDirection(),
                "palette", style.palette(),
                "lighting", style.lighting(),
                "wardrobe", style.wardrobe()
        ));
        brief.put("qualityChecklist", List.of(
                "portrait identity is consistent with source image",
                "hands, eyes, hairline, clothing edges are clean",
                "no readable words, phone numbers, QR codes, logos or watermarks",
                "all fixed layout regions remain open for deterministic backend rendering",
                "subject remains in the fixed hero-right position",
                "lower profile-card sections stay calm, low contrast, and readable"
        ));

        String promptJson = writeJson(brief);
        String promptText = buildPromptText(profile, style, templateSceneCode, resolvedStyleCode, sourceImageUrl, promptJson);
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
                "dark blocks behind text regions",
                "fake UI labels",
                "fake QR code"
        );
        return new AiProfileCardPrompt(promptJson, promptText, negativePrompt);
    }

    private String buildPromptText(ActorProfileDTO profile,
                                   StyleBrief style,
                                   String templateSceneCode,
                                   String styleCode,
                                   String sourceImageUrl,
                                   String promptJson) {
        return """
                Create a high-end vertical actor profile-card background layer using image-to-image generation.
                Use reference image #1 as the actor identity source: %s
                Preserve the actor's recognizable face, age impression, hairstyle direction, body proportion and natural skin texture.

                Fixed composition:
                - Canvas: 9:16 vertical poster, target 2160x3840.
                - This is only the visual background layer. Backend code will render all final text, photos, QR code and contact UI later.
                - Place the actor in the hero right area only: x=1120-2050, y=120-1420, face center near x=1580,y=520.
                - Keep hero left x=120-1080, y=120-1320 clean for backend-rendered name and profile facts.
                - Keep lower document regions calm and readable:
                  skills x=120-970 y=1450-2210, works x=1080-2010 y=1450-2210,
                  photos x=120-2040 y=2340-2700, about/stats x=120-2040 y=2860-3440,
                  footer x=0-2160 y=3540-3840.
                - Do not render any words, letters, numbers, QR code, watermark, logo, contact info or UI labels inside the image.

                Visual style:
                templateSceneCode=%s, styleCode=%s
                %s

                Actor profile signals to guide styling, wardrobe and mood only, not as rendered text:
                %s

                Model-independent brief:
                %s
                """.formatted(
                sourceImageUrl,
                templateSceneCode,
                styleCode,
                style.visualDirection(),
                buildReadableProfileSignals(profile),
                promptJson
        );
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
            return new StyleBrief(
                    "古风演员资料长图",
                    "Chinese period-drama actor profile-card background, elegant palace garden and ink-wash scenery, warm ivory parchment document lower half, antique gold ornamentation, refined Han/Tang costume texture, premium realistic portrait, calm readable layout surfaces",
                    "warm ivory, ink black, antique gold, muted cinnabar, jade green footer",
                    "soft cinematic daylight, gentle rim light on hair and robe, low contrast in document regions",
                    "period-drama robe silhouette, layered fabric, understated embroidery, elegant hair ornament if natural"
            );
        }
        return switch (templateSceneCode) {
            case "costume" -> new StyleBrief(
                    "古风演员资料长图",
                    "cinematic Chinese period-drama actor profile-card background, elegant Han/Tang inspired wardrobe, refined fabric texture, ink-wash atmospheric depth, palace corridor or misty garden background, premium realistic portrait, parchment lower document sections, no fantasy exaggeration",
                    "warm ivory, dark ink, muted cinnabar, antique gold, jade green",
                    "soft directional key light, gentle rim light on hair and shoulders, calm lower-section lighting",
                    "period-drama robe silhouette, layered fabric, understated embroidery"
            );
            case "urban" -> new StyleBrief(
                    "都市演员资料长图",
                    "modern cinematic actor profile-card background, quiet city or studio hero scene, polished fashion editorial tone, confident natural expression, realistic face detail, clean document panels in lower regions",
                    "charcoal, steel blue, porcelain white, restrained neon accent, soft grey",
                    "large softbox key light with cool rim light, readable lower panels",
                    "modern fitted coat or clean fashion styling"
            );
            case "classic" -> new StyleBrief(
                    "经典演员资料长图",
                    "timeless film-still actor profile-card background, warm studio hero backdrop, analog cinema texture, elegant facial lighting, professional casting profile atmosphere, clean lower document sections",
                    "warm grey, sepia brown, ivory, muted black, champagne",
                    "classic three-point portrait lighting, soft falloff, readable document lighting",
                    "simple tailored neutral wardrobe"
            );
            case "commercial" -> new StyleBrief(
                    "商业演员资料长图",
                    "clean commercial actor profile-card background, bright premium studio hero scene, approachable expression, polished natural skin texture, advertising-ready composition, lower regions clean and minimal",
                    "white, graphite, muted champagne, soft blue, silver",
                    "bright clean softbox lighting, low-contrast lower panels",
                    "minimal contemporary wardrobe"
            );
            case "artistic" -> new StyleBrief(
                    "艺术演员资料长图",
                    "art-house actor profile-card background, cinematic shadow in hero area, expressive but realistic mood, textured backdrop, restrained gallery poster feeling, calm lower document surfaces",
                    "off-white, ink black, olive grey, muted rust, stone grey",
                    "controlled dramatic side light, readable lower-panel falloff",
                    "minimal expressive wardrobe with texture"
            );
            default -> new StyleBrief(
                    "演员资料长图",
                    "premium cinematic actor profile-card background, realistic face, professional share poster composition, clean lower document regions",
                    "neutral warm palette, ivory, graphite",
                    "soft professional portrait lighting with readable lower regions",
                    "clean actor wardrobe"
            );
        };
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
            String wardrobe
    ) {
    }
}
