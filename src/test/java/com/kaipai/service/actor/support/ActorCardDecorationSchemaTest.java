package com.kaipai.service.actor.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaipai.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ActorCardDecorationSchemaTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void omittedStylesInheritProtocolAndPageDefaultsWithoutMutatingDraft() throws Exception {
        var input = json("""
                {"schemaVersion":1,"defaults":{"radius":32,"color":"#123456"},
                 "modules":[{"id":"photos","type":"photos"}]}
                """);
        var result = ActorCardDecorationSchema.normalize(input);
        assertEquals(32, result.at("/modules/0/style/radius").asInt());
        assertEquals("#123456", result.at("/modules/0/style/color").asText());
        assertEquals(24, result.at("/modules/0/style/padding").asInt());
        assertEquals(702, result.at("/modules/0/layout/width").asInt());
        assertEquals("flow", result.at("/modules/0/layout/zone").asText());
        assertEquals("grid", result.at("/modules/0/presentation").asText());
        assertEquals(1, result.path("minRendererVersion").asInt());
        assertTrue(result.at("/modules/0/enabled").asBoolean());
        assertFalse(input.at("/modules/0").has("style"));
        assertFalse(input.has("page"));
    }

    @Test
    void zeroAndFalseOverrideDefaultsAndReturnedNodesAreIndependent() throws Exception {
        var input = json("""
                {"schemaVersion":1,"defaults":{"radius":32},
                 "modules":[{"id":"photos","type":"photos","enabled":false,"style":{"radius":0}},
                            {"id":"works","type":"works"}]}
                """);
        var result = ActorCardDecorationSchema.normalize(input);
        assertEquals(0, result.at("/modules/0/style/radius").asInt());
        assertFalse(result.at("/modules/0/enabled").asBoolean());
        ((ObjectNode) result.at("/modules/0/style")).put("radius", 90);
        assertEquals(32, result.at("/modules/1/style/radius").asInt());
        assertEquals(32, result.at("/defaults/radius").asInt());
        assertEquals(0, input.at("/modules/0/style/radius").asInt());
    }

    @Test
    void moduleOrderIsStableAndNormalizationIsIdempotent() throws Exception {
        var input = json("""
                {"schemaVersion":1,"modules":[
                 {"id":"photos","type":"photos","order":20},
                 {"id":"works","type":"works","order":10},
                 {"id":"intro","type":"introduction","order":10}]}
                """);
        var result = ActorCardDecorationSchema.normalize(input);
        assertEquals("works", result.at("/modules/0/id").asText());
        assertEquals("intro", result.at("/modules/1/id").asText());
        assertEquals("photos", result.at("/modules/2/id").asText());
        assertEquals(result, ActorCardDecorationSchema.normalize(result));
        assertEquals("photos", input.at("/modules/0/id").asText());
    }

    @Test
    void heroSummaryAndFlowFullIntroductionCanCoexist() throws Exception {
        var input = json("""
                {"schemaVersion":1,"hero":{"height":800},"modules":[
                 {"id":"summary","type":"introduction","layout":
                  {"zone":"hero","x":0,"y":600,"width":750,"height":200,"zIndex":20}},
                 {"id":"full","type":"introduction","presentation":"full"}]}
                """);
        var result = ActorCardDecorationSchema.normalize(input);
        assertEquals("summary", result.at("/modules/0/presentation").asText());
        assertEquals(3, result.at("/modules/0/maxLines").asInt());
        assertEquals("full", result.at("/modules/1/presentation").asText());
        assertFalse(result.at("/modules/1").has("maxLines"));
        assertEquals(20, result.at("/modules/0/layout/zIndex").asInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "disabled", "summary"})
    void enabledHeroSummaryRequiresAnEnabledFullIntroductionInBody(String state) throws Exception {
        var input = heroIntroduction();
        var modules = (ArrayNode) input.path("modules");
        if (!"missing".equals(state)) {
            var body = json("{\"id\":\"full\",\"type\":\"introduction\",\"presentation\":\"full\"}");
            if ("disabled".equals(state)) body.put("enabled", false);
            else body.put("presentation", "summary");
            modules.add(body);
        }
        var ex = assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
        assertTrue(ex.getMessage().contains("introduction"));
    }

    @Test
    void disabledHeroSummaryDoesNotForceAnUnwantedBodyModule() throws Exception {
        var input = heroIntroduction();
        ((ObjectNode) input.at("/modules/0")).put("enabled", false);
        var result = ActorCardDecorationSchema.normalize(input);
        assertEquals(1, result.path("modules").size());
        assertFalse(result.at("/modules/0/enabled").asBoolean());
    }

    @Test
    void allContentTypesAndTheirRealPresentationChoicesAreAccepted() throws Exception {
        var input = json("""
                {"schemaVersion":1,"modules":[
                 {"id":"identity","type":"identity"},{"id":"highlights","type":"highlights"},
                 {"id":"profile","type":"profile"},{"id":"intro","type":"introduction","presentation":"summary","maxLines":6},
                 {"id":"works","type":"works","presentation":"list"},
                 {"id":"photos","type":"photos","presentation":"carousel"},
                 {"id":"stills","type":"stills","presentation":"carousel"},
                 {"id":"video","type":"video","presentation":"poster"},
                 {"id":"pdf","type":"pdf","presentation":"pages"},
                 {"id":"cooperation","type":"cooperation"}]}
                """);
        var result = ActorCardDecorationSchema.normalize(input);
        assertEquals(10, result.path("modules").size());
        assertEquals("standard", result.at("/modules/0/presentation").asText());
        assertEquals("contact", result.at("/modules/9/presentation").asText());
        assertEquals(6, result.at("/modules/3/maxLines").asInt());
    }

    @Test
    void customBodyWidthAndAllowedStyleEnumsSurviveNormalization() throws Exception {
        var input = json("""
                {"schemaVersion":1,"page":{"contentPadding":0,"gap":0,"backgroundColor":"#abc"},
                 "defaults":{"fontSize":60,"lineHeight":72,"fontWeight":700,"borderWidth":8,
                  "borderColor":"transparent","shadow":"medium","align":"right"},
                 "modules":[{"id":"profile","type":"profile","layout":{"width":750,"align":"center"},
                  "style":{"backgroundColor":"#12345678","marginTop":160,"marginBottom":0,"gap":0}}]}
                """);
        var result = ActorCardDecorationSchema.normalize(input);
        assertEquals(750, result.at("/modules/0/layout/width").asInt());
        assertEquals("center", result.at("/modules/0/layout/align").asText());
        assertEquals("#12345678", result.at("/modules/0/style/backgroundColor").asText());
        assertEquals("medium", result.at("/modules/0/style/shadow").asText());
        assertEquals(0, result.at("/page/gap").asInt());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            /schemaVersion | 3 | schemaVersion
            /schemaVersion | 1.0 | schemaVersion
            /schemaVersion | null | schemaVersion
            /minRendererVersion | 2 | minRendererVersion
            /minRendererVersion | "1" | minRendererVersion
            /page | null | page
            /page | [] | page
            /page/contentPadding | 97 | contentPadding
            /page/contentPadding | -1 | contentPadding
            /page/gap | "16rpx" | gap
            /page/backgroundColor | "url(secret)" | backgroundColor
            /defaults | null | defaults
            /defaults/radius | 97 | radius
            /defaults/padding | -1 | padding
            /defaults/fontSize | 15 | fontSize
            /defaults/fontSize | 73 | fontSize
            /defaults/lineHeight | 23 | lineHeight
            /defaults/fontWeight | 900 | fontWeight
            /defaults/borderWidth | 9 | borderWidth
            /defaults/shadow | "0 0 10px red" | shadow
            /defaults/color | "transparent" | color
            /defaults/color | "red;position:fixed" | color
            /defaults/backgroundColor | "#12" | backgroundColor
            /defaults/align | "justify" | align
            /defaults/gap | 1.5 | gap
            /hero/height | 399 | height
            /hero/height | 1801 | height
            /hero/focalX | 101 | focalX
            /hero/focalY | -1 | focalY
            /modules | [] | modules
            /modules | {} | modules
            /modules/0/id | "Bad ID" | id
            /modules/0/type | "html" | type
            /modules/0/enabled | "false" | enabled
            /modules/0/order | -1 | order
            /modules/0/order | 2147483648 | order
            /modules/0/style | null | style
            /modules/0/style/radius | "12" | radius
            /modules/0/layout | null | layout
            /modules/0/layout/zone | "absolute" | zone
            /modules/0/layout/width | 703 | width
            /modules/0/presentation | "player" | presentation
            /modules/0/maxLines | 3 | maxLines
            """)
    void rejectsInvalidTypedValuesWithFieldPaths(String pointer, String value, String field) throws Exception {
        var input = minimal();
        set(input, pointer, mapper.readTree(value));
        var ex = assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
        assertTrue(ex.getMessage().contains(field), ex.getMessage());
        assertFalse(ex.getMessage().contains("secret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/script", "/page/css", "/hero/imageUrl", "/defaults/position",
            "/modules/0/dataBinding", "/modules/0/style/onTap", "/modules/0/layout/transform"})
    void rejectsUnknownFieldsAtEverySchemaLevel(String pointer) throws Exception {
        var input = minimal();
        set(input, pointer, mapper.readTree("\"injected\""));
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"url", "control", "long"})
    void unknownFieldErrorsNeverEchoUntrustedKeys(String kind) throws Exception {
        String key = switch (kind) {
            case "url" -> "https://private.example/file?q-signature=hidden-secret";
            case "control" -> "\r\nforged-log-line: secret";
            default -> "secret".repeat(1024);
        };
        var input = minimal();
        ((ObjectNode) input.at("/modules/0")).set("style", mapper.createObjectNode().put(key, 1));
        var ex = assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
        assertTrue(ex.getMessage().startsWith("decoration.modules[0].style"));
        assertTrue(ex.getMessage().length() < 160);
        assertFalse(ex.getMessage().contains("secret"));
        assertFalse(ex.getMessage().contains("\r"));
        assertFalse(ex.getMessage().contains("\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "y", "height", "zIndex"})
    void bodyCannotUseAbsoluteCoordinatesOrFixedHeight(String field) throws Exception {
        var input = minimal();
        ((ObjectNode) input.at("/modules/0")).set("layout", mapper.createObjectNode().put(field, 1));
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
    }

    @Test
    void rejectsDuplicateIdsAndSameZoneDuplicateTypes() throws Exception {
        var input = minimal();
        ((ArrayNode) input.path("modules")).add(json("{\"id\":\"photos\",\"type\":\"works\"}"));
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
        ((ObjectNode) input.at("/modules/1")).put("id", "other").put("type", "photos");
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"x\":1,\"y\":0,\"width\":750,\"height\":200}",
            "{\"x\":0,\"y\":1200,\"width\":600,\"height\":200}",
            "{\"x\":2147483647,\"y\":0,\"width\":600,\"height\":200}",
            "{\"x\":0,\"y\":0,\"width\":600}",
            "{\"x\":0,\"y\":0,\"width\":600,\"height\":200,\"zIndex\":21}"})
    void heroRequiresCompleteBoundedRectangles(String rectangle) throws Exception {
        var input = minimal();
        ((ObjectNode) input.at("/modules/0")).put("type", "identity");
        var layout = json(rectangle);
        layout.put("zone", "hero");
        ((ObjectNode) input.at("/modules/0")).set("layout", layout);
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
    }

    @Test
    void mediaAndFullIntroductionCannotBeClippedIntoHero() throws Exception {
        var input = minimal();
        var module = (ObjectNode) input.at("/modules/0");
        module.set("layout", json("{\"zone\":\"hero\",\"x\":0,\"y\":0,\"width\":600,\"height\":200}"));
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
        module.put("type", "introduction").put("presentation", "full");
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
    }

    @Test
    void rejectsCollapsedLineHeightAndExcessSummaryLines() throws Exception {
        var input = minimal();
        input.set("defaults", mapper.createObjectNode().put("fontSize", 60));
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
        input.remove("defaults");
        ((ObjectNode) input.at("/modules/0")).put("type", "introduction")
                .put("presentation", "summary").put("maxLines", 7);
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
    }

    @Test
    void rejectsMissingRequiredFieldsNonObjectsAndOversizedLists() throws Exception {
        for (String raw : new String[]{"null", "[]", "{}", "{\"schemaVersion\":1}"}) {
            JsonNode input = mapper.readTree(raw);
            assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
        }
        var input = minimal();
        var modules = (ArrayNode) input.path("modules");
        for (int i = 1; i < 17; i++) modules.add(json("{\"id\":\"item" + i + "\",\"type\":\"photos\"}"));
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(null));
    }

    private ObjectNode minimal() throws Exception {
        return json("{\"schemaVersion\":1,\"modules\":[{\"id\":\"photos\",\"type\":\"photos\"}]}");
    }

    private ObjectNode heroIntroduction() throws Exception {
        return json("""
                {"schemaVersion":1,"modules":[{"id":"summary","type":"introduction",
                 "layout":{"zone":"hero","x":0,"y":0,"width":600,"height":200}}]}
                """);
    }

    private ObjectNode json(String raw) throws Exception {
        return (ObjectNode) mapper.readTree(raw);
    }

    private void set(ObjectNode root, String pointer, JsonNode value) {
        var parts = pointer.substring(1).split("/");
        JsonNode parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (parent.isArray()) parent = parent.get(Integer.parseInt(parts[i]));
            else {
                var object = (ObjectNode) parent;
                if (!object.has(parts[i])) object.set(parts[i], mapper.createObjectNode());
                parent = object.get(parts[i]);
            }
        }
        ((ObjectNode) parent).set(parts[parts.length - 1], value);
    }
}
