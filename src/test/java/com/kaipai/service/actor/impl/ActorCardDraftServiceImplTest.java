package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardVisualInputMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingItemRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardStepSaveReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardWorksReplaceReqDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardVisualInput;
import com.kaipai.model.actor.card.entity.ActorCardWork;
import com.kaipai.model.actor.entity.ActorMediaAsset;
import com.kaipai.service.actor.ActorCardMediaBindingService;
import com.kaipai.service.actor.ActorCardDecorationAssignmentService;
import com.kaipai.service.actor.ActorMediaAssetService;
import com.kaipai.service.actor.ActorMediaAssetOwnershipVerifier;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.BadSqlGrammarException;

/** 覆盖步骤 6 附件三态语义与判据切换——判据漏改会让附件已绑定却显示「未添加」。 */
class ActorCardDraftServiceImplTest {

    @Test void badgeSnapshotNormalizesOnlyNewFieldAndPreservesOtherFields() throws Exception {
        ActorCard card = draft();
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        var request = stepOnly();
        request.setProfileSnapshotJson("{\"heroBadgeText\":\"  剧宣合作  \",\"name\":\"  unchanged  \",\"custom\":12}");
        service.saveStep(USER_ID,CARD_ID,request);
        var saved = new ObjectMapper().readTree(persistedCard().getProfileSnapshotJson());
        assertEquals("剧宣合作",saved.path("heroBadgeText").asText());
        assertEquals("  unchanged  ",saved.path("name").asText());
        assertEquals(12,saved.path("custom").asInt());
    }

    @Test void badgeSnapshotRejectsInvalidTypesControlsAndOverlengthBeforeWriting() {
        for (String value : List.of("null","12","true","[]","\"<script>\"","\"a\\nb\"","\"" + "a".repeat(41) + "\"")) {
            when(cardMapper.selectById(CARD_ID)).thenReturn(draft());
            var request = stepOnly(); request.setProfileSnapshotJson("{\"heroBadgeText\":" + value + "}");
            assertThrows(BizException.class, () -> service.saveStep(USER_ID,CARD_ID,request));
        }
        verify(cardMapper, never()).updateById(any());
    }

    @Test void publishedCardCannotChangeAddOrRemoveBadgeThroughStep() {
        for (String snapshot : List.of("{\"heroBadgeText\":\"改文案\"}","{\"name\":\"actor\"}","{\"heroBadgeText\":\"\"}")) {
            ActorCard card = draft(); card.setStatus("published");
            card.setProfileSnapshotJson("{\"heroBadgeText\":\"锁定文案\"}");
            when(cardMapper.selectById(CARD_ID)).thenReturn(card);
            var request = stepOnly(); request.setProfileSnapshotJson(snapshot);
            assertEquals(409,assertThrows(BizException.class, () -> service.saveStep(USER_ID,CARD_ID,request)).getCode());
        }
        verify(cardMapper, never()).updateById(any());
    }

    @Test void publishedOldSnapshotCannotAcquireEvenEmptyBadgeAndUnchangedBadgeCanRoundTrip() {
        ActorCard card=draft(); card.setStatus("published"); card.setProfileSnapshotJson("{\"name\":\"old\"}");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        var request=stepOnly(); request.setProfileSnapshotJson("{\"heroBadgeText\":\"\"}");
        assertThrows(BizException.class, () -> service.saveStep(USER_ID,CARD_ID,request));
        verify(cardMapper,never()).updateById(any());
        card.setProfileSnapshotJson("{\"heroBadgeText\":\"locked\"}");
        request.setProfileSnapshotJson("{\"heroBadgeText\":\"locked\",\"name\":\"unchanged-old-behavior\"}");
        service.saveStep(USER_ID,CARD_ID,request);
        assertEquals(request.getProfileSnapshotJson(),persistedCard().getProfileSnapshotJson());
    }

