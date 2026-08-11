package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.*;
import com.kaipai.mapper.card.ShareCardAssetMapper;
import com.kaipai.model.actor.dto.ActorAssetQueryDTO;
import com.kaipai.model.actor.dto.ActorAssetBindingDTO;
import com.kaipai.model.actor.dto.ActorAssetUpdateDTO;
import com.kaipai.model.actor.dto.ActorCurrentResumeUpdateDTO;
import com.kaipai.model.actor.dto.ActorWorkAssetRespDTO;
import com.kaipai.model.actor.dto.ActorWorkAssetsReplaceDTO;
import com.kaipai.common.result.ResultCode;
import com.kaipai.model.actor.entity.*;
import com.kaipai.service.actor.PrivateActorMediaStorage;
import com.kaipai.service.actor.ActorPrivatePdfProcessor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ActorMediaAssetServiceImplTest {
    private ActorMediaAssetMapper assetMapper;
    private ActorProfileMapper profileMapper;
    private ActorProfileAssetMapper profileAssetMapper;
    private ActorWorkAssetMapper workAssetMapper;
    private ActorExperienceMapper experienceMapper;
    private ShareCardAssetMapper shareAssetMapper;
    private ActorMediaAssetPageMapper pageMapper;
    private PrivateActorMediaStorage storage;
    private ActorPrivatePdfProcessor pdfProcessor;
    private ActorPdfAssetLifecycleService pdfLifecycle;
    private ActorMediaAssetServiceImpl service;

    @BeforeEach void setUp() {
        assetMapper = mock(ActorMediaAssetMapper.class); profileMapper = mock(ActorProfileMapper.class);
        profileAssetMapper = mock(ActorProfileAssetMapper.class); workAssetMapper = mock(ActorWorkAssetMapper.class); experienceMapper = mock(ActorExperienceMapper.class);
        shareAssetMapper = mock(ShareCardAssetMapper.class); storage = mock(PrivateActorMediaStorage.class);
        pageMapper = mock(ActorMediaAssetPageMapper.class); pdfProcessor = mock(ActorPrivatePdfProcessor.class);
        pdfLifecycle = mock(ActorPdfAssetLifecycleService.class);
        service = new ActorMediaAssetServiceImpl(assetMapper, profileMapper, profileAssetMapper, workAssetMapper,
                experienceMapper, shareAssetMapper, pageMapper, storage, pdfProcessor, pdfLifecycle);
    }

    @Test void createdAssetPersistsObjectIdentityWithoutAccessUrl() {
        when(assetMapper.insert(any())).thenAnswer(call -> { ((ActorMediaAsset) call.getArgument(0)).setAssetId(81L); return 1; });
        var result = service.createReadyAsset(7L, "photo", "portrait_candidate",
                new PrivateActorMediaStorage.StoredObjectRef("cos", "private", "actor/7/a.jpg", null), "a.jpg", "image/jpeg", 100L);
        assertEquals(81L, result.getAssetId()); assertNull(result.getAccessUrl());
        var persisted = org.mockito.ArgumentCaptor.forClass(ActorMediaAsset.class); verify(assetMapper).insert(persisted.capture()); assertEquals("actor/7/a.jpg", persisted.getValue().getObjectKey());
    }

    @Test void uploadGeneratesPrivateObjectIdentityOnServerAndReturnsMetadataOnly() {
        var file = new MockMultipartFile("file", "模卡.jpg", "image/jpeg", new byte[] {1, 2, 3});
        when(storage.store(7L, "photo", file)).thenReturn(
                new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", "actor/7/photo/a.jpg", null));
        when(assetMapper.insert(any())).thenAnswer(call -> { ((ActorMediaAsset) call.getArgument(0)).setAssetId(82L); return 1; });

        var result = service.upload(7L, "photo", "model_card", file);

        assertEquals(82L, result.getAssetId());
        assertNull(result.getAccessUrl());
        verify(storage).store(7L, "photo", file);
    }

    @Test void uploadRejectsLegacyOrInvalidCategoryBeforeStorage() {
        var file = new MockMultipartFile("file", "旧头像.jpg", "image/jpeg", new byte[] {1, 2, 3});

        assertAll(
                () -> assertEquals(ResultCode.PARAM_ERROR.getCode(),
                        assertThrows(BizException.class,
                                () -> service.upload(7L, "photo", null, file)).getCode()),
                () -> assertEquals(ResultCode.PARAM_ERROR.getCode(),
                        assertThrows(BizException.class,
                                () -> service.upload(7L, "photo", "   ", file)).getCode()),
                () -> assertEquals(ResultCode.PARAM_ERROR.getCode(),
                        assertThrows(BizException.class,
                                () -> service.upload(7L, "   ", "other", file)).getCode()),
                () -> assertEquals(ResultCode.PARAM_ERROR.getCode(),
                        assertThrows(BizException.class,
                                () -> service.upload(7L, "photo", "avatar", file)).getCode()),
                () -> assertEquals(ResultCode.PARAM_ERROR.getCode(),
                        assertThrows(BizException.class,
                                () -> service.upload(7L, "photo", "work_still", file)).getCode()),
                () -> assertEquals(ResultCode.PARAM_ERROR.getCode(),
                        assertThrows(BizException.class,
                                () -> service.upload(7L, "video", "model_card", file)).getCode()));
        verifyNoInteractions(storage);
        verify(assetMapper, never()).insert(any());
    }

    @Test void ownerAccessRequiresOwnerAndReadyAsset() {
        ActorMediaAsset asset = readyPhoto(7L); when(assetMapper.selectOne(any())).thenReturn(asset, null);
        when(storage.issueAccessUrl(any(), any(), any())).thenReturn(new PrivateActorMediaStorage.SignedAccess("https://signed", Instant.now().plusSeconds(600)));
        assertEquals("https://signed", service.issueOwnerAccessUrl(7L, 81L).getAccessUrl());
        assertEquals(46012, assertThrows(BizException.class, () -> service.issueOwnerAccessUrl(8L, 81L)).getCode());
    }

    @Test void referencedAssetCannotBeDeleted() {
        when(assetMapper.selectOwnedActiveByIdsForUpdate(any(), any())).thenReturn(List.of(readyPhoto(7L)));
        when(profileMapper.selectCount(any())).thenReturn(1L);
        assertEquals(46014, assertThrows(BizException.class, () -> service.delete(7L, 81L)).getCode());
        verify(assetMapper, never()).deleteById(any(Long.class));
    }

    @Test void deletingPdfAlsoDeletesPersistedPageObjects() {
        ActorMediaAsset pdf = readyPhoto(7L); pdf.setMediaType("pdf"); pdf.setObjectKey("resume.pdf");
        ActorMediaAssetPage page = new ActorMediaAssetPage(); page.setPageId(3L); page.setAssetId(81L); page.setImageObjectKey("page-1.jpg");
        when(assetMapper.selectOwnedActiveByIdsForUpdate(any(), any())).thenReturn(List.of(pdf));
        when(profileMapper.selectCount(any())).thenReturn(0L);
        when(profileAssetMapper.selectCount(any())).thenReturn(0L);
        when(workAssetMapper.selectCount(any())).thenReturn(0L);
        when(shareAssetMapper.selectCount(any())).thenReturn(0L);
        when(pageMapper.selectList(any())).thenReturn(java.util.List.of(page));

        service.delete(7L, 81L);

        verify(storage).delete("private", "page-1.jpg");
        verify(pageMapper).delete(any());
        verify(storage).delete("private", "resume.pdf");
    }

    @Test void listAssetsPaginatesAndFiltersWithoutExposingObjectIdentity() {
        ActorMediaAsset photo = readyPhoto(7L);
        photo.setOriginalName("王火火模卡.jpg");
        when(assetMapper.selectPage(any(Page.class), any())).thenAnswer(call -> {
            Page<ActorMediaAsset> page = call.getArgument(0);
            page.setTotal(1);
            page.setRecords(java.util.List.of(photo));
            return page;
        });
        ActorAssetQueryDTO query = new ActorAssetQueryDTO();
        query.setPage(1); query.setSize(10); query.setMediaType("photo"); query.setCategoryCode("portrait_candidate");

        var result = service.list(7L, query);

        assertEquals(1L, result.getTotal());
        assertEquals(81L, result.getList().get(0).getAssetId());
        assertNull(result.getList().get(0).getAccessUrl());
    }

    @Test void updateMetadataOnlyChangesOwnedAssetNameAndCategory() {
        ActorMediaAsset asset = readyPhoto(7L);
        when(assetMapper.selectOne(any())).thenReturn(asset);
        ActorAssetUpdateDTO request = new ActorAssetUpdateDTO();
        request.setOriginalName("  新模卡.jpg  "); request.setCategoryCode("model_card");

        var result = service.update(7L, 81L, request);

        assertEquals("新模卡.jpg", result.getOriginalName());
        assertEquals("model_card", result.getCategoryCode());
        verify(assetMapper).updateById(asset);
    }

    @Test void updatingOnlyNamePreservesExistingCategory() {
        ActorMediaAsset asset = readyPhoto(7L);
        asset.setOriginalName("old.jpg");
        asset.setCategoryCode("portrait");
        when(assetMapper.selectOne(any())).thenReturn(asset);
        ActorAssetUpdateDTO request = new ActorAssetUpdateDTO();
        request.setOriginalName("  new.jpg  ");

        var result = service.update(7L, 81L, request);

        assertEquals("new.jpg", result.getOriginalName());
        assertEquals("portrait", result.getCategoryCode());
    }

    @Test void updatingOnlyCategoryPreservesExistingName() {
        ActorMediaAsset asset = readyPhoto(7L);
        asset.setOriginalName("portrait.jpg");
        asset.setCategoryCode("other");
        when(assetMapper.selectOne(any())).thenReturn(asset);
        ActorAssetUpdateDTO request = new ActorAssetUpdateDTO();
        request.setCategoryCode("portrait_candidate");

        var result = service.update(7L, 81L, request);

        assertEquals("portrait.jpg", result.getOriginalName());
        assertEquals("portrait_candidate", result.getCategoryCode());
    }

    @Test void updateRejectsLegacyOrCrossMediaCategoryWithoutPersisting() {
        ActorMediaAsset asset = readyPhoto(7L);
        when(assetMapper.selectOne(any())).thenReturn(asset);
        ActorAssetUpdateDTO legacy = new ActorAssetUpdateDTO();
        legacy.setCategoryCode("work_still");
        ActorAssetUpdateDTO crossMedia = new ActorAssetUpdateDTO();
        crossMedia.setCategoryCode("self_intro");
        ActorAssetUpdateDTO blank = new ActorAssetUpdateDTO();
        blank.setCategoryCode("   ");

        assertAll(
                () -> assertEquals(ResultCode.PARAM_ERROR.getCode(),
                        assertThrows(BizException.class,
                                () -> service.update(7L, 81L, legacy)).getCode()),
                () -> assertEquals(ResultCode.PARAM_ERROR.getCode(),
                        assertThrows(BizException.class,
                                () -> service.update(7L, 81L, crossMedia)).getCode()),
                () -> assertEquals(ResultCode.PARAM_ERROR.getCode(),
                        assertThrows(BizException.class,
                                () -> service.update(7L, 81L, blank)).getCode()));
        verify(assetMapper, never()).updateById(any());
    }

    @Test void onlyOwnedReadyPdfCanBecomeCurrentResume() {
        ActorMediaAsset pdf = readyPhoto(7L);
        pdf.setMediaType("pdf");
        ActorProfile profile = new ActorProfile(); profile.setActorProfileId(9L); profile.setUserId(7L);
        when(assetMapper.selectOwnedActiveByIdsForUpdate(any(), any())).thenReturn(List.of(pdf));
        when(profileMapper.selectOne(any())).thenReturn(profile);
        ActorCurrentResumeUpdateDTO request = new ActorCurrentResumeUpdateDTO(); request.setAssetId(81L);

        service.setCurrentResume(7L, request);

        assertEquals(81L, profile.getCurrentResumeAssetId());
        verify(profileMapper).updateById(profile);
    }

    @Test void failedPdfCannotBecomeCurrentResume() {
        ActorMediaAsset pdf = readyPhoto(7L); pdf.setMediaType("pdf"); pdf.setProcessStatus("failed");
        when(assetMapper.selectOwnedActiveByIdsForUpdate(any(), any())).thenReturn(List.of(pdf));
        ActorCurrentResumeUpdateDTO request = new ActorCurrentResumeUpdateDTO(); request.setAssetId(81L);

        assertEquals(46013, assertThrows(BizException.class, () -> service.setCurrentResume(7L, request)).getCode());
        verify(profileMapper, never()).updateById(any());
    }

    @Test void pdfUploadPersistsOrderedPagesBeforeBecomingReady() {
        var file = new MockMultipartFile("file", "简历.pdf", "application/pdf", "%PDF-test".getBytes());
        when(assetMapper.insert(any())).thenAnswer(call -> { ((ActorMediaAsset) call.getArgument(0)).setAssetId(90L); return 1; });
        var original = new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", "actor-private/7/pdf/a.pdf", null);
        var page1 = new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", "actor-private/7/pdf-page/1.jpg", null);
        var page2 = new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", "actor-private/7/pdf-page/2.jpg", null);
        when(storage.store(7L, "pdf", file)).thenReturn(original);
        when(pdfProcessor.process(7L, file)).thenReturn(java.util.List.of(page1, page2));

        var result = service.upload(7L, "pdf", "resume", file);

        assertEquals("ready", result.getProcessStatus());
        verify(pdfLifecycle).finalizeReady(7L, 90L, List.of(page1, page2));
        verify(pdfLifecycle, never()).markFailed(any(), any(), any(), any());
        assertEquals(2, result.getPageCount());
    }

    @Test void pdfConversionFailureIsPersistedAndNotReportedReady() {
        var file = new MockMultipartFile("file", "坏简历.pdf", "application/pdf", "%PDF-bad".getBytes());
        when(assetMapper.insert(any())).thenAnswer(call -> { ((ActorMediaAsset) call.getArgument(0)).setAssetId(91L); return 1; });
        when(storage.store(7L, "pdf", file)).thenReturn(new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", "actor-private/7/pdf/bad.pdf", null));
        when(pdfProcessor.process(7L, file)).thenThrow(new ActorPrivatePdfProcessor.PdfProcessingException("PDF_RENDER_FAILED", "PDF 页转换失败"));

        var result = service.upload(7L, "pdf", "resume", file);

        assertEquals("failed", result.getProcessStatus());
        assertEquals("PDF 页转换失败", result.getFailureMessage());
        verify(pdfLifecycle).markFailed(7L, 91L, "PDF_RENDER_FAILED", "PDF 页转换失败");
        verify(pdfLifecycle, never()).finalizeReady(any(), any(), any());
    }

    @Test void uncheckedProcessorFailureTransitionsTheNewAssetToFailed() {
        var file = new MockMultipartFile("file", "坏简历.pdf", "application/pdf", "%PDF-bad".getBytes());
        stubStoredPdf(file, 92L, "actor-private/7/pdf/unchecked.pdf");
        when(pdfProcessor.process(7L, file)).thenThrow(new IllegalStateException("renderer crashed"));

        var result = service.upload(7L, "pdf", "resume", file);

        assertEquals(92L, result.getAssetId());
        assertEquals("failed", result.getProcessStatus());
        verify(pdfLifecycle).markFailed(7L, 92L, "PDF_RENDER_FAILED", "PDF 页转换失败");
    }

    @Test void emptyProcessorOutputIsFailedInsteadOfReady() {
        var file = new MockMultipartFile("file", "空简历.pdf", "application/pdf", "%PDF-empty".getBytes());
        stubStoredPdf(file, 93L, "actor-private/7/pdf/empty.pdf");
        when(pdfProcessor.process(7L, file)).thenReturn(List.of());
        doThrow(new IllegalStateException("PDF processor returned no pages"))
                .when(pdfLifecycle).finalizeReady(7L, 93L, List.of());

        var result = service.upload(7L, "pdf", "resume", file);

        assertEquals("failed", result.getProcessStatus());
        verify(pdfLifecycle).markFailed(7L, 93L, "PDF_FINALIZE_FAILED", "PDF 处理结果保存失败");
    }

    @Test void nullProcessorOutputStillEntersFailedCompensation() {
        var file = new MockMultipartFile("file", "空简历.pdf", "application/pdf", "%PDF-null".getBytes());
        stubStoredPdf(file, 96L, "actor-private/7/pdf/null.pdf");
        when(pdfProcessor.process(7L, file)).thenReturn(null);
        doThrow(new IllegalStateException("PDF processor returned no pages"))
                .when(pdfLifecycle).finalizeReady(7L, 96L, null);

        var result = service.upload(7L, "pdf", "resume", file);

        assertEquals("failed", result.getProcessStatus());
        verify(pdfLifecycle).markFailed(7L, 96L, "PDF_FINALIZE_FAILED", "PDF 处理结果保存失败");
    }

    @Test void finalizeFailureDeletesGeneratedObjectsBestEffortAndMarksAssetFailed() {
        var file = new MockMultipartFile("file", "简历.pdf", "application/pdf", "%PDF-test".getBytes());
        var page1 = new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", "page-1.jpg", null);
        var page2 = new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", "page-2.jpg", null);
        stubStoredPdf(file, 94L, "actor-private/7/pdf/finalize.pdf");
        when(pdfProcessor.process(7L, file)).thenReturn(List.of(page1, page2));
        doThrow(new IllegalStateException("commit failed"))
                .when(pdfLifecycle).finalizeReady(7L, 94L, List.of(page1, page2));
        doThrow(new IllegalStateException("cleanup failed"))
                .when(storage).delete("private-assets", "page-1.jpg");

        var result = service.upload(7L, "pdf", "resume", file);

        assertEquals("failed", result.getProcessStatus());
        verify(storage).delete("private-assets", "page-1.jpg");
        verify(storage).delete("private-assets", "page-2.jpg");
        verify(pdfLifecycle).markFailed(7L, 94L, "PDF_FINALIZE_FAILED", "PDF 处理结果保存失败");
    }

    @Test void ambiguousFinalizeOutcomeDoesNotDeletePagesWhenFailedTransitionIsRejected() {
        var file = new MockMultipartFile("file", "简历.pdf", "application/pdf", "%PDF-test".getBytes());
        var page = new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", "page-ready.jpg", null);
        stubStoredPdf(file, 97L, "actor-private/7/pdf/ambiguous.pdf");
        when(pdfProcessor.process(7L, file)).thenReturn(List.of(page));
        doThrow(new IllegalStateException("commit outcome unknown"))
                .when(pdfLifecycle).finalizeReady(7L, 97L, List.of(page));
        IllegalStateException staleTransition = new IllegalStateException("asset is no longer processing");
        doThrow(staleTransition).when(pdfLifecycle)
                .markFailed(7L, 97L, "PDF_FINALIZE_FAILED", "PDF 处理结果保存失败");

        var thrown = assertThrows(IllegalStateException.class,
                () -> service.upload(7L, "pdf", "resume", file));

        assertSame(staleTransition, thrown);
        verify(storage, never()).delete("private-assets", "page-ready.jpg");
    }

    @Test void failedTransitionFailurePropagatesWithoutReturningAnInMemoryFailedAsset() {
        var file = new MockMultipartFile("file", "坏简历.pdf", "application/pdf", "%PDF-bad".getBytes());
        stubStoredPdf(file, 95L, "actor-private/7/pdf/failed-transition.pdf");
        when(pdfProcessor.process(7L, file)).thenThrow(
                new ActorPrivatePdfProcessor.PdfProcessingException("PDF_RENDER_FAILED", "PDF 页转换失败"));
        IllegalStateException transitionFailure = new IllegalStateException("failed transition unavailable");
        doThrow(transitionFailure).when(pdfLifecycle)
                .markFailed(7L, 95L, "PDF_RENDER_FAILED", "PDF 页转换失败");

        var thrown = assertThrows(IllegalStateException.class,
                () -> service.upload(7L, "pdf", "resume", file));

        assertSame(transitionFailure, thrown);
    }

    @Test void zeroRowProcessingInsertDeletesTheStoredOriginalAndStopsProcessing() {
        var file = new MockMultipartFile("file", "简历.pdf", "application/pdf", "%PDF-test".getBytes());
        when(storage.store(7L, "pdf", file)).thenReturn(
                new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", "original.pdf", null));
        when(assetMapper.insert(any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.upload(7L, "pdf", "resume", file));

        verify(storage).delete("private-assets", "original.pdf");
        verifyNoInteractions(pdfProcessor, pdfLifecycle);
    }

    @Test void thrownProcessingInsertDeletesTheStoredOriginalAndPreservesTheFailure() {
        var file = new MockMultipartFile("file", "简历.pdf", "application/pdf", "%PDF-test".getBytes());
        when(storage.store(7L, "pdf", file)).thenReturn(
                new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", "original.pdf", null));
        IllegalStateException insertFailure = new IllegalStateException("insert failed");
        when(assetMapper.insert(any())).thenThrow(insertFailure);

        var thrown = assertThrows(IllegalStateException.class,
                () -> service.upload(7L, "pdf", "resume", file));

        assertSame(insertFailure, thrown);
        verify(storage).delete("private-assets", "original.pdf");
        verifyNoInteractions(pdfProcessor, pdfLifecycle);
    }

    @Test void readyPhotoCanBeBoundToProfileAndRejectsNonPhoto() {
        ActorMediaAsset photo = readyPhoto(7L);
        ActorProfile profile = new ActorProfile(); profile.setActorProfileId(9L); profile.setUserId(7L);
        when(assetMapper.selectOwnedActiveByIdsForUpdate(any(), any())).thenReturn(List.of(photo)); when(profileMapper.selectOne(any())).thenReturn(profile);

        service.bindProfileAsset(7L, 81L, "portrait", 1);

        var relation = org.mockito.ArgumentCaptor.forClass(ActorProfileAsset.class);
        verify(profileAssetMapper).insert(relation.capture());
        assertEquals(9L, relation.getValue().getActorProfileId());
        assertEquals("portrait", relation.getValue().getUsageCode());
    }

    @Test void readyPhotoOwnershipValidationUsesTheDeletionLockProtocol() {
        ActorMediaAsset photo = readyPhoto(7L);
        when(assetMapper.selectOwnedActiveByIdsForUpdate(7L, List.of(81L))).thenReturn(List.of(photo));

        service.requireOwnedReadyPhoto(7L, 81L);

        verify(assetMapper).selectOwnedActiveByIdsForUpdate(7L, List.of(81L));
    }

    @Test void readyPdfOwnershipValidationUsesTheDeletionLockProtocol() {
        when(assetMapper.selectOwnedActiveByIdsForUpdate(7L, List.of(81L))).thenReturn(List.of(readyPdf(7L)));

        service.requireOwnedReadyPdf(7L, 81L);

        verify(assetMapper).selectOwnedActiveByIdsForUpdate(7L, List.of(81L));
    }

    /** 越权：归属查询按 userId 收口，他人素材查不出来，回 46012 而不是暴露「存在但不属于你」。 */
    @Test void readyPdfValidationRejectsAnAssetOwnedBySomeoneElse() {
        when(assetMapper.selectOwnedActiveByIdsForUpdate(8L, List.of(81L))).thenReturn(List.of());

        assertEquals(46012, assertThrows(BizException.class, () -> service.requireOwnedReadyPdf(8L, 81L)).getCode());
    }

    @Test void readyPdfValidationRejectsANonPdfAsset() {
        when(assetMapper.selectOwnedActiveByIdsForUpdate(7L, List.of(81L))).thenReturn(List.of(readyPhoto(7L)));

        assertEquals(46013, assertThrows(BizException.class, () -> service.requireOwnedReadyPdf(7L, 81L)).getCode());
    }

    @Test void readyPdfValidationRejectsAPdfStillBeingProcessedOrFailed() {
        ActorMediaAsset processing = readyPdf(7L); processing.setProcessStatus("processing");
        ActorMediaAsset failed = readyPdf(7L); failed.setProcessStatus("failed");
        when(assetMapper.selectOwnedActiveByIdsForUpdate(7L, List.of(81L))).thenReturn(List.of(processing), List.of(failed));

        assertAll(
                () -> assertEquals(46013, assertThrows(BizException.class, () -> service.requireOwnedReadyPdf(7L, 81L)).getCode()),
                () -> assertEquals(46013, assertThrows(BizException.class, () -> service.requireOwnedReadyPdf(7L, 81L)).getCode()));
    }

    @Test void listPagesVerifiesOwnershipAndRejectsNonPdfOrNotReady() {
        ActorMediaAsset pdf = readyPdf(7L);
        ActorMediaAssetPage page1 = new ActorMediaAssetPage(); page1.setPageNo(1); page1.setImageObjectKey("page-1.jpg");
        ActorMediaAssetPage page2 = new ActorMediaAssetPage(); page2.setPageNo(2); page2.setImageObjectKey("page-2.jpg");
        when(assetMapper.selectOne(any())).thenReturn(pdf, null, readyPhoto(7L));
        when(pageMapper.selectList(any())).thenReturn(List.of(page1, page2));
        when(storage.issueAccessUrl(any(), any(), any())).thenAnswer(call ->
            new PrivateActorMediaStorage.SignedAccess("https://signed/" + call.getArgument(1),
                java.time.Instant.now().plusSeconds(600)));

        var pages = service.listPages(7L, 81L);

        assertEquals(2, pages.size());
        assertEquals(1, pages.get(0).getPageNo());
        assertEquals("https://signed/page-1.jpg", pages.get(0).getAccessUrl());
        assertEquals(2, pages.get(1).getPageNo());
        assertEquals(46012, assertThrows(BizException.class, () -> service.listPages(8L, 81L)).getCode());
        assertEquals(46013, assertThrows(BizException.class, () -> service.listPages(7L, 81L)).getCode());
    }

    @Test void listPagesReturnsEmptyForPdfWithNoPages() {
        when(assetMapper.selectOne(any())).thenReturn(readyPdf(7L));
        when(pageMapper.selectList(any())).thenReturn(List.of());

        assertEquals(List.of(), service.listPages(7L, 81L));
    }

    @Test
    void workAssetsReturnsTheMapperSnapshotForAnOwnedActiveWorkWithoutLocking() {
        ActorWorkAssetRespDTO still = workAssetSnapshot(81L, "still", 1);
        ActorWorkAssetRespDTO clip = workAssetSnapshot(82L, "clip", 1);
        when(experienceMapper.selectOwnedActiveById(7L, 12L)).thenReturn(ownedWork());
        when(workAssetMapper.selectOwnedActiveAssets(7L, 12L)).thenReturn(List.of(still, clip));

        List<ActorWorkAssetRespDTO> result = service.workAssets(7L, 12L);

        assertEquals(List.of(still, clip), result);
        verify(experienceMapper).selectOwnedActiveById(7L, 12L);
        verify(workAssetMapper).selectOwnedActiveAssets(7L, 12L);
        verify(experienceMapper, never()).selectOwnedActiveByIdForUpdate(any(), any());
    }

    @Test
    void workAssetsReturnsAnEmptySnapshotWhenTheOwnedWorkHasNoRelations() {
        when(experienceMapper.selectOwnedActiveById(7L, 12L)).thenReturn(ownedWork());
        when(workAssetMapper.selectOwnedActiveAssets(7L, 12L)).thenReturn(List.of());

        assertEquals(List.of(), service.workAssets(7L, 12L));

        verify(workAssetMapper).selectOwnedActiveAssets(7L, 12L);
    }

    @Test
    void missingAndForeignWorksShareTheNonLeakingNotFoundFailure() {
        ActorExperience foreignWork = ownedWork();
        foreignWork.setUserId(8L);
        when(experienceMapper.selectOwnedActiveById(7L, 12L)).thenReturn(null);
        when(experienceMapper.selectOwnedActiveById(7L, 13L)).thenReturn(foreignWork);

        BizException missing = assertThrows(BizException.class, () -> service.workAssets(7L, 12L));
        BizException foreign = assertThrows(BizException.class, () -> service.workAssets(7L, 13L));

        assertAll(
                () -> assertEquals("作品不存在", missing.getMessage()),
                () -> assertEquals("作品不存在", foreign.getMessage()));
        verify(workAssetMapper, never()).selectOwnedActiveAssets(any(), any());
        verify(experienceMapper, never()).selectOwnedActiveByIdForUpdate(any(), any());
    }

    @Test
    void changedWorkAssetSetReplacesAllRelationsInStableOrderAndIncrementsVersionOnce() {
        stubOwnedWorkAndProfile();
        ActorMediaAsset photo = readyAsset(81L, 7L, "photo");
        ActorMediaAsset video = readyAsset(82L, 7L, "video");
        when(assetMapper.selectOwnedActiveByIdsForUpdate(any(), any())).thenReturn(List.of(video, photo));
        when(workAssetMapper.selectList(any())).thenReturn(List.of(relation(80L, "still", 1)));
        when(workAssetMapper.deleteActiveByExperienceId(12L)).thenReturn(1);
        when(workAssetMapper.insert(any())).thenReturn(1);
        when(profileMapper.incrementWorkLibraryVersion(9L)).thenReturn(1);

        service.replaceWorkAssets(7L, 12L,
                bindings(binding(82L, "clip", 1), binding(81L, "still", 1)));

        var lockOrder = inOrder(experienceMapper, assetMapper);
        lockOrder.verify(experienceMapper).selectOwnedActiveByIdForUpdate(7L, 12L);
        lockOrder.verify(assetMapper).selectOwnedActiveByIdsForUpdate(7L, List.of(81L, 82L));
        verify(workAssetMapper).deleteActiveByExperienceId(12L);
        var inserted = org.mockito.ArgumentCaptor.forClass(ActorWorkAsset.class);
        verify(workAssetMapper, times(2)).insert(inserted.capture());
        assertEquals(List.of("still", "clip"),
                inserted.getAllValues().stream().map(ActorWorkAsset::getUsageCode).toList());
        assertEquals(List.of(81L, 82L),
                inserted.getAllValues().stream().map(ActorWorkAsset::getAssetId).toList());
        verify(profileMapper, times(1)).incrementWorkLibraryVersion(9L);
    }

    @Test
    void invalidAssetInDesiredSetLeavesRelationsAndVersionUntouched() {
        stubOwnedWorkAndProfile();
        ActorMediaAsset photo = readyAsset(81L, 7L, "photo");
        ActorMediaAsset foreignVideo = readyAsset(82L, 8L, "video");
        when(assetMapper.selectOwnedActiveByIdsForUpdate(any(), any())).thenReturn(List.of(photo, foreignVideo));

        BizException error = assertThrows(BizException.class, () -> service.replaceWorkAssets(
                7L, 12L, bindings(binding(81L, "still", 1), binding(82L, "clip", 1))));

        assertEquals(46012, error.getCode());
        verify(workAssetMapper, never()).deleteActiveByExperienceId(any());
        verify(workAssetMapper, never()).insert(any());
        verify(profileMapper, never()).incrementWorkLibraryVersion(any());
    }

    @Test
    void workMustBelongToTheUsersProfileBeforeAnyRelationIsReadOrWritten() {
        ActorExperience work = ownedWork();
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 12L)).thenReturn(work);
        ActorProfile anotherProfile = profile();
        anotherProfile.setUserId(8L);
        when(profileMapper.selectOne(any())).thenReturn(anotherProfile);

        assertThrows(BizException.class, () -> service.replaceWorkAssets(
                7L, 12L, bindings(binding(81L, "still", 1))));

        verify(assetMapper, never()).selectOwnedActiveByIdsForUpdate(any(), any());
        verify(workAssetMapper, never()).selectList(any());
        verify(workAssetMapper, never()).deleteActiveByExperienceId(any());
        verify(profileMapper, never()).incrementWorkLibraryVersion(any());
    }

    @Test
    void identicalNormalizedSetIsNoOpWhileEmptySetClearsAllBindings() {
        stubOwnedWorkAndProfile();
        ActorMediaAsset photo = readyAsset(81L, 7L, "photo");
        ActorMediaAsset video = readyAsset(82L, 7L, "video");
        when(assetMapper.selectOwnedActiveByIdsForUpdate(any(), any())).thenReturn(List.of(photo, video));
        when(workAssetMapper.selectList(any())).thenReturn(List.of(
                relation(82L, "clip", 1), relation(81L, "still", 1)));
        when(workAssetMapper.deleteActiveByExperienceId(12L)).thenReturn(2);
        when(profileMapper.incrementWorkLibraryVersion(9L)).thenReturn(1);

        service.replaceWorkAssets(7L, 12L,
                bindings(binding(82L, "clip", 1), binding(81L, "still", 1)));

        verify(workAssetMapper, never()).deleteActiveByExperienceId(any());
        verify(profileMapper, never()).incrementWorkLibraryVersion(any());

        service.replaceWorkAssets(7L, 12L, bindings());

        verify(workAssetMapper).deleteActiveByExperienceId(12L);
        verify(workAssetMapper, never()).insert(any());
        verify(profileMapper, times(1)).incrementWorkLibraryVersion(9L);
    }

    @Test
    void usageMustBeStillOrClip() {
        stubOwnedWorkAndProfile();

        BizException error = assertThrows(BizException.class, () -> service.replaceWorkAssets(
                7L, 12L, bindings(binding(81L, "cover", 1))));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), error.getCode());
        verify(assetMapper, never()).selectOwnedActiveByIdsForUpdate(any(), any());
        verify(workAssetMapper, never()).deleteActiveByExperienceId(any());
    }

    @Test
    void stillRequiresPhotoAndClipRequiresVideo() {
        stubOwnedWorkAndProfile();
        when(assetMapper.selectOwnedActiveByIdsForUpdate(any(), any())).thenReturn(List.of(readyAsset(81L, 7L, "video")));

        BizException stillError = assertThrows(BizException.class, () -> service.replaceWorkAssets(
                7L, 12L, bindings(binding(81L, "still", 1))));

        assertEquals(46013, stillError.getCode());
        verify(workAssetMapper, never()).deleteActiveByExperienceId(any());

        reset(assetMapper);
        when(assetMapper.selectOwnedActiveByIdsForUpdate(any(), any())).thenReturn(List.of(readyAsset(82L, 7L, "photo")));
        BizException clipError = assertThrows(BizException.class, () -> service.replaceWorkAssets(
                7L, 12L, bindings(binding(82L, "clip", 1))));

        assertEquals(46013, clipError.getCode());
        verify(workAssetMapper, never()).deleteActiveByExperienceId(any());
        verify(profileMapper, never()).incrementWorkLibraryVersion(any());
    }

    @Test
    void workAssetMustBeReadyBeforeAnyRelationIsChanged() {
        stubOwnedWorkAndProfile();
        ActorMediaAsset processingPhoto = readyAsset(81L, 7L, "photo");
        processingPhoto.setProcessStatus("processing");
        when(assetMapper.selectOwnedActiveByIdsForUpdate(any(), any())).thenReturn(List.of(processingPhoto));

        BizException error = assertThrows(BizException.class, () -> service.replaceWorkAssets(
                7L, 12L, bindings(binding(81L, "still", 1))));

        assertEquals(46013, error.getCode());
        verify(workAssetMapper, never()).deleteActiveByExperienceId(any());
        verify(workAssetMapper, never()).insert(any());
        verify(profileMapper, never()).incrementWorkLibraryVersion(any());
    }

    @Test
    void duplicateAssetIdIsRejectedAcrossUsages() {
        stubOwnedWorkAndProfile();

        BizException error = assertThrows(BizException.class, () -> service.replaceWorkAssets(
                7L, 12L, bindings(binding(81L, "still", 1), binding(81L, "clip", 1))));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), error.getCode());
        verify(assetMapper, never()).selectOwnedActiveByIdsForUpdate(any(), any());
        verify(workAssetMapper, never()).deleteActiveByExperienceId(any());
    }

    @Test
    void sortNumbersMustBePositiveUniqueAndContinuousWithinEachUsage() {
        stubOwnedWorkAndProfile();

        assertAll(
                () -> assertThrows(BizException.class, () -> service.replaceWorkAssets(
                        7L, 12L, bindings(binding(81L, "still", 0)))),
                () -> assertThrows(BizException.class, () -> service.replaceWorkAssets(
                        7L, 12L, bindings(binding(81L, "still", 1), binding(82L, "still", 1)))),
                () -> assertThrows(BizException.class, () -> service.replaceWorkAssets(
                        7L, 12L, bindings(binding(81L, "still", 1), binding(82L, "still", 3)))));

        verify(assetMapper, never()).selectOwnedActiveByIdsForUpdate(any(), any());
        verify(workAssetMapper, never()).deleteActiveByExperienceId(any());
        verify(profileMapper, never()).incrementWorkLibraryVersion(any());
    }

    @Test void retrySuccessCreatesANewAssetWithTheInheritedCategoryAndLeavesTheOldRowUntouched() {
        ActorMediaAsset failed = readyPhoto(7L); failed.setMediaType("pdf"); failed.setProcessStatus("failed");
        failed.setCategoryCode("resume");
        var file = new MockMultipartFile("file", "retry.pdf", "application/pdf", "%PDF-retry".getBytes());
        when(assetMapper.selectOne(any())).thenReturn(failed);
        when(storage.store(7L, "pdf", file)).thenReturn(new PrivateActorMediaStorage.StoredObjectRef("cos", "private", "retry.pdf", null));
        when(assetMapper.insert(any())).thenAnswer(call -> {
            ActorMediaAsset inserted = call.getArgument(0);
            assertEquals("processing", inserted.getProcessStatus());
            inserted.setAssetId(92L);
            return 1;
        });
        var page = new PrivateActorMediaStorage.StoredObjectRef("cos", "private", "retry-page.jpg", null);
        when(pdfProcessor.process(7L, file)).thenReturn(List.of(page));

        var result = service.retryPdf(7L, 81L, file);

        assertEquals(92L, result.getAssetId());
        assertEquals("ready", result.getProcessStatus());
        var inserted = org.mockito.ArgumentCaptor.forClass(ActorMediaAsset.class);
        verify(assetMapper).insert(inserted.capture());
        assertEquals("resume", inserted.getValue().getCategoryCode());
        verify(pdfLifecycle).finalizeReady(7L, 92L, List.of(page));
        verify(assetMapper, never()).updateById(failed);
        verify(assetMapper, never()).deleteById(81L);
    }

    @Test void retryCanonicalizesMissingLegacyPdfCategoryToResume() {
        assertLegacyPdfRetryUsesResume(null);
    }

    @Test void retryCanonicalizesOtherLegacyPdfCategoryToResume() {
        assertLegacyPdfRetryUsesResume("other");
    }

    @Test void retryFailureKeepsTheOldFailedRowAndReturnsTheNewFailedAsset() {
        ActorMediaAsset failed = readyPhoto(7L); failed.setMediaType("pdf"); failed.setProcessStatus("failed");
        failed.setCategoryCode("resume");
        var file = new MockMultipartFile("file", "retry.pdf", "application/pdf", "%PDF-retry".getBytes());
        when(assetMapper.selectOne(any())).thenReturn(failed);
        stubStoredPdf(file, 93L, "retry.pdf");
        when(pdfProcessor.process(7L, file)).thenThrow(
                new ActorPrivatePdfProcessor.PdfProcessingException("PDF_RENDER_FAILED", "PDF 页转换失败"));

        var result = service.retryPdf(7L, 81L, file);

        assertEquals(93L, result.getAssetId());
        assertEquals("failed", result.getProcessStatus());
        verify(pdfLifecycle).markFailed(7L, 93L, "PDF_RENDER_FAILED", "PDF 页转换失败");
        verify(assetMapper, never()).updateById(failed);
        verify(assetMapper, never()).deleteById(81L);
    }

    @Test void nonPdfRetryIsRejectedWithoutStorageOrDatabaseMutation() {
        ActorMediaAsset failedPhoto = readyPhoto(7L);
        failedPhoto.setProcessStatus("failed");
        when(assetMapper.selectOne(any())).thenReturn(failedPhoto);

        assertThrows(BizException.class,
                () -> service.retryPdf(7L, 81L, retryFile()));

        verifyRejectedRetryHasNoSideEffects();
    }

    @Test void nonFailedPdfRetryIsRejectedWithoutStorageOrDatabaseMutation() {
        ActorMediaAsset readyPdf = readyPhoto(7L);
        readyPdf.setMediaType("pdf");
        when(assetMapper.selectOne(any())).thenReturn(readyPdf);

        assertThrows(BizException.class,
                () -> service.retryPdf(7L, 81L, retryFile()));

        verifyRejectedRetryHasNoSideEffects();
    }

    @Test void foreignPdfRetryIsRejectedWithoutStorageOrDatabaseMutation() {
        when(assetMapper.selectOne(any())).thenReturn(null);

        assertThrows(BizException.class,
                () -> service.retryPdf(7L, 81L, retryFile()));

        verifyRejectedRetryHasNoSideEffects();
    }

    private ActorMediaAsset readyPhoto(Long userId) { ActorMediaAsset a = new ActorMediaAsset(); a.setAssetId(81L); a.setUserId(userId); a.setMediaType("photo"); a.setProcessStatus("ready"); a.setStorageProvider("cos"); a.setBucketCode("private"); a.setObjectKey("actor/7/a.jpg"); return a; }

    private ActorMediaAsset readyPdf(Long userId) { ActorMediaAsset a = readyPhoto(userId); a.setMediaType("pdf"); a.setCategoryCode("resume"); a.setObjectKey("actor/7/resume.pdf"); a.setPageCount(3); return a; }

    private void stubStoredPdf(MockMultipartFile file, Long assetId, String objectKey) {
        when(storage.store(7L, "pdf", file)).thenReturn(
                new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", objectKey, null));
        when(assetMapper.insert(any())).thenAnswer(call -> {
            ((ActorMediaAsset) call.getArgument(0)).setAssetId(assetId);
            return 1;
        });
    }

    private void assertLegacyPdfRetryUsesResume(String legacyCategoryCode) {
        ActorMediaAsset failed = readyPhoto(7L);
        failed.setMediaType("pdf");
        failed.setProcessStatus("failed");
        failed.setCategoryCode(legacyCategoryCode);
        var file = new MockMultipartFile("file", "retry.pdf", "application/pdf", "%PDF-retry".getBytes());
        when(assetMapper.selectOne(any())).thenReturn(failed);
        stubStoredPdf(file, 94L, "retry.pdf");
        when(pdfProcessor.process(7L, file)).thenReturn(List.of(
                new PrivateActorMediaStorage.StoredObjectRef(
                        "cos", "private-assets", "retry-page.jpg", null)));

        var result = service.retryPdf(7L, 81L, file);

        assertEquals("resume", result.getCategoryCode());
        var inserted = org.mockito.ArgumentCaptor.forClass(ActorMediaAsset.class);
        verify(assetMapper).insert(inserted.capture());
        assertEquals("resume", inserted.getValue().getCategoryCode());
        verify(assetMapper, never()).updateById(failed);
        verify(assetMapper, never()).deleteById(81L);
    }

    private MockMultipartFile retryFile() {
        return new MockMultipartFile("file", "retry.pdf", "application/pdf", "%PDF-retry".getBytes());
    }

    private void verifyRejectedRetryHasNoSideEffects() {
        verifyNoInteractions(storage, pdfProcessor, pdfLifecycle);
        verify(assetMapper, never()).insert(any());
        verify(assetMapper, never()).updateById(any());
        verify(assetMapper, never()).deleteById(any());
    }

    private void stubOwnedWorkAndProfile() {
        when(experienceMapper.selectOwnedActiveByIdForUpdate(7L, 12L)).thenReturn(ownedWork());
        when(profileMapper.selectOne(any())).thenReturn(profile());
    }

    private ActorExperience ownedWork() {
        ActorExperience work = new ActorExperience();
        work.setExperienceId(12L);
        work.setUserId(7L);
        work.setActorProfileId(9L);
        return work;
    }

    private ActorProfile profile() {
        ActorProfile profile = new ActorProfile();
        profile.setActorProfileId(9L);
        profile.setUserId(7L);
        profile.setWorkLibraryVersion(7L);
        return profile;
    }

    private ActorMediaAsset readyAsset(long assetId, long userId, String mediaType) {
        ActorMediaAsset asset = readyPhoto(userId);
        asset.setAssetId(assetId);
        asset.setMediaType(mediaType);
        asset.setObjectKey("actor/" + userId + "/" + assetId);
        return asset;
    }

    private ActorWorkAssetRespDTO workAssetSnapshot(long assetId, String usageCode, int sortNo) {
        ActorWorkAssetRespDTO asset = new ActorWorkAssetRespDTO();
        asset.setAssetId(assetId);
        asset.setUsageCode(usageCode);
        asset.setSortNo(sortNo);
        asset.setMediaType("still".equals(usageCode) ? "photo" : "video");
        asset.setProcessStatus("ready");
        return asset;
    }

    private ActorWorkAsset relation(long assetId, String usageCode, int sortNo) {
        ActorWorkAsset relation = new ActorWorkAsset();
        relation.setExperienceId(12L);
        relation.setAssetId(assetId);
        relation.setUsageCode(usageCode);
        relation.setSortNo(sortNo);
        return relation;
    }

    private ActorAssetBindingDTO binding(long assetId, String usageCode, Integer sortNo) {
        ActorAssetBindingDTO binding = new ActorAssetBindingDTO();
        binding.setAssetId(assetId);
        binding.setUsageCode(usageCode);
        binding.setSortNo(sortNo);
        return binding;
    }

    private ActorWorkAssetsReplaceDTO bindings(ActorAssetBindingDTO... bindings) {
        ActorWorkAssetsReplaceDTO request = new ActorWorkAssetsReplaceDTO();
        request.setBindings(List.of(bindings));
        return request;
    }
}
