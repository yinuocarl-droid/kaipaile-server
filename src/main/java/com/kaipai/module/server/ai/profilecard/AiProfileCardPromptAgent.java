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
                                            String sourceImageUrl) {
        AiProfileImageProvider provider = providerRegistry.resolve(providerCode);
        AiProfileCardPrompt prompt = build(profile, templateSceneCode, sourceImageUrl, provider.modelCode());
        AiProfileImageGenerationResult imageResult = provider.generate(new AiProfileImageGenerationRequest(
                taskId,
                provider.modelCode(),
                templateSceneCode,
                sourceImageUrl,
                prompt.promptText(),
                prompt.negativePrompt(),
                prompt.promptJson()
        ));
        return new AiProfileCardGeneration(provider.providerCode(), provider.modelCode(), prompt, imageResult);
    }

    private AiProfileCardPrompt build(ActorProfileDTO profile,
                                      String templateSceneCode,
                                      String sourceImageUrl,
                                      String modelCode) {
        StyleBrief style = resolveStyle(templateSceneCode);
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("task", "image_to_image_actor_share_poster");
        brief.put("modelCode", modelCode);
        brief.put("styleCode", templateSceneCode);
        brief.put("canvas", Map.of(
                "ratio", "9:16 vertical",
                "targetSize", "2160x3840",
                "renderIntent", "premium actor profile share image"
        ));
        brief.put("fixedLayout", Map.of(
                "primaryReferenceSlot", "reference image #1 is the actor identity source; preserve facial identity and natural proportions",
                "subjectBox", "right side, x=1180-1980, y=400-3380; face center near x=1530,y=1080; upper body or full body must stay inside this box",
                "safeArea", "left side, x=160-1020, y=430-3200 must remain visually clean for app-rendered profile text; do not generate text",
                "background", "full bleed scenic background; depth behind subject; no readable signage"
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
                "left safe area remains open for deterministic frontend text overlay",
                "subject remains in the fixed right-side position"
        ));

        String promptJson = writeJson(brief);
        String promptText = buildPromptText(profile, style, sourceImageUrl, promptJson);
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
                "subject outside right-side layout box",
                "busy left-side safe area"
        );
        return new AiProfileCardPrompt(promptJson, promptText, negativePrompt);
    }

    private String buildPromptText(ActorProfileDTO profile,
                                   StyleBrief style,
                                   String sourceImageUrl,
                                   String promptJson) {
        return """
                Create a high-end vertical actor share image using image-to-image generation.
                Use reference image #1 as the actor identity source: %s
                Preserve the actor's recognizable face, age impression, hairstyle direction, body proportion and natural skin texture.

                Fixed composition:
                - Canvas: 9:16 vertical poster, target 2160x3840.
                - Place the actor on the right side only: x=1180-1980, y=400-3380, face center near x=1530,y=1080.
                - Keep the left side x=160-1020, y=430-3200 as a clean text-safe area for the app UI overlay.
                - Do not render any words, letters, numbers, QR code, watermark, logo, contact info or UI labels inside the image.

                Visual style:
                %s

                Actor profile signals to guide mood only, not as rendered text:
                %s

                Model-independent brief:
                %s
                """.formatted(
                sourceImageUrl,
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

    private StyleBrief resolveStyle(String templateSceneCode) {
        return switch (templateSceneCode) {
            case "costume" -> new StyleBrief(
                    "古风",
                    "cinematic Chinese period-drama actor portrait, elegant Han/Tang inspired wardrobe, refined fabric texture, ink-wash atmospheric depth, palace corridor or misty garden background, premium gpt-image style, editorial lighting, realistic face, no fantasy exaggeration",
                    "warm ivory, dark ink, muted cinnabar, antique gold",
                    "soft directional key light, gentle rim light on hair and shoulders",
                    "period-drama robe silhouette, layered fabric, understated embroidery"
            );
            case "urban" -> new StyleBrief(
                    "都市",
                    "modern cinematic actor portrait, quiet city night or studio backdrop, polished fashion editorial tone, confident but natural expression, realistic face detail, shallow depth of field",
                    "charcoal, steel blue, porcelain white, restrained neon accent",
                    "large softbox key light with cool rim light",
                    "modern fitted coat or clean fashion styling"
            );
            case "classic" -> new StyleBrief(
                    "经典",
                    "timeless film-still actor portrait, warm studio backdrop, analog cinema texture, elegant facial lighting, professional casting profile atmosphere",
                    "warm grey, sepia brown, ivory, muted black",
                    "classic three-point portrait lighting, soft falloff",
                    "simple tailored neutral wardrobe"
            );
            case "commercial" -> new StyleBrief(
                    "商业",
                    "clean commercial actor portrait, bright premium studio scene, approachable expression, polished skin texture, advertising-ready composition",
                    "white, graphite, muted champagne, soft blue",
                    "bright clean softbox lighting",
                    "minimal contemporary wardrobe"
            );
            case "artistic" -> new StyleBrief(
                    "艺术",
                    "art-house actor portrait, cinematic shadow, expressive but realistic mood, textured backdrop, restrained gallery poster feeling",
                    "off-white, ink black, olive grey, muted rust",
                    "controlled dramatic side light",
                    "minimal expressive wardrobe with texture"
            );
            default -> new StyleBrief(
                    "演员分享图",
                    "premium cinematic actor portrait, realistic face, professional share poster composition",
                    "neutral warm palette",
                    "soft professional portrait lighting",
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