    @Test void absentBadgeLeavesLegacySnapshotBytesUnchangedAndStepOnlyDoesNotRefillEmptyBadge() {
        ActorCard card=draft(); card.setProfileSnapshotJson("{\"heroBadgeText\":\"\"}");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        service.saveStep(USER_ID,CARD_ID,stepOnly());
        assertEquals("{\"heroBadgeText\":\"\"}",persistedCard().getProfileSnapshotJson());
        var request=stepOnly(); request.setProfileSnapshotJson("{ \"name\" : \" legacy \" }");
        service.saveStep(USER_ID,CARD_ID,request);
        assertEquals(request.getProfileSnapshotJson(),card.getProfileSnapshotJson());
    }

    private static final Long USER_ID = 7L;
    private static final Long CARD_ID = 31L;
    private static final Long ASSET_ID = 81L;

    private ActorCardMapper cardMapper;
    private ActorCardWorkMapper workMapper;
    private ActorMediaAssetMapper assetMapper;
    private ActorMediaAssetOwnershipVerifier verifier;
    private ActorCardMediaBindingService mediaBindingService;
    private ActorCardVisualInputMapper visualInputMapper;
    private ActorCardDecorationAssignmentService decorationAssignmentService;
    private ActorMediaAssetService actorMediaAssetService;
    private AiGeneratedImageStorage generatedImageStorage;
    private ActorCardDraftServiceImpl service;

