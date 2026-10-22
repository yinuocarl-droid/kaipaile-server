package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.ai.ActorAiProfileCardTaskMapper;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingItemRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.ai.entity.ActorAiProfileCardTask;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

class ActorCardGenerateServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long CARD_ID = 31L;

    private ActorCardMapper cardMapper;
    private ActorCardWorkMapper workMapper;
    private ActorAiProfileCardTaskMapper taskMapper;
    private ActorCardMediaBindingService mediaBindingService;
    private ActorCardGenerateService service;

    @BeforeEach
    void setUp() {
        cardMapper = mock(ActorCardMapper.class);
        workMapper = mock(ActorCardWorkMapper.class);
        taskMapper = mock(ActorAiProfileCardTaskMapper.class);
        mediaBindingService = mock(ActorCardMediaBindingService.class);
        service = new ActorCardGenerateService(cardMapper, workMapper, taskMapper, mediaBindingService);
        ReflectionTestUtils.setField(service, "taskExecutor", (Executor) task -> { });
    }

    @Test
    void anAssetSourcePassesTheRequiredGateWithoutPersistingItsSignedPreview() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(requiredDraft());
        when(workMapper.selectCount(any())).thenReturn(1L);
        when(mediaBindingService.list(USER_ID, CARD_ID, "source_image", null))
                .thenReturn(assetBinding());

        var response = service.submit(USER_ID, CARD_ID);

        ArgumentCaptor<ActorAiProfileCardTask> task =
                ArgumentCaptor.forClass(ActorAiProfileCardTask.class);
        verify(taskMapper).insert(task.capture());
        assertNotNull(response.getTaskId());
        assertNull(task.getValue().getSourceImageUrl());
    }

    @Test
    void anAssetSourceWritesAStableStructuredGenerationMarker() {
        assertEquals("structured://actor-card/31",
                ActorCardGenerateService.resolveGeneratedPreviewUrl(CARD_ID, null));
        assertEquals("https://cdn.test/expanded.jpg",
                ActorCardGenerateService.resolveGeneratedPreviewUrl(
                        CARD_ID, " https://cdn.test/expanded.jpg "));
    }

    @Test
    void anActiveEmptySourceSetSuppressesTheLegacyUrlAtTheGenerateGate() {
        ActorCard card = requiredDraft();
        card.setSourceImageUrl("https://legacy.test/source.jpg");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        ActorCardMediaBindingRespDTO empty = new ActorCardMediaBindingRespDTO();
        empty.setSetId(91L);
        empty.setUsage("source_image");
        empty.setItems(List.of());
        when(mediaBindingService.list(USER_ID, CARD_ID, "source_image", null)).thenReturn(empty);

        assertThrows(BizException.class, () -> service.submit(USER_ID, CARD_ID));

        verify(taskMapper, never()).insert(any());
    }

    @Test
    void aTemplateMainVisualPassesTheGateWithAnExplicitEmptyAssetBinding() {
        ActorCard card = requiredDraft();
        card.setBackgroundImageUrl("https://cdn.test/template.jpg");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(workMapper.selectCount(any())).thenReturn(1L);
        ActorCardMediaBindingRespDTO empty = new ActorCardMediaBindingRespDTO();
        empty.setSetId(91L);
        empty.setUsage("source_image");
        empty.setItems(List.of());
        when(mediaBindingService.list(USER_ID, CARD_ID, "source_image", null)).thenReturn(empty);

        service.submit(USER_ID, CARD_ID);

        ArgumentCaptor<ActorAiProfileCardTask> task =
                ArgumentCaptor.forClass(ActorAiProfileCardTask.class);
        verify(taskMapper).insert(task.capture());
        assertEquals("https://cdn.test/template.jpg", task.getValue().getSourceImageUrl());
    }

    @Test
    void aMissingMigrationFallsBackToTheLegacySourceContract() {
        ActorCard card = requiredDraft();
        card.setSourceImageUrl("https://legacy.test/source.jpg");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(workMapper.selectCount(any())).thenReturn(1L);
        when(mediaBindingService.list(USER_ID, CARD_ID, "source_image", null))
                .thenThrow(new DataAccessResourceFailureException("media table missing"));

        service.submit(USER_ID, CARD_ID);

        ArgumentCaptor<ActorAiProfileCardTask> task =
                ArgumentCaptor.forClass(ActorAiProfileCardTask.class);
        verify(taskMapper).insert(task.capture());
        assertEquals("https://legacy.test/source.jpg", task.getValue().getSourceImageUrl());
    }

    @Test
    void aMissingMigrationAlsoFallsBackToAHistoricalBackgroundOnlyCard() {
        ActorCard card = requiredDraft();
        card.setBackgroundImageUrl("https://legacy.test/background.jpg");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(workMapper.selectCount(any())).thenReturn(1L);
        when(mediaBindingService.list(USER_ID, CARD_ID, "source_image", null))
                .thenThrow(new DataAccessResourceFailureException("media table missing"));

        service.submit(USER_ID, CARD_ID);

        ArgumentCaptor<ActorAiProfileCardTask> task =
                ArgumentCaptor.forClass(ActorAiProfileCardTask.class);
        verify(taskMapper).insert(task.capture());
        assertEquals("https://legacy.test/background.jpg", task.getValue().getSourceImageUrl());
    }

    private ActorCard requiredDraft() {
        ActorCard card = new ActorCard();
        card.setId(CARD_ID);
        card.setUserId(USER_ID);
        card.setStatus("draft");
        card.setStyle("classic");
        card.setProfileSnapshotJson("{}");
        return card;
    }

    private ActorCardMediaBindingRespDTO assetBinding() {
        ActorCardMediaBindingItemRespDTO item = new ActorCardMediaBindingItemRespDTO();
        item.setAssetId(81L);
        item.setPreviewUrl("https://signed.test/source");
        item.setSortOrder(0);
        ActorCardMediaBindingRespDTO binding = new ActorCardMediaBindingRespDTO();
        binding.setSetId(91L);
        binding.setUsage("source_image");
        binding.setItems(List.of(item));
        return binding;
    }
}
