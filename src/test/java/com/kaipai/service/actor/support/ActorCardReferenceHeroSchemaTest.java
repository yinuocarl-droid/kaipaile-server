package com.kaipai.service.actor.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaipai.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ActorCardReferenceHeroSchemaTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void v1GoldenNormalizationBytesRemainUnchangedForPublishedChecksums() throws Exception {
        String style = "{\"radius\":16,\"padding\":24,\"gap\":16,\"marginTop\":0,\"marginBottom\":0,\"fontSize\":28,\"lineHeight\":42,\"fontWeight\":400,\"borderWidth\":0,\"color\":\"#1F2937\",\"backgroundColor\":\"#FFFFFF\",\"borderColor\":\"#E5E7EB\",\"shadow\":\"none\",\"align\":\"left\"}";
        String expected = "{\"schemaVersion\":1,\"minRendererVersion\":1,\"page\":{\"backgroundColor\":\"#F3F4F6\",\"contentPadding\":24,\"gap\":16},\"hero\":{\"height\":1334,\"focalX\":50,\"focalY\":50},\"defaults\":" + style
                + ",\"modules\":[{\"id\":\"identity\",\"type\":\"identity\",\"enabled\":true,\"order\":0,\"presentation\":\"standard\",\"layout\":{\"zone\":\"flow\",\"width\":702,\"align\":\"left\"},\"style\":" + style + "}]}";
        var actual = ActorCardDecorationSchema.normalize(json.readTree("{\"schemaVersion\":1,\"modules\":[{\"id\":\"identity\",\"type\":\"identity\"}]}"));
        assertEquals(expected,actual.toString());
    }

    private ObjectNode config() throws Exception {
        return (ObjectNode) json.readTree("""
          {"schemaVersion":2,"minRendererVersion":2,"modules":[
           {"id":"identity","type":"identity","presentation":"profileHero",
            "layout":{"zone":"hero","x":40,"y":884,"width":670,"height":300}},
           {"id":"promotion","type":"promotion","presentation":"pill",
            "layout":{"zone":"hero","x":40,"y":1199,"width":670,"height":125}}]}
          """);
    }

    @Test void normalizesReferenceHeroWithClosedDefaultsAndIsIdempotent() throws Exception {
        var input = config();
        var result = assertDoesNotThrow(() -> ActorCardDecorationSchema.normalize(input));
        assertEquals(2, result.path("minRendererVersion").asInt());
        assertEquals(true, result.at("/hero/overlay/enabled").asBoolean());
        assertEquals("#0F172A", result.at("/hero/overlay/color").asText());
        assertEquals(6, result.at("/hero/overlay/startOpacity").asInt());
        assertEquals(24, result.at("/hero/overlay/endOpacity").asInt());
        assertEquals("ACTOR PROFILE", result.at("/modules/0/identityOptions/eyebrow").asText());
        assertEquals(104, result.at("/modules/0/identityOptions/nameFontSize").asInt());
        assertEquals(124, result.at("/modules/0/identityOptions/nameLineHeight").asInt());
        assertEquals("[\"role\",\"height\",\"weight\"]", result.at("/modules/0/identityOptions/metaFields").toString());
        assertEquals("jin", result.at("/modules/0/identityOptions/weightUnit").asText());
        assertEquals(result, ActorCardDecorationSchema.normalize(result));
        assertFalse(input.at("/modules/0").has("identityOptions"));
    }

    @ParameterizedTest
    @CsvSource(delimiter='|', value={
      "eyebrowFontSize|15", "eyebrowFontSize|41", "eyebrowLetterSpacing|13",
      "nameFontSize|121", "nameLineHeight|103", "metaFontSize|49", "metaLineHeight|35",
      "eyebrowColor|\"red\"", "weightUnit|\"lb\"", "metaFields|[\"fans\"]",
      "metaFields|[\"role\",\"role\"]", "eyebrow|\"<script>\"", "roleLabel|\"a\\nb\"",
      "nameFontSize|\"104\"", "unknown|1", "eyebrow|null"
    })
    void rejectsInvalidIdentityOptions(String key, String value) throws Exception {
        var input = config();
        ((ObjectNode) input.at("/modules/0")).putObject("identityOptions").set(key, json.readTree(value));
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
    }

    @Test void enforcesVersionZoneAndTypeApplicabilityAndV1RemainsClosed() throws Exception {
        var input = config();
        ((ObjectNode) input.at("/modules/1/layout")).removeAll();
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
        var v1 = config(); v1.put("schemaVersion",1).put("minRendererVersion",1);
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(v1));
        var legacy = ActorCardDecorationSchema.normalize(json.readTree("{\"schemaVersion\":1,\"modules\":[{\"id\":\"identity\",\"type\":\"identity\"}]}"));
        assertFalse(legacy.path("hero").has("overlay"));
        assertFalse(legacy.at("/modules/0").has("identityOptions"));
    }

    @Test void allowsEmptyLabelsAndBoundaryOptionsButRejectsUnsafeOverlay() throws Exception {
        var input = config();
        ((ObjectNode) input.at("/modules/0")).putObject("identityOptions")
            .put("eyebrow", "").put("roleLabel", "").put("nameFontSize", 120).put("nameLineHeight",160);
        input.putObject("hero").putObject("overlay").put("enabled",false).put("startOpacity",0).put("endOpacity",100);
        var result = assertDoesNotThrow(() -> ActorCardDecorationSchema.normalize(input));
        assertEquals("", result.at("/modules/0/identityOptions/eyebrow").asText());
        assertFalse(result.at("/hero/overlay/enabled").asBoolean());
        ((ObjectNode)input.at("/hero/overlay")).put("css", "url(https://invalid)");
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
    }

    @ParameterizedTest
    @CsvSource(delimiter='|', value={"startOpacity|-1","startOpacity|101","endOpacity|1.5",
       "endOpacity|\"24\"","enabled|1","color|\"url(x)\"","color|null","unknown|0"})
    void overlayHasClosedFieldsTypesAndRanges(String key,String value) throws Exception {
        var input = config();
        input.putObject("hero").putObject("overlay").set(key,json.readTree(value));
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(input));
    }

    @Test void optionsNeverApplyToStandardIdentityOrOtherModulesAndStyleRangeDoesNotExpand() throws Exception {
        var standard=config();
        ((ObjectNode)standard.at("/modules/0")).put("presentation","standard").putObject("identityOptions");
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(standard));
        var promotion=config();
        ((ObjectNode)promotion.at("/modules/1")).putObject("identityOptions");
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(promotion));
        var largeStyle=config();
        ((ObjectNode)largeStyle.at("/modules/0")).putObject("style").put("fontSize",104).put("lineHeight",124);
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(largeStyle));
        var flow=config();
        ((ObjectNode)flow.at("/modules/0/layout")).removeAll();
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(flow));
        var longLabels=config();
        ((ObjectNode)longLabels.at("/modules/0")).putObject("identityOptions").put("eyebrow","a".repeat(41));
        assertThrows(BizException.class, () -> ActorCardDecorationSchema.normalize(longLabels));
    }
}