    @BeforeEach void setUp() {
        cardMapper = mock(ActorCardMapper.class);
        workMapper = mock(ActorCardWorkMapper.class);
        assetMapper = mock(ActorMediaAssetMapper.class);
        verifier = mock(ActorMediaAssetOwnershipVerifier.class);
        mediaBindingService = mock(ActorCardMediaBindingService.class);
        visualInputMapper = mock(ActorCardVisualInputMapper.class);
        decorationAssignmentService = mock(ActorCardDecorationAssignmentService.class);
        actorMediaAssetService = mock(ActorMediaAssetService.class);
        generatedImageStorage = mock(AiGeneratedImageStorage.class);
        when(cardMapper.selectOwnedForUpdate(anyLong(), anyLong())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            Long cardId = invocation.getArgument(1);
            ActorCard selected = cardMapper.selectById(cardId);
            return selected != null && userId.equals(selected.getUserId()) ? selected : null;
        });
        service = new ActorCardDraftServiceImpl(
                cardMapper, workMapper, assetMapper, verifier, mediaBindingService,
                visualInputMapper, decorationAssignmentService, actorMediaAssetService,
                generatedImageStorage, new ObjectMapper());
    }

    @Test
    void aNewDraftLocksTheDefaultDecorationThatExistsAtCreationTime() {
        when(cardMapper.insert(any(ActorCard.class))).thenAnswer(invocation -> {
            ActorCard card = invocation.getArgument(0);
            card.setId(CARD_ID);
            return 1;
        });

        ActorCardRespDTO created = service.createDraft(USER_ID);

        assertEquals(CARD_ID, created.getId());
        assertNull(created.getProfileSnapshotJson(), "Old clients must still initialize the entire empty profile step");
        assertEquals("pending",created.getStepStatuses().get(1).getStatusCode());
        verify(decorationAssignmentService).selectForMode(USER_ID, CARD_ID, "person_ai", null);
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

    @Test void mediaBindingsCountTowardStepsOneFourAndFive() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());
        when(mediaBindingService.list(USER_ID, CARD_ID, "source_image", null))
                .thenReturn(binding("source_image", 1));
        when(mediaBindingService.list(USER_ID, CARD_ID, "life_photo", null))
                .thenReturn(binding("life_photo", 2));
        when(mediaBindingService.list(USER_ID, CARD_ID, "video_resume", null))
                .thenReturn(binding("video_resume", 1));

        ActorCardRespDTO dto = service.getDraft(USER_ID, CARD_ID);

        assertAll(
                () -> assertEquals("done", step(dto, 1).getStatusCode()),
                () -> assertEquals("done", step(dto, 4).getStatusCode()),
                () -> assertEquals("2张", step(dto, 4).getStatusLabel()),
                () -> assertEquals("done", step(dto, 5).getStatusCode()));
    }

    @Test void activeEmptyBindingsSuppressLegacyStepStatusFallback() {
        ActorCard card = draft();
        card.setSourceImageUrl("https://legacy.test/source.jpg");
        card.setPhotosJson("[\"https://legacy.test/life.jpg\"]");
        card.setVideoUrl("https://legacy.test/video.mp4");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(mediaBindingService.list(eq(USER_ID), eq(CARD_ID), any(), isNull()))
                .thenAnswer(invocation -> binding(invocation.getArgument(2), 0));

        ActorCardRespDTO dto = service.getDraft(USER_ID, CARD_ID);

        assertAll(
                () -> assertEquals("empty", step(dto, 1).getStatusCode()),
                () -> assertEquals("0张", step(dto, 4).getStatusLabel()),
                () -> assertEquals("empty", step(dto, 5).getStatusCode()));
    }

    @Test void anExplicitEmptyAssetBindingStillCountsATemplateMainVisual() {
        ActorCard card = draft();
        card.setSourceImageUrl("https://legacy.test/stale-source.jpg");
        card.setBackgroundImageUrl("https://cdn.test/template.jpg");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(mediaBindingService.list(eq(USER_ID), eq(CARD_ID), any(), isNull()))
                .thenAnswer(invocation -> binding(invocation.getArgument(2), 0));

        assertEquals("done", step(service.getDraft(USER_ID, CARD_ID), 1).getStatusCode());
    }

    @Test
    void configuredVisualInputDoesNotTreatBackgroundOrLegacyMirrorsAsAPersonSource() {
        ActorCard card = draft();
        card.setSourceImageUrl("https://legacy.test/source.jpg");
        card.setBackgroundImageUrl("https://cdn.test/background.jpg");
        card.setExpandedImageUrl("https://cdn.test/legacy-expanded.jpg");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(mediaBindingService.list(USER_ID, CARD_ID, "source_image", null))
                .thenReturn(binding("source_image", 1));
        ActorCardVisualInput visual = visualInput("current", "stale");
        visual.setBackgroundId(55L);
        visual.setGeneratedImageUrl("https://cdn.test/stale-generated.jpg");
        when(visualInputMapper.selectByCardId(CARD_ID)).thenReturn(visual);

        assertEquals("empty", step(service.getDraft(USER_ID, CARD_ID), 1).getStatusCode());
    }

    @Test
    void configuredVisualInputCountsEitherStablePersonSourceAsStepOneDone() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());
        ActorCardVisualInput visual = visualInput("current", null);
        visual.setPersonAssetId(81L);
        when(visualInputMapper.selectByCardId(CARD_ID)).thenReturn(visual);

        assertEquals("done", step(service.getDraft(USER_ID, CARD_ID), 1).getStatusCode());

        visual.setPersonAssetId(null);
        visual.setPersonImageUrl("https://managed.test/person.jpg");
        assertEquals("done", step(service.getDraft(USER_ID, CARD_ID), 1).getStatusCode());
    }

    @Test
    void configuredVisualInputCountsGeneratedResultOnlyForTheCurrentFingerprint() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());
        ActorCardVisualInput visual = visualInput("current", "stale");
        visual.setGeneratedImageUrl("https://managed.test/generated.jpg");
        when(visualInputMapper.selectByCardId(CARD_ID)).thenReturn(visual);

        assertEquals("empty", step(service.getDraft(USER_ID, CARD_ID), 1).getStatusCode());

        visual.setGeneratedFingerprint("current");
        assertEquals("done", step(service.getDraft(USER_ID, CARD_ID), 1).getStatusCode());
    }

    @Test
    void visualInputRejectsEveryLegacyMainVisualFieldBeforeAnyMutation() {
        ActorCard card = draft();
        card.setStyle("classic");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(visualInputMapper.selectByCardId(CARD_ID)).thenReturn(visualInput("current", null));
        List<Consumer<ActorCardStepSaveReqDTO>> legacyWrites = List.of(
                dto -> dto.setSourceImageUrl("https://legacy.test/person.jpg"),
                dto -> dto.setBackgroundImageUrl("https://legacy.test/background.jpg"),
                dto -> dto.setExpandedImageUrl("https://legacy.test/generated.jpg"));

        for (Consumer<ActorCardStepSaveReqDTO> legacyWrite : legacyWrites) {
            ActorCardStepSaveReqDTO request = new ActorCardStepSaveReqDTO();
            request.setCurrentStep(1);
            request.setStyle("urban");
            legacyWrite.accept(request);

            BizException error = assertThrows(BizException.class,
                    () -> service.saveStep(USER_ID, CARD_ID, request));

            assertAll(
                    () -> assertEquals(409, error.getCode()),
                    () -> assertEquals("当前演员卡已启用新版首图，请使用新版首图入口", error.getMessage()),
                    () -> assertEquals(6, card.getCurrentStep()),
                    () -> assertEquals("classic", card.getStyle()));
        }
        verify(cardMapper, never()).updateById(any());
        verify(verifier, never()).requireOwnedReadyPdf(any(), any());
    }

    @Test
    void visualInputStillAllowsStyleAndProgressOnlySave() {
        ActorCard card = draft();
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(visualInputMapper.selectByCardId(CARD_ID)).thenReturn(visualInput("current", null));
        ActorCardStepSaveReqDTO request = new ActorCardStepSaveReqDTO();
        request.setCurrentStep(2);
        request.setStyle("urban");

        service.saveStep(USER_ID, CARD_ID, request);

        ActorCard saved = persistedCard();
        assertAll(
                () -> assertEquals(2, saved.getCurrentStep()),
                () -> assertEquals("urban", saved.getStyle()));
    }

    @Test
    void legacyMainVisualWriteLocksTheOwnedCardBeforeCheckingVisualInput() {
        ActorCard card = draft();
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(cardMapper.update(isNull(), any())).thenReturn(1);
        ActorCardStepSaveReqDTO request = new ActorCardStepSaveReqDTO();
        request.setCurrentStep(1);
        request.setSourceImageUrl("https://legacy.test/person.jpg");

        service.saveStep(USER_ID, CARD_ID, request);

        var order = inOrder(cardMapper, visualInputMapper);
        order.verify(cardMapper).selectOwnedForUpdate(USER_ID, CARD_ID);
        order.verify(visualInputMapper).selectByCardId(CARD_ID);
    }

    @Test
    void missingVisualInputTableKeepsLegacyMainVisualWriteCompatible() {
        ActorCard card = draft();
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(cardMapper.update(isNull(), any())).thenReturn(1);
        when(visualInputMapper.selectByCardId(CARD_ID)).thenThrow(new BadSqlGrammarException(
                "select visual input", "missing table",
                new java.sql.SQLException(
                        "Table 'test.actor_card_visual_input' doesn't exist", "42S02", 1146)));
        ActorCardStepSaveReqDTO request = new ActorCardStepSaveReqDTO();
        request.setCurrentStep(1);
        request.setSourceImageUrl("https://legacy.test/person.jpg");

        service.saveStep(USER_ID, CARD_ID, request);

        assertEquals("https://legacy.test/person.jpg", persistedCard().getSourceImageUrl());
    }

    @Test
    void nonMissingVisualInputSqlFailureIsNotDowngradedDuringSave() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());
        when(visualInputMapper.selectByCardId(CARD_ID)).thenThrow(new BadSqlGrammarException(
                "select visual input", "broken sql",
                new java.sql.SQLException(
                        "syntax error near actor_card_visual_input", "42000", 1064)));
        ActorCardStepSaveReqDTO request = new ActorCardStepSaveReqDTO();
        request.setSourceImageUrl("https://legacy.test/person.jpg");

        assertThrows(BadSqlGrammarException.class,
                () -> service.saveStep(USER_ID, CARD_ID, request));
        verify(cardMapper, never()).updateById(any());
    }

    @Test void stepOneCanExplicitlyClearHistoricalImageMirrors() {
        ActorCard card = draft();
        card.setBackgroundImageUrl("https://legacy.test/background.jpg");
        card.setSourceImageUrl("https://legacy.test/source.jpg");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(cardMapper.update(isNull(), any())).thenReturn(1);
        ActorCardStepSaveReqDTO dto = new ActorCardStepSaveReqDTO();
        dto.setCurrentStep(1);
        dto.setBackgroundImageUrl("");
        dto.setSourceImageUrl("");

        service.saveStep(USER_ID, CARD_ID, dto);

        assertAll(
                () -> assertNull(persistedCard().getBackgroundImageUrl()),
                () -> assertNull(card.getSourceImageUrl()));
    }

    @Test void aMissingMediaBindingTableKeepsLegacyStepStatusReadable() {
        ActorCard card = draft();
        card.setSourceImageUrl("https://legacy.test/source.jpg");
        card.setPhotosJson("[\"https://legacy.test/life.jpg\"]");
        card.setVideoUrl("https://legacy.test/video.mp4");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(mediaBindingService.list(eq(USER_ID), eq(CARD_ID), any(), isNull()))
                .thenThrow(new BadSqlGrammarException("select media bindings", "missing table", new java.sql.SQLException()));

        ActorCardRespDTO dto = service.getDraft(USER_ID, CARD_ID);

        assertAll(
                () -> assertEquals("done", step(dto, 1).getStatusCode()),
                () -> assertEquals("1张", step(dto, 4).getStatusLabel()),
                () -> assertEquals("done", step(dto, 5).getStatusCode()));
    }

    @Test
    void missingVisualInputTableKeepsLegacyStepOneReadable() {
        ActorCard card = draft();
        card.setSourceImageUrl("https://legacy.test/source.jpg");
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        when(visualInputMapper.selectByCardId(CARD_ID)).thenThrow(new BadSqlGrammarException(
                "select visual input", "missing table",
                new java.sql.SQLException(
                        "Table 'test.actor_card_visual_input' doesn't exist", "42S02", 1146)));

        assertEquals("done", step(service.getDraft(USER_ID, CARD_ID), 1).getStatusCode());
    }

    @Test
    void nonMissingTableVisualInputSqlFailureIsNotDowngradedToLegacy() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());
        when(visualInputMapper.selectByCardId(CARD_ID)).thenThrow(new BadSqlGrammarException(
                "select visual input", "broken sql",
                new java.sql.SQLException(
                        "syntax error near actor_card_visual_input", "42000", 1064)));

        assertThrows(BadSqlGrammarException.class, () -> service.getDraft(USER_ID, CARD_ID));
    }

    @Test
    void draftCompatibilityFieldsProjectCurrentPersonAndFingerprintMatchedGeneratedResult() {
        ActorCard card = draftWithLegacyMainVisualMirrors();
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        ActorCardVisualInput visual = visualInput("current", "current");
        visual.setPersonSourceType("managed_url");
        visual.setPersonImageUrl("https://managed.test/person.jpg");
        visual.setGeneratedImageUrl("https://managed.test/generated.jpg");
        when(visualInputMapper.selectByCardId(CARD_ID)).thenReturn(visual);
        when(generatedImageStorage.issueReadUrl(
                eq("https://managed.test/person.jpg"), any()))
                .thenReturn("https://signed.test/person");
        when(generatedImageStorage.issueReadUrl(
                eq("https://managed.test/generated.jpg"), any()))
                .thenReturn("https://signed.test/generated");

        ActorCardRespDTO result = service.getDraft(USER_ID, CARD_ID);

        assertAll(
                () -> assertEquals("https://signed.test/person", result.getSourceImageUrl()),
                () -> assertEquals("https://signed.test/generated", result.getBackgroundImageUrl()),
                () -> assertEquals("https://signed.test/generated", result.getExpandedImageUrl()),
                () -> assertEquals("https://legacy.test/preview.jpg", result.getGeneratedPreviewUrl()),
                () -> assertEquals("https://legacy.test/source.jpg", card.getSourceImageUrl()),
                () -> assertEquals("https://legacy.test/background.jpg", card.getBackgroundImageUrl()),
                () -> assertEquals("https://legacy.test/expanded.jpg", card.getExpandedImageUrl()));
        verify(cardMapper, never()).updateById(any());
        verify(cardMapper, never()).update(isNull(), any());
    }

    @Test
    void draftCompatibilityFieldsUseStablePersonWhileGeneratedResultIsStale() {
        ActorCard card = draftWithLegacyMainVisualMirrors();
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        ActorCardVisualInput visual = visualInput("current", "stale");
        visual.setPersonSourceType("managed_url");
        visual.setPersonImageUrl("https://managed.test/person.jpg");
        visual.setGeneratedImageUrl("https://managed.test/stale-generated.jpg");
        when(visualInputMapper.selectByCardId(CARD_ID)).thenReturn(visual);
        when(generatedImageStorage.issueReadUrl(
                eq("https://managed.test/person.jpg"), any()))
                .thenReturn("https://signed.test/person");

        ActorCardRespDTO result = service.getDraft(USER_ID, CARD_ID);

        assertAll(
                () -> assertEquals("https://signed.test/person", result.getSourceImageUrl()),
                () -> assertEquals("https://signed.test/person", result.getBackgroundImageUrl()),
                () -> assertNull(result.getExpandedImageUrl()),
                () -> assertEquals("https://legacy.test/preview.jpg", result.getGeneratedPreviewUrl()));
        verify(generatedImageStorage, never()).issueReadUrl(
                eq("https://managed.test/stale-generated.jpg"), any());
    }

    @Test
    void visualAiResultDoesNotFabricateStructuredGenerationReadiness() {
        ActorCard card = draft();
        card.setGeneratedPreviewUrl(null);
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        ActorCardVisualInput visual = visualInput("current", "current");
        visual.setPersonImageUrl("https://managed.test/person.jpg");
        visual.setGeneratedImageUrl("https://managed.test/generated.jpg");
        when(visualInputMapper.selectByCardId(CARD_ID)).thenReturn(visual);
        when(generatedImageStorage.issueReadUrl(
                eq("https://managed.test/generated.jpg"), any()))
                .thenReturn("https://signed.test/generated");

        ActorCardRespDTO result = service.getDraft(USER_ID, CARD_ID);

        assertAll(
                () -> assertEquals("https://signed.test/generated", result.getExpandedImageUrl()),
                () -> assertNull(result.getGeneratedPreviewUrl()));
    }

    @Test
    void draftCompatibilityProjectionNeverUsesSelectedBackgroundAsPersonOrWritesItBack() {
        ActorCard card = draftWithLegacyMainVisualMirrors();
        when(cardMapper.selectById(CARD_ID)).thenReturn(card);
        ActorCardVisualInput visual = visualInput("current", "stale");
        visual.setBackgroundId(71L);
        visual.setBackgroundImageUrl("https://managed.test/selected-background.jpg");
        when(visualInputMapper.selectByCardId(CARD_ID)).thenReturn(visual);

        ActorCardRespDTO result = service.getDraft(USER_ID, CARD_ID);

        assertAll(
                () -> assertNull(result.getSourceImageUrl()),
                () -> assertNull(result.getBackgroundImageUrl()),
                () -> assertNull(result.getExpandedImageUrl()),
                () -> assertEquals("https://legacy.test/preview.jpg", result.getGeneratedPreviewUrl()),
                () -> assertEquals("https://legacy.test/background.jpg", card.getBackgroundImageUrl()));
        verify(generatedImageStorage, never()).issueReadUrl(
                eq("https://managed.test/selected-background.jpg"), any());
        verify(cardMapper, never()).updateById(any());
        verify(cardMapper, never()).update(isNull(), any());
    }

    // ── 归属 ──────────────────────────────────────────────────────────────────

    @Test void anotherUsersCardCannotBeSaved() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());

        assertThrows(BizException.class, () -> service.saveStep(8L, CARD_ID, withAttachment(ASSET_ID)));
        verify(cardMapper, never()).updateById(any());
    }

    @Test void replaceWorksBindsNewStillAssetIdsInTheSameOperation() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());
        when(workMapper.selectList(any())).thenReturn(List.of());
        when(workMapper.insert(any(ActorCardWork.class))).thenAnswer(invocation -> {
            ActorCardWork work = invocation.getArgument(0);
            work.setId(101L);
            return 1;
        });

        service.replaceWorks(USER_ID, CARD_ID, worksWithAssets(91L, 92L));

        ArgumentCaptor<ActorCardMediaBindingReqDTO> binding =
                ArgumentCaptor.forClass(ActorCardMediaBindingReqDTO.class);
        verify(mediaBindingService).replace(eq(USER_ID), eq(CARD_ID), binding.capture());
        assertAll(
                () -> assertEquals("work_still", binding.getValue().getUsage()),
                () -> assertEquals(101L, binding.getValue().getWorkId()),
                () -> assertEquals(List.of(91L, 92L), binding.getValue().getItems().stream()
                        .map(item -> item.getAssetId()).toList()),
                () -> assertEquals(List.of(0, 1), binding.getValue().getItems().stream()
                        .map(item -> item.getSortOrder()).toList()));
    }

    @Test void replaceWorksKeepsLegacyStillUrlsWithoutCreatingNewBindings() {
        when(cardMapper.selectById(CARD_ID)).thenReturn(draft());
        when(workMapper.selectList(any())).thenReturn(List.of());

        service.replaceWorks(USER_ID, CARD_ID, worksWithLegacyUrl("https://example.test/still.jpg"));

        ArgumentCaptor<ActorCardWork> inserted = ArgumentCaptor.forClass(ActorCardWork.class);
        verify(workMapper).insert(inserted.capture());
        assertEquals("[\"https://example.test/still.jpg\"]", inserted.getValue().getStillsJson());
        verify(mediaBindingService, never()).replace(any(), any(), any());
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

    private ActorCard draftWithLegacyMainVisualMirrors() {
        ActorCard card = draft();
        card.setSourceImageUrl("https://legacy.test/source.jpg");
        card.setBackgroundImageUrl("https://legacy.test/background.jpg");
        card.setExpandedImageUrl("https://legacy.test/expanded.jpg");
        card.setGeneratedPreviewUrl("https://legacy.test/preview.jpg");
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

    private ActorCardWorksReplaceReqDTO worksWithAssets(Long... assetIds) {
        ActorCardWorksReplaceReqDTO.WorkItem work = baseWork();
        work.setStillAssetIds(List.of(assetIds));
        ActorCardWorksReplaceReqDTO request = new ActorCardWorksReplaceReqDTO();
        request.setWorks(List.of(work));
        return request;
    }

    private ActorCardWorksReplaceReqDTO worksWithLegacyUrl(String url) {
        ActorCardWorksReplaceReqDTO.WorkItem work = baseWork();
        work.setStills(List.of(url));
        ActorCardWorksReplaceReqDTO request = new ActorCardWorksReplaceReqDTO();
        request.setWorks(List.of(work));
        return request;
    }

    private ActorCardWorksReplaceReqDTO.WorkItem baseWork() {
        ActorCardWorksReplaceReqDTO.WorkItem work = new ActorCardWorksReplaceReqDTO.WorkItem();
        work.setWorkTitle("测试作品");
        work.setWorkType("movie");
        work.setRoleName("角色");
        return work;
    }

    private ActorCard persistedCard() {
        ArgumentCaptor<ActorCard> saved = ArgumentCaptor.forClass(ActorCard.class);
        verify(cardMapper).updateById(saved.capture());
        return saved.getValue();
    }

    private ActorCardRespDTO.StepStatus stepSix(ActorCardRespDTO dto) {
        return step(dto, 6);
    }

    private ActorCardRespDTO.StepStatus step(ActorCardRespDTO dto, int step) {
        return dto.getStepStatuses().stream()
                .filter(s -> s.getStep() == step).findFirst().orElseThrow();
    }

    private ActorCardMediaBindingRespDTO binding(String usage, int count) {
        ActorCardMediaBindingRespDTO response = new ActorCardMediaBindingRespDTO();
        response.setSetId(91L);
        response.setUsage(usage);
        response.setItems(java.util.stream.IntStream.range(0, count).mapToObj(index -> {
            ActorCardMediaBindingItemRespDTO item = new ActorCardMediaBindingItemRespDTO();
            item.setAssetId(100L + index);
            item.setSortOrder(index);
            return item;
        }).toList());
        return response;
    }

    private ActorCardVisualInput visualInput(String inputFingerprint, String generatedFingerprint) {
        ActorCardVisualInput visual = new ActorCardVisualInput();
        visual.setCardId(CARD_ID);
        visual.setUserId(USER_ID);
        visual.setGenerationMode("person_background_ai");
        visual.setInputFingerprint(inputFingerprint);
        visual.setGeneratedFingerprint(generatedFingerprint);
        return visual;
    }
}
