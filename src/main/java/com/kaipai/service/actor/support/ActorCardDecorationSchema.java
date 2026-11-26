package com.kaipai.service.actor.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaipai.common.exception.BizException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Closed v1/v2 page-decoration contract shared by draft and published-version services.
 * This class never resolves actor data, permissions, assets or AI inputs.
 */
public final class ActorCardDecorationSchema {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "minRendererVersion", "page", "defaults", "hero", "modules");
    private static final Set<String> PAGE_FIELDS = Set.of("backgroundColor", "contentPadding", "gap");
    private static final Set<String> HERO_FIELDS = Set.of("height", "focalX", "focalY");
    private static final Set<String> HERO_V2_FIELDS = Set.of("height", "focalX", "focalY", "overlay");
    private static final Set<String> HERO_OVERLAY_FIELDS = Set.of("enabled", "color", "startOpacity", "endOpacity");
    private static final Set<String> MODULE_FIELDS = Set.of(
            "id", "type", "enabled", "order", "presentation", "maxLines", "layout", "style");
    private static final Set<String> MODULE_V2_FIELDS = Set.of(
            "id", "type", "enabled", "order", "presentation", "maxLines", "layout", "style", "identityOptions");
    private static final Set<String> IDENTITY_OPTIONS_FIELDS = Set.of("eyebrow", "eyebrowFontSize",
            "eyebrowLetterSpacing", "eyebrowColor", "nameFontSize", "nameLineHeight", "metaFontSize",
            "metaLineHeight", "roleLabel", "metaFields", "weightUnit");
    private static final Set<String> META_FIELDS = Set.of("role", "gender", "age", "height", "weight", "city");
    private static final Set<String> STYLE_FIELDS = Set.of(
            "radius", "padding", "gap", "marginTop", "marginBottom", "fontSize", "lineHeight",
            "fontWeight", "color", "backgroundColor", "borderColor", "borderWidth", "shadow", "align");
    private static final Set<String> LAYOUT_FIELDS = Set.of("zone", "x", "y", "width", "height", "zIndex", "align");
    private static final Set<String> FLOW_FIELDS = Set.of("zone", "width", "align");
    private static final Set<String> OVERLAY_FIELDS = Set.of("zone", "x", "y", "width", "height", "zIndex");
    private static final Set<String> OVERLAY_TYPES = Set.of("identity", "highlights", "introduction");
    private static final Set<String> ALIGNMENTS = Set.of("left", "center", "right");
    private static final Map<String, List<String>> PRESENTATIONS = Map.of(
            "identity", List.of("standard"), "highlights", List.of("chips"),
            "profile", List.of("rows"), "introduction", List.of("full", "summary"),
            "works", List.of("cards", "list"), "photos", List.of("grid", "carousel"),
            "stills", List.of("grid", "carousel"), "video", List.of("player", "poster"),
            "pdf", List.of("button", "pages"), "cooperation", List.of("contact"));
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]{0,47}");
    private static final Pattern HEX = Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    private ActorCardDecorationSchema() {
    }

    /**
     * Validate a draft and return independent, stable-ordered, fully inherited renderer settings.
     * Missing optional fields inherit; explicit null and unsupported values are rejected.
     *
     * @param input configuration content, without template identity or actor data
     * @return a new configuration tree; neither input nor sibling styles share mutable nodes
     * @throws BizException when any field violates its version's closed contract
     */
    public static ObjectNode normalize(JsonNode input) {
        ObjectNode source = object(input, ROOT_FIELDS, "decoration");
        ObjectNode result = JSON.objectNode();
        int version = integer(source, "schemaVersion", null, 1, 2, "decoration");
        result.put("schemaVersion", version);
        result.put("minRendererVersion", integer(source, "minRendererVersion", version, version, version, "decoration"));

        ObjectNode page = normalizePage(optionalObject(source, "page", PAGE_FIELDS, "decoration"));
        ObjectNode hero = normalizeHero(optionalObject(source, "hero", version == 2 ? HERO_V2_FIELDS : HERO_FIELDS, "decoration"), version);
        ObjectNode defaults = normalizeStyle(optionalObject(source, "defaults", STYLE_FIELDS, "decoration"),
                baseStyle(), "decoration.defaults");
        result.set("page", page);
        result.set("hero", hero);
        result.set("defaults", defaults);

        JsonNode modules = source.get("modules");
        if (modules == null || !modules.isArray() || modules.isEmpty() || modules.size() > 16) {
            throw invalid("decoration.modules", "must contain 1..16 modules");
        }
        List<ObjectNode> normalized = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> typesByZone = new HashSet<>();
        for (int index = 0; index < modules.size(); index++) {
            String path = "decoration.modules[" + index + "]";
            ObjectNode module = normalizeModule(modules.get(index), defaults, page, hero, path, version);
            if (!ids.add(module.path("id").textValue())) {
                throw invalid(path + ".id", "must be unique");
            }
            String zoneType = module.at("/layout/zone").textValue() + ":" + module.path("type").textValue();
            if (!typesByZone.add(zoneType)) {
                throw invalid(path + ".type", "must be unique within its zone");
            }
            normalized.add(module);
        }
        requireFullIntroductionForHero(normalized);
        // List.sort is stable: equal order values retain the editor's input order.
        normalized.sort(Comparator.comparingInt(module -> module.path("order").intValue()));
        var output = result.putArray("modules");
        normalized.forEach(output::add);
        return result;
    }

    private static void requireFullIntroductionForHero(List<ObjectNode> modules) {
        boolean enabledHeroSummary = false;
        boolean enabledBodyFull = false;
        for (ObjectNode module : modules) {
            if (!module.path("enabled").booleanValue() || !"introduction".equals(module.path("type").textValue())) {
                continue;
            }
            if ("hero".equals(module.at("/layout/zone").textValue())) enabledHeroSummary = true;
            if ("flow".equals(module.at("/layout/zone").textValue())
                    && "full".equals(module.path("presentation").textValue())) enabledBodyFull = true;
        }
        if (enabledHeroSummary && !enabledBodyFull) {
            throw invalid("decoration.modules.introduction", "enabled hero summary requires enabled flow full text");
        }
    }

    private static ObjectNode normalizePage(ObjectNode input) {
        ObjectNode result = JSON.objectNode();
        result.put("backgroundColor", color(input, "backgroundColor", "#F3F4F6", false, "decoration.page"));
        result.put("contentPadding", integer(input, "contentPadding", 24, 0, 96, "decoration.page"));
        result.put("gap", integer(input, "gap", 16, 0, 96, "decoration.page"));
        return result;
    }

    private static ObjectNode normalizeHero(ObjectNode input, int version) {
        ObjectNode result = JSON.objectNode();
        result.put("height", integer(input, "height", 1334, 400, 1800, "decoration.hero"));
        result.put("focalX", integer(input, "focalX", 50, 0, 100, "decoration.hero"));
        result.put("focalY", integer(input, "focalY", 50, 0, 100, "decoration.hero"));
        if (version == 2) {
            ObjectNode overlay = optionalObject(input, "overlay", HERO_OVERLAY_FIELDS, "decoration.hero");
            result.putObject("overlay").put("enabled", bool(overlay, "enabled", true, "decoration.hero.overlay"))
                    .put("color", color(overlay, "color", "#0F172A", false, "decoration.hero.overlay"))
                    .put("startOpacity", integer(overlay, "startOpacity", 6, 0, 100, "decoration.hero.overlay"))
                    .put("endOpacity", integer(overlay, "endOpacity", 24, 0, 100, "decoration.hero.overlay"));
        }
        return result;
    }

    private static ObjectNode normalizeModule(JsonNode raw, ObjectNode defaults, ObjectNode page,
                                               ObjectNode hero, String path, int version) {
        ObjectNode input = object(raw, version == 2 ? MODULE_V2_FIELDS : MODULE_FIELDS, path);
        String id = text(input, "id", null, path);
        if (!ID.matcher(id).matches()) throw invalid(path + ".id", "invalid module identifier");
        Set<String> types = new HashSet<>(PRESENTATIONS.keySet());
        if (version == 2) types.add("promotion");
        String type = choice(input, "type", null, types, path);
        ObjectNode layout = normalizeLayout(optionalObject(input, "layout", LAYOUT_FIELDS, path),
                type, page, hero, path + ".layout", version);
        boolean overlay = "hero".equals(layout.path("zone").textValue());
        List<String> presentations = "promotion".equals(type) ? List.of("pill") : PRESENTATIONS.get(type);
        Set<String> allowedPresentations = new HashSet<>(presentations);
        if (version == 2 && "identity".equals(type)) allowedPresentations.add("profileHero");
        String defaultPresentation = overlay && "introduction".equals(type)
                ? "summary" : presentations.get(0);
        String presentation = choice(input, "presentation", defaultPresentation,
                allowedPresentations, path);
        if (("profileHero".equals(presentation) || "promotion".equals(type)) && !overlay) {
            throw invalid(path + ".presentation", "requires hero layout");
        }
        if (overlay && "introduction".equals(type) && !"summary".equals(presentation)) {
            throw invalid(path + ".presentation", "hero introduction must use summary");
        }
        ObjectNode result = JSON.objectNode();
        result.put("id", id);
        result.put("type", type);
        result.put("enabled", bool(input, "enabled", true, path));
        result.put("order", integer(input, "order", 0, 0, 10000, path));
        result.put("presentation", presentation);
        if ("profileHero".equals(presentation)) {
            result.set("identityOptions", normalizeIdentityOptions(
                    optionalObject(input, "identityOptions", IDENTITY_OPTIONS_FIELDS, path), path + ".identityOptions"));
        } else if (input.has("identityOptions")) {
            throw invalid(path + ".identityOptions", "only supported for profileHero identity");
        }
        if ("introduction".equals(type) && "summary".equals(presentation)) {
            result.put("maxLines", integer(input, "maxLines", 3, 1, 6, path));
        } else if (input.has("maxLines")) {
            throw invalid(path + ".maxLines", "only supported for introduction summary");
        }
        result.set("layout", layout);
        result.set("style", normalizeStyle(optionalObject(input, "style", STYLE_FIELDS, path),
                defaults, path + ".style"));
        return result;
    }

    private static ObjectNode normalizeLayout(ObjectNode input, String type, ObjectNode page,
                                               ObjectNode hero, String path, int version) {
        String zone = choice(input, "zone", "flow", Set.of("hero", "flow"), path);
        ObjectNode result = JSON.objectNode().put("zone", zone);
        if ("flow".equals(zone)) {
            object(input, FLOW_FIELDS, path);
            int bodyWidth = 750 - 2 * page.path("contentPadding").intValue();
            result.put("width", integer(input, "width", bodyWidth, 1, bodyWidth, path));
            result.put("align", choice(input, "align", "left", ALIGNMENTS, path));
            return result;
        }
        object(input, OVERLAY_FIELDS, path);
        if (!OVERLAY_TYPES.contains(type) && !(version == 2 && "promotion".equals(type))) {
            throw invalid(path + ".zone", "module requires flow layout");
        }
        int canvasHeight = hero.path("height").intValue();
        int x = integer(input, "x", null, 0, 750, path);
        int y = integer(input, "y", null, 0, canvasHeight, path);
        int width = integer(input, "width", null, 1, 750, path);
        int height = integer(input, "height", null, 1, canvasHeight, path);
        // Each operand is already bounded; addition cannot overflow.
        if (x + width > 750 || y + height > canvasHeight) {
            throw invalid(path, "rectangle must remain within hero bounds");
        }
        result.put("x", x).put("y", y).put("width", width).put("height", height);
        result.put("zIndex", integer(input, "zIndex", 0, 0, 20, path));
        return result;
    }

    private static ObjectNode normalizeIdentityOptions(ObjectNode input, String path) {
        ObjectNode result = JSON.objectNode();
        result.put("eyebrow", safeLabel(input, "eyebrow", "ACTOR PROFILE", 40, path));
        result.put("eyebrowFontSize", integer(input, "eyebrowFontSize", 28, 16, 40, path));
        result.put("eyebrowLetterSpacing", integer(input, "eyebrowLetterSpacing", 6, 0, 12, path));
        result.put("eyebrowColor", color(input, "eyebrowColor", "#D8E0EA", false, path));
        result.put("nameFontSize", integer(input, "nameFontSize", 104, 32, 120, path));
        result.put("nameLineHeight", integer(input, "nameLineHeight", 124, 40, 160, path));
        result.put("metaFontSize", integer(input, "metaFontSize", 36, 16, 48, path));
        result.put("metaLineHeight", integer(input, "metaLineHeight", 48, 24, 80, path));
        if (result.path("nameLineHeight").asInt() < result.path("nameFontSize").asInt()
                || result.path("metaLineHeight").asInt() < result.path("metaFontSize").asInt()) {
            throw invalid(path, "lineHeight must not be smaller than fontSize");
        }
        result.put("roleLabel", safeLabel(input, "roleLabel", "演员", 16, path));
        var meta = result.putArray("metaFields");
        if (!input.has("metaFields")) {
            meta.add("role").add("height").add("weight");
        } else {
            JsonNode fields = input.get("metaFields");
            if (!fields.isArray() || fields.size() > 6) throw invalid(path + ".metaFields", "must contain at most 6 fields");
            Set<String> seen = new HashSet<>();
            for (JsonNode field : fields) {
                if (!field.isTextual() || !META_FIELDS.contains(field.textValue()) || !seen.add(field.textValue())) {
                    throw invalid(path + ".metaFields", "must contain unique supported fields");
                }
                meta.add(field.textValue());
            }
        }
        result.put("weightUnit", choice(input, "weightUnit", "jin", Set.of("kg", "jin"), path));
        return result;
    }

    private static String safeLabel(ObjectNode input, String field, String fallback, int max, String path) {
        String value = text(input, field, fallback, path);
        if (value.length() > max || HeroBadgeText.hasForbiddenCharacters(value)) {
            throw invalid(path + "." + field, "invalid plain text");
        }
        return value;
    }

    private static ObjectNode normalizeStyle(ObjectNode input, ObjectNode inherited, String path) {
        ObjectNode result = inherited.deepCopy();
        styleInteger(result, input, "radius", 0, 96, path);
        styleInteger(result, input, "padding", 0, 96, path);
        styleInteger(result, input, "gap", 0, 96, path);
        styleInteger(result, input, "marginTop", 0, 160, path);
        styleInteger(result, input, "marginBottom", 0, 160, path);
        styleInteger(result, input, "fontSize", 16, 72, path);
        styleInteger(result, input, "lineHeight", 24, 120, path);
        styleInteger(result, input, "fontWeight", 400, 700, path);
        styleInteger(result, input, "borderWidth", 0, 8, path);
        if (!Set.of(400, 500, 600, 700).contains(result.path("fontWeight").intValue())) {
            throw invalid(path + ".fontWeight", "unsupported font weight");
        }
        if (result.path("lineHeight").intValue() < result.path("fontSize").intValue()) {
            throw invalid(path + ".lineHeight", "must not be smaller than fontSize");
        }
        for (String field : List.of("color", "backgroundColor", "borderColor")) {
            result.put(field, color(input, field, inherited.path(field).textValue(), !"color".equals(field), path));
        }
        result.put("shadow", choice(input, "shadow", inherited.path("shadow").textValue(),
                Set.of("none", "soft", "medium"), path));
        result.put("align", choice(input, "align", inherited.path("align").textValue(), ALIGNMENTS, path));
        return result;
    }

    private static ObjectNode baseStyle() {
        return JSON.objectNode().put("radius", 16).put("padding", 24).put("gap", 16)
                .put("marginTop", 0).put("marginBottom", 0).put("fontSize", 28).put("lineHeight", 42)
                .put("fontWeight", 400).put("borderWidth", 0).put("color", "#1F2937")
                .put("backgroundColor", "#FFFFFF").put("borderColor", "#E5E7EB")
                .put("shadow", "none").put("align", "left");
    }

    private static void styleInteger(ObjectNode result, ObjectNode input, String field,
                                     int min, int max, String path) {
        result.put(field, integer(input, field, result.path(field).intValue(), min, max, path));
    }

    private static ObjectNode optionalObject(ObjectNode parent, String field, Set<String> fields, String path) {
        return parent.has(field) ? object(parent.get(field), fields, path + "." + field) : JSON.objectNode();
    }

    private static ObjectNode object(JsonNode value, Set<String> fields, String path) {
        if (value == null || !value.isObject()) throw invalid(path, "must be an object");
        value.fieldNames().forEachRemaining(field -> {
            // Unknown JSON keys are untrusted too: never echo private URLs or log-control characters.
            if (!fields.contains(field)) throw invalid(path + ".<unknown>", "unsupported field");
        });
        return (ObjectNode) value;
    }

    private static int integer(ObjectNode parent, String field, Integer fallback, int min, int max, String path) {
        JsonNode node = parent.get(field);
        if (node == null && fallback != null) return fallback;
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()
                || node.intValue() < min || node.intValue() > max) {
            throw invalid(path + "." + field, "must be an integer in [" + min + ", " + max + "]");
        }
        return node.intValue();
    }

    private static String text(ObjectNode parent, String field, String fallback, String path) {
        JsonNode node = parent.get(field);
        if (node == null && fallback != null) return fallback;
        if (node == null || !node.isTextual()) throw invalid(path + "." + field, "must be a string");
        return node.textValue();
    }

    private static String choice(ObjectNode parent, String field, String fallback, Set<String> choices, String path) {
        String value = text(parent, field, fallback, path);
        if (!choices.contains(value)) throw invalid(path + "." + field, "unsupported value");
        return value;
    }

    private static String color(ObjectNode parent, String field, String fallback, boolean transparent, String path) {
        String value = text(parent, field, fallback, path);
        if (!HEX.matcher(value).matches() && !(transparent && "transparent".equals(value))) {
            throw invalid(path + "." + field, "must be a supported hex color");
        }
        return value;
    }

    private static boolean bool(ObjectNode parent, String field, boolean fallback, String path) {
        JsonNode node = parent.get(field);
        if (node == null) return fallback;
        if (!node.isBoolean()) throw invalid(path + "." + field, "must be a boolean");
        return node.booleanValue();
    }

    private static BizException invalid(String path, String reason) {
        return new BizException(path + ": " + reason);
    }
}
