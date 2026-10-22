package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.card.dto.ActorCardListItemDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 完成度判据必须与步骤标签同源。此前两处各写一份 hasText(attachmentUrl)，
 * 改绑定模型时只改一处，会让同一张卡在 Hub 页与列表页给出互相矛盾的结论。
 */
class ActorCardPublishServiceTest {

    private static final Long USER_ID = 7L;

    private ActorCardMapper cardMapper;
    private ActorCardWorkMapper cardWorkMapper;
    private ActorCardMediaBindingService mediaBindingService;
    private AiGeneratedImageStorage generatedImageStorage;
    private ActorCardPublishService service;

    @BeforeEach void setUp() {
        cardMapper = mock(ActorCardMapper.class);
        cardWorkMapper = mock(ActorCardWorkMapper.class);
        mediaBindingService = mock(ActorCardMediaBindingService.class);
        generatedImageStorage = mock(AiGeneratedImageStorage.class);
        when(generatedImageStorage.issueReadUrl(anyString(), any(Duration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cardWorkMapper.selectCount(any())).thenReturn(1L);
        service = new ActorCardPublishService(
                cardMapper, cardWorkMapper, mock(ActorProfileMapper.class),
                mediaBindingService, generatedImageStorage);
    }

    @Test void completionCountsAnAssetIdBoundAttachment() {
        ActorCard card = cardWithEverythingExceptAttachment();
        card.setAttachmentAssetId(81L);

        assertEquals(100, completionOf(card));
    }

    @Test void completionStillCountsALegacyUrlOnlyAttachment() {
        ActorCard card = cardWithEverythingExceptAttachment();
        card.setAttachmentUrl("https://legacy.example.com/resume.pdf");

        assertEquals(100, completionOf(card));
    }

    @Test void completionDropsTheAttachmentWhenNeitherFormIsPresent() {
        assertEquals(85, completionOf(cardWithEverythingExceptAttachment()));
    }

    @Test void listCoverFallsBackToAHistoricalBackgroundOnlyCard() {
        ActorCard card = cardWithEverythingExceptAttachment();
        card.setExpandedImageUrl(null);
        card.setBackgroundImageUrl("https://cdn.test/background.jpg");
        when(cardMapper.selectList(any())).thenReturn(List.of(card));

        assertEquals("https://cdn.test/background.jpg", service.list(USER_ID, null).get(0).getCoverImageUrl());
    }

    @Test void listCoverUsesTheBoundAssetMainVisual() {
        ActorCard card = cardWithEverythingExceptAttachment();
        card.setExpandedImageUrl(null);
        card.setSourceImageUrl(null);
        when(cardMapper.selectList(any())).thenReturn(List.of(card));
        var item = new com.kaipai.model.actor.card.dto.ActorCardMediaBindingItemRespDTO();
        item.setAssetId(81L);
        item.setPreviewUrl("https://signed.test/source");
        var binding = new com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO();
        binding.setSetId(91L);
        binding.setItems(List.of(item));
        when(mediaBindingService.list(USER_ID, card.getId(), "source_image", null)).thenReturn(binding);

        ActorCardListItemDTO result = service.list(USER_ID, null).get(0);

        assertEquals("https://signed.test/source", result.getCoverImageUrl());
        assertEquals(85, result.getCompletionPercentage());
    }

    @Test void listCoverSignsTheExpandedPrivateObject() {
        ActorCard card = cardWithEverythingExceptAttachment();
        card.setExpandedImageUrl("https://private.test/actor-card-expand/result.png");
        when(cardMapper.selectList(any())).thenReturn(List.of(card));
        when(generatedImageStorage.issueReadUrl(
                "https://private.test/actor-card-expand/result.png", Duration.ofMinutes(10)))
                .thenReturn("https://private.test/actor-card-expand/result.png?signature=test");

        assertEquals("https://private.test/actor-card-expand/result.png?signature=test",
                service.list(USER_ID, null).get(0).getCoverImageUrl());
    }

    @Test void listCoverFallsBackToTheBoundSourceWhenSigningFails() {
        ActorCard card = cardWithEverythingExceptAttachment();
        card.setExpandedImageUrl("https://private.test/actor-card-expand/result.png");
        when(cardMapper.selectList(any())).thenReturn(List.of(card));
        var item = new com.kaipai.model.actor.card.dto.ActorCardMediaBindingItemRespDTO();
        item.setAssetId(81L);
        item.setPreviewUrl("https://signed.test/source");
        var binding = new com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO();
        binding.setSetId(91L);
        binding.setItems(List.of(item));
        when(mediaBindingService.list(USER_ID, card.getId(), "source_image", null)).thenReturn(binding);
        when(generatedImageStorage.issueReadUrl(
                "https://private.test/actor-card-expand/result.png", Duration.ofMinutes(10)))
                .thenThrow(new IllegalStateException("signing unavailable"));

        assertEquals("https://signed.test/source", service.list(USER_ID, null).get(0).getCoverImageUrl());
    }

    @Test void structuredGenerationMarkerIsNeverExposedAsAListImage() {
        ActorCard card = cardWithEverythingExceptAttachment();
        card.setExpandedImageUrl(null);
        card.setGeneratedPreviewUrl("structured://actor-card/31");
        when(cardMapper.selectList(any())).thenReturn(List.of(card));

        assertNull(service.list(USER_ID, null).get(0).getCoverImageUrl());
    }

    @Test void requiredCompleteDraftIsReadyToGenerateButNotPublish() {
        ActorCard card = cardWithEverythingExceptAttachment();
        when(cardMapper.selectList(any())).thenReturn(List.of(card));

        ActorCardListItemDTO result = service.list(USER_ID, "draft").get(0);

        assertTrue(result.getGenerateReady());
        assertFalse(result.getPublishReady());
    }

    @Test void generatedDraftIsReadyToPreviewAndPublish() {
        ActorCard card = cardWithEverythingExceptAttachment();
        card.setGeneratedPreviewUrl("structured://actor-card/31");
        when(cardMapper.selectList(any())).thenReturn(List.of(card));

        ActorCardListItemDTO result = service.list(USER_ID, "draft").get(0);

        assertTrue(result.getGenerateReady());
        assertTrue(result.getPublishReady());
    }

    @Test void publishedCardDoesNotExposeDraftStageActions() {
        ActorCard card = cardWithEverythingExceptAttachment();
        card.setStatus("published");
        card.setGeneratedPreviewUrl("structured://actor-card/31");
        when(cardMapper.selectList(any())).thenReturn(List.of(card));

        ActorCardListItemDTO result = service.list(USER_ID, "published").get(0);

        assertFalse(result.getGenerateReady());
        assertFalse(result.getPublishReady());
    }

    @Test void missingWorkDisablesGenerationAndReducesCompletion() {
        ActorCard card = cardWithEverythingExceptAttachment();
        card.setAttachmentAssetId(81L);
        when(cardWorkMapper.selectCount(any())).thenReturn(0L);
        when(cardMapper.selectList(any())).thenReturn(List.of(card));

        ActorCardListItemDTO result = service.list(USER_ID, "draft").get(0);

        assertFalse(result.getGenerateReady());
        assertFalse(result.getPublishReady());
        assertEquals(85, result.getCompletionPercentage());
    }

    @Test void publishStillRejectsDraftWithoutGeneratedPreview() {
        ActorCard card = cardWithEverythingExceptAttachment();
        when(cardMapper.selectById(card.getId())).thenReturn(card);

        BizException error = assertThrows(BizException.class,
                () -> service.publish(USER_ID, card.getId()));

        assertEquals("演员卡尚未生成预览，请先完成 AI 生成步骤", error.getMessage());
        verify(cardMapper, never()).update(any(), any());
    }

    private int completionOf(ActorCard card) {
        when(cardMapper.selectList(any())).thenReturn(List.of(card));
        List<ActorCardListItemDTO> list = service.list(USER_ID, null);
        return list.get(0).getCompletionPercentage();
    }

    /** 7 步里除附件外全部满足，便于用百分比反推附件那一项是否被计入。 */
    private ActorCard cardWithEverythingExceptAttachment() {
        ActorCard card = new ActorCard();
        card.setId(31L);
        card.setUserId(USER_ID);
        card.setStatus("draft");
        card.setExpandedImageUrl("https://example.com/main.jpg");
        card.setProfileSnapshotJson("{\"name\":\"x\"}");
        card.setPhotosJson("[\"https://example.com/a.jpg\"]");
        card.setVideoUrl("https://example.com/v.mp4");
        card.setSettingsJson("{\"showAttachment\":true}");
        return card;
    }
}
