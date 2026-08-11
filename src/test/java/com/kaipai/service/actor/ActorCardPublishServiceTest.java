package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.card.dto.ActorCardListItemDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
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
    private ActorCardPublishService service;

    @BeforeEach void setUp() {
        cardMapper = mock(ActorCardMapper.class);
        service = new ActorCardPublishService(cardMapper, mock(ActorProfileMapper.class));
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
