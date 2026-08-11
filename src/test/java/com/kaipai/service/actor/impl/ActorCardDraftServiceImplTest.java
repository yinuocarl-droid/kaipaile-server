package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.model.actor.card.dto.ActorCardRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardStepSaveReqDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.entity.ActorMediaAsset;
import com.kaipai.service.actor.ActorMediaAssetOwnershipVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 覆盖步骤 6 附件三态语义与判据切换——判据漏改会让附件已绑定却显示「未添加」。 */
class ActorCardDraftServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long CARD_ID = 31L;
    private static final Long ASSET_ID = 81L;

    private ActorCardMapper cardMapper;
    private ActorCardWorkMapper workMapper;
    private ActorMediaAssetMapper assetMapper;
    private ActorMediaAssetOwnershipVerifier verifier;
    private ActorCardDraftServiceImpl service;

    @BeforeEach void setUp() {
        cardMapper = mock(ActorCardMapper.class);
        workMapper = mock(ActorCardWorkMapper.class);
        assetMapper = mock(ActorMediaAssetMapper.class);
        verifier = mock(ActorMediaAssetOwnershipVerifier.class);
        service = new ActorCardDraftServiceImpl(cardMapper, workMapper, assetMapper, verifier, new ObjectMapper());
    }

    // ── 三态语义 ──────────────────────────────────────────────────────────────

    /** 不传 attachment 键 = 本次不涉及附件。跳过 / 下一步走这条，不得清空已绑定的附件。 */
    @Test void omittingTheAttachmentKeyLeavesAnExistingBindingUntouched() {
        ActorCard card = draftWithAsset(ASSET_ID);
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);

        service.saveStep(USER_ID, CARD_ID, stepOnly());

        assertEquals(ASSET_ID, persistedCard().getAttachmentAssetId());
        verify(verifier, never()).requireOwnedReadyPdf(any(), any());
    }

    @Test void bindingAnAssetVerifiesOwnershipBeforePersisting() {
        ActorCard card = draft();
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);

        service.saveStep(USER_ID, CARD_ID, withAttachment(ASSET_ID));

        verify(verifier).requireOwnedReadyPdf(USER_ID, ASSET_ID);
        assertEquals(ASSET_ID, persistedCard().getAttachmentAssetId());
    }

    /** 归属校验失败必须阻断落库，不能先写后校验。 */
    @Test void aRejectedAssetIsNeverPersisted() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());
        doThrow(new BizException(46012, "素材不存在"))
                .when(verifier).requireOwnedReadyPdf(USER_ID, ASSET_ID);

        assertEquals(46012, assertThrows(BizException.class,
                () -> service.saveStep(USER_ID, CARD_ID, withAttachment(ASSET_ID))).getCode());
        verify(cardMapper, never()).updateById(any());
    }

    /** 显式传 assetId=null 才是清空，且必须连历史 URL 一起清，否则判据仍为真、删不掉。 */
    @Test void anExplicitNullAssetIdClearsBothTheBindingAndTheLegacyUrl() {
        ActorCard card = draftWithAsset(ASSET_ID);
        card.setAttachmentUrl("https://legacy.example.com/resume.pdf");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);

        service.saveStep(USER_ID, CARD_ID, withAttachment(null));

        ActorCard saved = persistedCard();
        assertAll(
                () -> assertNull(saved.getAttachmentAssetId()),
                () -> assertNull(saved.getAttachmentUrl()),
                () -> verify(verifier, never()).requireOwnedReadyPdf(any(), any()));
    }

    /** attachmentUrl 已停写：老前端「跳过」提交的空串不得再被当成清空意图。 */
    @Test void aLegacyAttachmentUrlInTheRequestBodyIsIgnored() {
        ActorCard card = draftWithAsset(ASSET_ID);
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        ActorCardStepSaveReqDTO dto = stepOnly();
        dto.setAttachmentUrl("");

        service.saveStep(USER_ID, CARD_ID, dto);

        assertEquals(ASSET_ID, persistedCard().getAttachmentAssetId());
    }

    // ── 步骤 6 判据（本任务主要风险点） ────────────────────────────────────────

    @Test void stepSixCountsAsAddedWhenOnlyTheAssetIdIsPresent() {
        ActorCard card = draftWithAsset(ASSET_ID);
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(assetMapper.selectOne(any())).thenReturn(readyPdf());

        ActorCardRespDTO.StepStatus six = stepSix(service.getDraft(USER_ID, CARD_ID));

        assertAll(
                () -> assertEquals("done", six.getStatusCode()),
                () -> assertEquals("已添加", six.getStatusLabel()));
    }

    /** 老草稿只有 URL 没有 assetId，仍须显示已添加，否则用户失去删除入口。 */
    @Test void stepSixStillCountsALegacyUrlOnlyDraftAsAdded() {
        ActorCard card = draft();
        card.setAttachmentUrl("https://legacy.example.com/resume.pdf");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);

        assertEquals("done", stepSix(service.getDraft(USER_ID, CARD_ID)).getStatusCode());
    }

    @Test void stepSixIsEmptyWhenNeitherBindingNorLegacyUrlExists() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());

        ActorCardRespDTO.StepStatus six = stepSix(service.getDraft(USER_ID, CARD_ID));

        assertAll(
                () -> assertEquals("empty", six.getStatusCode()),
                () -> assertEquals("未添加", six.getStatusLabel()));
    }

    // ── 派生字段 ──────────────────────────────────────────────────────────────

    @Test void attachmentDerivedFieldsComeFromTheAssetRow() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draftWithAsset(ASSET_ID));
        when(assetMapper.selectOne(any())).thenReturn(readyPdf());

        ActorCardRespDTO dto = service.getDraft(USER_ID, CARD_ID);

        assertAll(
                () -> assertEquals(ASSET_ID, dto.getAttachmentAssetId()),
                () -> assertEquals("我的简历.pdf", dto.getAttachmentName()),
                () -> assertEquals(3, dto.getAttachmentPageCount()),
                () -> assertEquals("ready", dto.getAttachmentStatus()));
    }

    /** 素材已被删除时留空派生字段，不让整张卡读不出来。 */
    @Test void aMissingAssetRowLeavesDerivedFieldsBlankWithoutFailingTheWholeCard() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draftWithAsset(ASSET_ID));
        when(assetMapper.selectOne(any())).thenReturn(null);

        ActorCardRespDTO dto = service.getDraft(USER_ID, CARD_ID);

        assertAll(
                () -> assertEquals(ASSET_ID, dto.getAttachmentAssetId()),
                () -> assertNull(dto.getAttachmentName()),
                () -> assertNull(dto.getAttachmentStatus()),
                () -> assertEquals("done", stepSix(dto).getStatusCode()));
    }

    @Test void noAssetLookupHappensWhenTheCardHasNoAttachment() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());

        service.getDraft(USER_ID, CARD_ID);

        verify(assetMapper, never()).selectOne(any());
    }

    // ── 归属 ──────────────────────────────────────────────────────────────────

    @Test void anotherUsersCardCannotBeSaved() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());

        assertThrows(BizException.class, () -> service.saveStep(8L, CARD_ID, withAttachment(ASSET_ID)));
        verify(cardMapper, never()).updateById(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ActorCard draft() {
        ActorCard card = new ActorCard();
        card.setId(CARD_ID);
        card.setUserId(USER_ID);
        card.setStatus("draft");
        card.setCurrentStep(6);
        return card;
    }

    private ActorCard draftWithAsset(Long assetId) {
        ActorCard card = draft();
        card.setAttachmentAssetId(assetId);
        return card;
    }

    private ActorMediaAsset readyPdf() {
        ActorMediaAsset asset = new ActorMediaAsset();
        asset.setAssetId(ASSET_ID);
        asset.setUserId(USER_ID);
        asset.setMediaType("pdf");
        asset.setCategoryCode("resume");
        asset.setProcessStatus("ready");
        asset.setOriginalName("我的简历.pdf");
        asset.setPageCount(3);
        return asset;
    }

    private ActorCardStepSaveReqDTO stepOnly() {
        ActorCardStepSaveReqDTO dto = new ActorCardStepSaveReqDTO();
        dto.setCurrentStep(6);
        return dto;
    }

    private ActorCardStepSaveReqDTO withAttachment(Long assetId) {
        ActorCardStepSaveReqDTO dto = stepOnly();
        ActorCardStepSaveReqDTO.AttachmentBinding binding = new ActorCardStepSaveReqDTO.AttachmentBinding();
        binding.setAssetId(assetId);
        dto.setAttachment(binding);
        return dto;
    }

    private ActorCard persistedCard() {
        ArgumentCaptor<ActorCard> saved = ArgumentCaptor.forClass(ActorCard.class);
        verify(cardMapper).updateById(saved.capture());
        return saved.getValue();
    }

    private ActorCardRespDTO.StepStatus stepSix(ActorCardRespDTO dto) {
        return dto.getStepStatuses().stream()
                .filter(s -> s.getStep() == 6).findFirst().orElseThrow();
    }
}
