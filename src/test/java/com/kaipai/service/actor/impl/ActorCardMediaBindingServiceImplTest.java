package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardMediaItemMapper;
import com.kaipai.mapper.actor.ActorCardMediaSetMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingItemReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardMediaItem;
import com.kaipai.model.actor.card.entity.ActorCardMediaSet;
import com.kaipai.model.actor.card.entity.ActorCardWork;
import com.kaipai.model.actor.entity.ActorMediaAsset;
import com.kaipai.model.actor.dto.ActorAssetAccessUrlRespDTO;
import com.kaipai.service.actor.ActorMediaAssetService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActorCardMediaBindingServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long CARD_ID = 11L;

    private ActorCardMapper cardMapper;
    private ActorCardWorkMapper workMapper;
    private ActorCardMediaSetMapper setMapper;
    private ActorCardMediaItemMapper itemMapper;
    private ActorMediaAssetMapper assetMapper;
    private ActorMediaAssetService assetService;
    private ActorCardMediaBindingServiceImpl service;

    @BeforeEach
    void setUp() {
        cardMapper = mock(ActorCardMapper.class);
        workMapper = mock(ActorCardWorkMapper.class);
        setMapper = mock(ActorCardMediaSetMapper.class);
        itemMapper = mock(ActorCardMediaItemMapper.class);
        assetMapper = mock(ActorMediaAssetMapper.class);
        assetService = mock(ActorMediaAssetService.class);
        service = new ActorCardMediaBindingServiceImpl(
                cardMapper, workMapper, setMapper, itemMapper, assetMapper, assetService, new ObjectMapper());
    }

    @Test
    void returnsLegacyItemsWhenNoBindingSetExists() {
        ActorCard card = ownedDraft();
        card.setPhotosJson("[\"https://example.test/a.jpg\",\"https://example.test/b.jpg\"]");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(setMapper.selectActive(CARD_ID, ActorCardMediaBindingServiceImpl.LIFE_PHOTO, null))
                .thenReturn(null);

        ActorCardMediaBindingRespDTO result = service.list(
                USER_ID, CARD_ID, ActorCardMediaBindingServiceImpl.LIFE_PHOTO, null);

        assertNull(result.getSetId());
        assertEquals(2, result.getItems().size());
        assertEquals("https://example.test/a.jpg", result.getItems().get(0).getLegacyUrl());
        assertEquals(1, result.getItems().get(1).getSortOrder());
    }

    @Test
    void activeEmptySetSuppressesLegacyFallback() {
        ActorCard card = ownedDraft();
        card.setSourceImageUrl("https://example.test/legacy.jpg");
        ActorCardMediaSet set = mediaSet(23L, ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null);
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(setMapper.selectActive(CARD_ID, ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null))
                .thenReturn(set);
        when(itemMapper.selectActiveBySetId(23L)).thenReturn(List.of());

        ActorCardMediaBindingRespDTO result = service.list(
                USER_ID, CARD_ID, ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null);

        assertEquals(23L, result.getSetId());
        assertEquals(List.of(), result.getItems());
    }

    @Test
    void rejectsDuplicateAssetsBeforeLockingAssets() {
        when(cardMapper.selectOwnedForUpdate(USER_ID, CARD_ID)).thenReturn(ownedDraft());
        ActorCardMediaBindingReqDTO request = request(
                ActorCardMediaBindingServiceImpl.LIFE_PHOTO,
                null,
                item(51L, 0), item(51L, 1));

        assertThrows(BizException.class, () -> service.replace(USER_ID, CARD_ID, request));

        verify(assetMapper, never()).selectOwnedActiveByIdsForUpdate(eq(USER_ID), anyList());
    }

    @Test
    void rejectsMediaTypeThatDoesNotMatchUsage() {
        when(cardMapper.selectOwnedForUpdate(USER_ID, CARD_ID)).thenReturn(ownedDraft());
        ActorMediaAsset video = asset(51L, "video", "ready");
        when(assetMapper.selectOwnedActiveByIdsForUpdate(USER_ID, List.of(51L))).thenReturn(List.of(video));

        BizException error = assertThrows(BizException.class, () -> service.replace(
                USER_ID,
                CARD_ID,
                request(ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null, item(51L, 0))));

        assertEquals(46013, error.getCode());
    }

    @Test
    void rejectsMissingOrForeignAssetsWithoutLeakingTheirExistence() {
        when(cardMapper.selectOwnedForUpdate(USER_ID, CARD_ID)).thenReturn(ownedDraft());
        when(assetMapper.selectOwnedActiveByIdsForUpdate(USER_ID, List.of(51L))).thenReturn(List.of());

        BizException error = assertThrows(BizException.class, () -> service.replace(
                USER_ID,
                CARD_ID,
                request(ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null, item(51L, 0))));

        assertEquals(46012, error.getCode());
    }

    @Test
    void rejectsAssetsThatAreNotReadyForBinding() {
        when(cardMapper.selectOwnedForUpdate(USER_ID, CARD_ID)).thenReturn(ownedDraft());
        ActorMediaAsset processingPhoto = asset(51L, "photo", "processing");
        when(assetMapper.selectOwnedActiveByIdsForUpdate(USER_ID, List.of(51L)))
                .thenReturn(List.of(processingPhoto));

        BizException error = assertThrows(BizException.class, () -> service.replace(
                USER_ID,
                CARD_ID,
                request(ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null, item(51L, 0))));

        assertEquals(46013, error.getCode());
    }

    @Test
    void rejectsNonContinuousSortOrdersBeforeLockingAssets() {
        when(cardMapper.selectOwnedForUpdate(USER_ID, CARD_ID)).thenReturn(ownedDraft());

        assertThrows(BizException.class, () -> service.replace(
                USER_ID,
                CARD_ID,
                request(
                        ActorCardMediaBindingServiceImpl.LIFE_PHOTO,
                        null,
                        item(51L, 0),
                        item(52L, 2))));

        verify(assetMapper, never()).selectOwnedActiveByIdsForUpdate(eq(USER_ID), anyList());
    }

    @Test
    void rejectsMoreThanTwelveLifePhotosBeforeLockingAssets() {
        when(cardMapper.selectOwnedForUpdate(USER_ID, CARD_ID)).thenReturn(ownedDraft());
        List<ActorCardMediaBindingItemReqDTO> items = new ArrayList<>();
        for (int sortOrder = 0; sortOrder < 13; sortOrder++) {
            items.add(item(51L + sortOrder, sortOrder));
        }
        ActorCardMediaBindingReqDTO request = new ActorCardMediaBindingReqDTO();
        request.setUsage(ActorCardMediaBindingServiceImpl.LIFE_PHOTO);
        request.setItems(items);

        assertThrows(BizException.class, () -> service.replace(USER_ID, CARD_ID, request));

        verify(assetMapper, never()).selectOwnedActiveByIdsForUpdate(eq(USER_ID), anyList());
    }

    @Test
    void workStillRequiresWorkOwnedByTheCard() {
        when(cardMapper.selectOwnedForUpdate(USER_ID, CARD_ID)).thenReturn(ownedDraft());
        when(workMapper.selectActiveByIdAndCard(CARD_ID, 99L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.replace(
                USER_ID,
                CARD_ID,
                request(ActorCardMediaBindingServiceImpl.WORK_STILL, 99L, item(51L, 0))));

        verify(assetMapper, never()).selectOwnedActiveByIdsForUpdate(eq(USER_ID), anyList());
    }

    @Test
    void savesOrderedReadyAssetsAndReturnsMetadata() {
        when(cardMapper.selectOwnedForUpdate(USER_ID, CARD_ID)).thenReturn(ownedDraft());
        ActorMediaAsset first = asset(51L, "photo", "ready");
        first.setOriginalName("first.jpg");
        ActorMediaAsset second = asset(52L, "photo", "ready");
        second.setOriginalName("second.jpg");
        when(assetMapper.selectOwnedActiveByIdsForUpdate(USER_ID, List.of(51L, 52L)))
                .thenReturn(List.of(first, second));
        when(setMapper.selectActiveForUpdate(
                CARD_ID, ActorCardMediaBindingServiceImpl.LIFE_PHOTO, null)).thenReturn(null);
        when(setMapper.insert(any(ActorCardMediaSet.class))).thenAnswer(invocation -> {
            ActorCardMediaSet set = invocation.getArgument(0);
            set.setSetId(23L);
            return 1;
        });
        List<ActorCardMediaItem> saved = new ArrayList<>();
        when(itemMapper.insert(any(ActorCardMediaItem.class))).thenAnswer(invocation -> {
            ActorCardMediaItem item = invocation.getArgument(0);
            item.setItemId(100L + saved.size());
            saved.add(item);
            return 1;
        });
        when(itemMapper.selectActiveBySetId(23L)).thenAnswer(invocation -> new ArrayList<>(saved));
        Instant expiresAt = Instant.parse("2026-08-18T13:00:00Z");
        when(assetService.issueOwnerAccessUrl(USER_ID, 51L))
                .thenReturn(new ActorAssetAccessUrlRespDTO("https://signed.test/51", expiresAt));
        when(assetService.issueOwnerAccessUrl(USER_ID, 52L))
                .thenReturn(new ActorAssetAccessUrlRespDTO("https://signed.test/52", expiresAt));

        ActorCardMediaBindingRespDTO result = service.replace(
                USER_ID,
                CARD_ID,
                request(ActorCardMediaBindingServiceImpl.LIFE_PHOTO, null, item(51L, 0), item(52L, 1)));

        assertEquals(23L, result.getSetId());
        assertEquals(List.of(51L, 52L), result.getItems().stream().map(item -> item.getAssetId()).toList());
        assertEquals("first.jpg", result.getItems().get(0).getOriginalName());
        assertEquals("https://signed.test/51", result.getItems().get(0).getPreviewUrl());
        assertEquals(1, result.getItems().get(1).getSortOrder());
        verify(itemMapper).deleteActiveBySetId(23L);
    }

    @Test
    void changingTheSourceAssetClearsThePreviousExpandedImageInTheSameTransaction() {
        ActorCard card = ownedDraft();
        card.setExpandedImageUrl("https://example.test/old-expanded.jpg");
        card.setExpandedSourceFingerprint("old-source-fingerprint");
        when(cardMapper.selectOwnedForUpdate(USER_ID, CARD_ID)).thenReturn(card);
        when(cardMapper.update(isNull(), any())).thenReturn(1);
        ActorMediaAsset source = asset(51L, "photo", "ready");
        when(assetMapper.selectOwnedActiveByIdsForUpdate(USER_ID, List.of(51L)))
                .thenReturn(List.of(source));
        when(setMapper.selectActiveForUpdate(
                CARD_ID, ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null)).thenReturn(null);
        when(setMapper.insert(any(ActorCardMediaSet.class))).thenAnswer(invocation -> {
            ActorCardMediaSet set = invocation.getArgument(0);
            set.setSetId(23L);
            return 1;
        });
        List<ActorCardMediaItem> saved = new ArrayList<>();
        when(itemMapper.insert(any(ActorCardMediaItem.class))).thenAnswer(invocation -> {
            ActorCardMediaItem item = invocation.getArgument(0);
            saved.add(item);
            return 1;
        });
        when(itemMapper.selectActiveBySetId(23L)).thenAnswer(invocation -> new ArrayList<>(saved));
        when(assetService.issueOwnerAccessUrl(USER_ID, 51L)).thenReturn(
                new ActorAssetAccessUrlRespDTO(
                        "https://signed.test/51", Instant.parse("2026-08-18T13:00:00Z")));

        service.replace(
                USER_ID,
                CARD_ID,
                request(ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null, item(51L, 0)));

        assertNull(card.getExpandedImageUrl());
        assertNull(card.getExpandedSourceFingerprint());
        verify(cardMapper).update(isNull(), any());
    }

    @Test
    void creatingAnEmptySourceOverrideAlsoClearsThePreviousExpandedImage() {
        ActorCard card = ownedDraft();
        card.setSourceImageUrl("https://legacy.test/source.jpg");
        card.setExpandedImageUrl("https://legacy.test/expanded.jpg");
        card.setExpandedSourceFingerprint("legacy-source-fingerprint");
        when(cardMapper.selectOwnedForUpdate(USER_ID, CARD_ID)).thenReturn(card);
        when(cardMapper.update(isNull(), any())).thenReturn(1);
        when(setMapper.selectActiveForUpdate(
                CARD_ID, ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null)).thenReturn(null);
        when(setMapper.insert(any(ActorCardMediaSet.class))).thenAnswer(invocation -> {
            ActorCardMediaSet set = invocation.getArgument(0);
            set.setSetId(23L);
            return 1;
        });
        when(itemMapper.selectActiveBySetId(23L)).thenReturn(List.of());

        service.replace(
                USER_ID,
                CARD_ID,
                request(ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null));

        assertNull(card.getExpandedImageUrl());
        assertNull(card.getExpandedSourceFingerprint());
        verify(cardMapper).update(isNull(), any());
    }

    @Test
    void copyBindingsRemapsWorkSetsAndPreservesItems() {
        ActorCard source = ownedDraft();
        source.setId(11L);
        ActorCard target = ownedDraft();
        target.setId(12L);
        when(cardMapper.selectById(11L)).thenReturn(source);
        when(cardMapper.selectOwnedForUpdate(USER_ID, 12L)).thenReturn(target);
        ActorCardMediaSet sourceSet = mediaSet(23L, ActorCardMediaBindingServiceImpl.WORK_STILL, 101L);
        when(setMapper.selectActiveByCardForUpdate(11L)).thenReturn(List.of(sourceSet));
        ActorCardMediaItem sourceItem = new ActorCardMediaItem();
        sourceItem.setItemId(31L);
        sourceItem.setSetId(23L);
        sourceItem.setAssetId(51L);
        sourceItem.setSortOrder(0);
        when(itemMapper.selectActiveBySetId(23L)).thenReturn(List.of(sourceItem));
        when(setMapper.insert(any(ActorCardMediaSet.class))).thenAnswer(invocation -> {
            ActorCardMediaSet set = invocation.getArgument(0);
            set.setSetId(24L);
            return 1;
        });
        when(itemMapper.insert(any(ActorCardMediaItem.class))).thenReturn(1);

        service.copyBindings(USER_ID, 11L, 12L, Map.of(101L, 202L));

        var setCaptor = org.mockito.ArgumentCaptor.forClass(ActorCardMediaSet.class);
        verify(setMapper).insert(setCaptor.capture());
        assertEquals(12L, setCaptor.getValue().getCardId());
        assertEquals(202L, setCaptor.getValue().getCardWorkId());
        var itemCaptor = org.mockito.ArgumentCaptor.forClass(ActorCardMediaItem.class);
        verify(itemMapper).insert(itemCaptor.capture());
        assertEquals(24L, itemCaptor.getValue().getSetId());
        assertEquals(51L, itemCaptor.getValue().getAssetId());
        assertNull(itemCaptor.getValue().getItemId());
        assertEquals(0, itemCaptor.getValue().getSortOrder());
    }

    private ActorCard ownedDraft() {
        ActorCard card = new ActorCard();
        card.setId(CARD_ID);
        card.setUserId(USER_ID);
        card.setStatus("draft");
        return card;
    }

    private ActorCardMediaSet mediaSet(Long setId, String usage, Long workId) {
        ActorCardMediaSet set = new ActorCardMediaSet();
        set.setSetId(setId);
        set.setCardId(CARD_ID);
        set.setCardWorkId(workId);
        set.setUsageCode(usage);
        return set;
    }

    private ActorMediaAsset asset(Long assetId, String mediaType, String status) {
        ActorMediaAsset asset = new ActorMediaAsset();
        asset.setAssetId(assetId);
        asset.setUserId(USER_ID);
        asset.setMediaType(mediaType);
        asset.setProcessStatus(status);
        return asset;
    }

    private ActorCardMediaBindingReqDTO request(
            String usage,
            Long workId,
            ActorCardMediaBindingItemReqDTO... items) {
        ActorCardMediaBindingReqDTO request = new ActorCardMediaBindingReqDTO();
        request.setUsage(usage);
        request.setWorkId(workId);
        request.setItems(List.of(items));
        return request;
    }

    private ActorCardMediaBindingItemReqDTO item(Long assetId, int sortOrder) {
        ActorCardMediaBindingItemReqDTO item = new ActorCardMediaBindingItemReqDTO();
        item.setAssetId(assetId);
        item.setSortOrder(sortOrder);
        return item;
    }
}
