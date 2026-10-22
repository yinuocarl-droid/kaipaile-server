package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingItemRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActorCardExpandResultServiceTest {

    private ActorCardMapper cardMapper;
    private ActorCardMediaBindingService bindingService;
    private ActorCardExpandResultService service;

    @BeforeEach
    void setUp() {
        cardMapper = mock(ActorCardMapper.class);
        bindingService = mock(ActorCardMediaBindingService.class);
        service = new ActorCardExpandResultService(cardMapper, bindingService);
        ActorCard card = new ActorCard();
        card.setId(11L);
        card.setUserId(7L);
        when(cardMapper.selectOwnedForUpdate(7L, 11L)).thenReturn(card);
        when(bindingService.list(7L, 11L, "source_image", null)).thenReturn(assetBinding(51L));
    }

    @Test
    void appliesResultWhileTheStableSourceStillMatches() {
        String fingerprint = ActorCardMainVisualResolver.SourceReference.forAsset(51L).fingerprint();
        when(cardMapper.update(any(), any())).thenReturn(1);

        assertTrue(service.applyIfCurrent(
                7L, 11L, fingerprint, "https://managed.test/expanded.png"));

        verify(cardMapper).update(any(), any());
    }

    @Test
    void ignoresAResultAfterTheSourceHasChanged() {
        String staleFingerprint = ActorCardMainVisualResolver.SourceReference.forAsset(52L).fingerprint();

        assertFalse(service.applyIfCurrent(
                7L, 11L, staleFingerprint, "https://managed.test/stale.png"));

        verify(cardMapper, never()).update(any(), any());
    }

    private ActorCardMediaBindingRespDTO assetBinding(Long assetId) {
        ActorCardMediaBindingItemRespDTO item = new ActorCardMediaBindingItemRespDTO();
        item.setAssetId(assetId);
        ActorCardMediaBindingRespDTO binding = new ActorCardMediaBindingRespDTO();
        binding.setSetId(91L);
        binding.setItems(List.of(item));
        return binding;
    }
}
