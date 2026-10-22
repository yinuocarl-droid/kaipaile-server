package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kaipai.model.actor.card.dto.ActorCardMediaBindingItemRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActorCardMainVisualResolverTest {

    @Test
    void expandedResultWinsAllOtherSources() {
        ActorCard card = card("https://cdn.test/source.jpg", "https://cdn.test/background.jpg");
        card.setExpandedImageUrl("https://cdn.test/expanded.jpg");

        assertEquals("https://cdn.test/expanded.jpg",
                ActorCardMainVisualResolver.resolveDisplayUrl(card, binding("https://signed.test/asset"),
                        value -> value.startsWith("https://")));
    }

    @Test
    void fingerprintedExpandedResultIsIgnoredAfterTheAssetChanges() {
        ActorCard card = card("https://cdn.test/source.jpg", "https://cdn.test/background.jpg");
        card.setExpandedImageUrl("https://cdn.test/expanded.jpg");
        card.setExpandedSourceFingerprint(
                ActorCardMainVisualResolver.SourceReference.forAsset(80L).fingerprint());

        assertEquals("https://signed.test/asset",
                ActorCardMainVisualResolver.resolveDisplayUrl(card, binding("https://signed.test/asset"),
                        value -> value.startsWith("https://")));
    }

    @Test
    void assetBindingWinsHistoricalSourceAndBackground() {
        ActorCard card = card("https://cdn.test/source.jpg", "https://cdn.test/background.jpg");

        assertEquals("https://signed.test/asset",
                ActorCardMainVisualResolver.resolveDisplayUrl(card, binding("https://signed.test/asset"),
                        value -> value.startsWith("https://")));
    }

    @Test
    void historicalSourceWinsHistoricalBackgroundWhenNoBindingOverrideExists() {
        ActorCard card = card("https://cdn.test/source.jpg", "https://cdn.test/background.jpg");

        assertEquals("https://cdn.test/source.jpg",
                ActorCardMainVisualResolver.resolveDisplayUrl(card, null,
                        value -> value.startsWith("https://")));
    }

    @Test
    void explicitEmptyBindingSuppressesStaleSourceButKeepsTemplateMainVisual() {
        ActorCard card = card("https://cdn.test/stale-source.jpg", "https://cdn.test/template.jpg");
        ActorCardMediaBindingRespDTO empty = emptyBinding();

        assertTrue(ActorCardMainVisualResolver.hasEffectiveSource(card, empty));
        assertEquals("https://cdn.test/template.jpg",
                ActorCardMainVisualResolver.resolveDisplayUrl(card, empty,
                        value -> value.startsWith("https://")));
        assertEquals("https://cdn.test/template.jpg",
                ActorCardMainVisualResolver.resolveGenerateInput(card, empty));
    }

    @Test
    void explicitEmptyBindingDoesNotReviveAStaleSourceByItself() {
        ActorCard card = card("https://cdn.test/stale-source.jpg", null);

        assertFalse(ActorCardMainVisualResolver.hasEffectiveSource(card, emptyBinding()));
        assertNull(ActorCardMainVisualResolver.resolveGenerateInput(card, emptyBinding()));
    }

    private ActorCard card(String source, String background) {
        ActorCard card = new ActorCard();
        card.setSourceImageUrl(source);
        card.setBackgroundImageUrl(background);
        return card;
    }

    private ActorCardMediaBindingRespDTO binding(String url) {
        ActorCardMediaBindingItemRespDTO item = new ActorCardMediaBindingItemRespDTO();
        item.setAssetId(81L);
        item.setPreviewUrl(url);
        ActorCardMediaBindingRespDTO binding = new ActorCardMediaBindingRespDTO();
        binding.setSetId(91L);
        binding.setItems(List.of(item));
        return binding;
    }

    private ActorCardMediaBindingRespDTO emptyBinding() {
        ActorCardMediaBindingRespDTO binding = new ActorCardMediaBindingRespDTO();
        binding.setSetId(91L);
        binding.setItems(List.of());
        return binding;
    }
}
