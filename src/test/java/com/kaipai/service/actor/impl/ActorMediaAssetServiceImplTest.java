package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.*;
import com.kaipai.mapper.card.ShareCardAssetMapper;
import com.kaipai.model.actor.dto.ActorAssetQueryDTO;
import com.kaipai.model.actor.dto.ActorAssetUpdateDTO;
import com.kaipai.model.actor.dto.ActorCurrentResumeUpdateDTO;
import com.kaipai.model.actor.entity.*;
import com.kaipai.service.actor.PrivateActorMediaStorage;
import com.kaipai.service.actor.ActorPrivatePdfProcessor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
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
    private ActorMediaAssetServiceImpl service;

    @BeforeEach void setUp() {
        assetMapper = mock(ActorMediaAssetMapper.class); profileMapper = mock(ActorProfileMapper.class);
        profileAssetMapper = mock(ActorProfileAssetMapper.class); workAssetMapper = mock(ActorWorkAssetMapper.class); experienceMapper = mock(ActorExperienceMapper.class);
        shareAssetMapper = mock(ShareCardAssetMapper.class); storage = mock(PrivateActorMediaStorage.class);
        pageMapper = mock(ActorMediaAssetPageMapper.class); pdfProcessor = mock(ActorPrivatePdfProcessor.class);
        service = new ActorMediaAssetServiceImpl(assetMapper, profileMapper, profileAssetMapper, workAssetMapper, experienceMapper, shareAssetMapper, pageMapper, storage, pdfProcessor);
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

    @Test void ownerAccessRequiresOwnerAndReadyAsset() {
        ActorMediaAsset asset = readyPhoto(7L); when(assetMapper.selectOne(any())).thenReturn(asset, null);
        when(storage.issueAccessUrl(any(), any(), any())).thenReturn(new PrivateActorMediaStorage.SignedAccess("https://signed", Instant.now().plusSeconds(600)));
        assertEquals("https://signed", service.issueOwnerAccessUrl(7L, 81L).getAccessUrl());
        assertEquals(46012, assertThrows(BizException.class, () -> service.issueOwnerAccessUrl(8L, 81L)).getCode());
    }

    @Test void referencedAssetCannotBeDeleted() {
        when(assetMapper.selectOne(any())).thenReturn(readyPhoto(7L));
        when(profileMapper.selectCount(any())).thenReturn(1L);
        assertEquals(46014, assertThrows(BizException.class, () -> service.delete(7L, 81L)).getCode());
        verify(assetMapper, never()).deleteById(any(Long.class));
    }

    @Test void deletingPdfAlsoDeletesPersistedPageObjects() {
        ActorMediaAsset pdf = readyPhoto(7L); pdf.setMediaType("pdf"); pdf.setObjectKey("resume.pdf");
        ActorMediaAssetPage page = new ActorMediaAssetPage(); page.setPageId(3L); page.setAssetId(81L); page.setImageObjectKey("page-1.jpg");
        when(assetMapper.selectOne(any())).thenReturn(pdf);
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

    @Test void onlyOwnedReadyPdfCanBecomeCurrentResume() {
        ActorMediaAsset pdf = readyPhoto(7L);
        pdf.setMediaType("pdf");
        ActorProfile profile = new ActorProfile(); profile.setActorProfileId(9L); profile.setUserId(7L);
        when(assetMapper.selectOne(any())).thenReturn(pdf);
        when(profileMapper.selectOne(any())).thenReturn(profile);
        ActorCurrentResumeUpdateDTO request = new ActorCurrentResumeUpdateDTO(); request.setAssetId(81L);

        service.setCurrentResume(7L, request);

        assertEquals(81L, profile.getCurrentResumeAssetId());
        verify(profileMapper).updateById(profile);
    }

    @Test void failedPdfCannotBecomeCurrentResume() {
        ActorMediaAsset pdf = readyPhoto(7L); pdf.setMediaType("pdf"); pdf.setProcessStatus("failed");
        when(assetMapper.selectOne(any())).thenReturn(pdf);
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
        var pages = org.mockito.ArgumentCaptor.forClass(ActorMediaAssetPage.class);
        verify(pageMapper, times(2)).insert(pages.capture());
        assertEquals(java.util.List.of(1, 2), pages.getAllValues().stream().map(ActorMediaAssetPage::getPageNo).toList());
        assertEquals(2, result.getPageCount());
    }

    @Test void pdfConversionFailureIsPersistedAndNotReportedReady() {
        var file = new MockMultipartFile("file", "坏简历.pdf", "application/pdf", "%PDF-bad".getBytes());
        when(assetMapper.insert(any())).thenAnswer(call -> { ((ActorMediaAsset) call.getArgument(0)).setAssetId(91L); return 1; });
        when(storage.store(7L, "pdf", file)).thenReturn(new PrivateActorMediaStorage.StoredObjectRef("cos", "private-assets", "actor-private/7/pdf/bad.pdf", null));
        when(pdfProcessor.process(7L, file)).thenThrow(new ActorPrivatePdfProcessor.PdfProcessingException("PDF_RENDER_FAILED", "PDF 页转换失败"));

        var result = service.upload(7L, "pdf", "resume", file);

        assertEquals("failed", result.getProcessStatus());
        var asset = org.mockito.ArgumentCaptor.forClass(ActorMediaAsset.class);
        verify(assetMapper).updateById(asset.capture());
        assertEquals("PDF_RENDER_FAILED", asset.getValue().getFailureCode());
    }

    @Test void readyPhotoCanBeBoundToProfileAndRejectsNonPhoto() {
        ActorMediaAsset photo = readyPhoto(7L);
        ActorProfile profile = new ActorProfile(); profile.setActorProfileId(9L); profile.setUserId(7L);
        when(assetMapper.selectOne(any())).thenReturn(photo); when(profileMapper.selectOne(any())).thenReturn(profile);

        service.bindProfileAsset(7L, 81L, "portrait", 1);

        var relation = org.mockito.ArgumentCaptor.forClass(ActorProfileAsset.class);
        verify(profileAssetMapper).insert(relation.capture());
        assertEquals(9L, relation.getValue().getActorProfileId());
        assertEquals("portrait", relation.getValue().getUsageCode());
    }

    @Test void readyWorkAssetRequiresOwnedWorkAndAssetType() {
        ActorMediaAsset video = readyPhoto(7L); video.setMediaType("video");
        ActorExperience work = new ActorExperience(); work.setExperienceId(12L); work.setUserId(7L);
        when(assetMapper.selectOne(any())).thenReturn(video);
        when(experienceMapper.selectOne(any())).thenReturn(work);

        service.bindWorkAsset(7L, 12L, 81L, "clip", 1);

        verify(workAssetMapper).insert(any(ActorWorkAsset.class));
    }

    @Test void retryRequiresFailedPdfAndCreatesFreshProcessingLifecycle() {
        ActorMediaAsset failed = readyPhoto(7L); failed.setMediaType("pdf"); failed.setProcessStatus("failed");
        var file = new MockMultipartFile("file", "retry.pdf", "application/pdf", "%PDF-retry".getBytes());
        when(assetMapper.selectOne(any())).thenReturn(failed);
        when(storage.store(7L, "pdf", file)).thenReturn(new PrivateActorMediaStorage.StoredObjectRef("cos", "private", "retry.pdf", null));
        when(assetMapper.insert(any())).thenAnswer(call -> { ((ActorMediaAsset) call.getArgument(0)).setAssetId(92L); return 1; });
        when(pdfProcessor.process(7L, file)).thenReturn(java.util.List.of());

        var result = service.retryPdf(7L, 81L, file);

        assertEquals(92L, result.getAssetId());
        assertEquals("ready", result.getProcessStatus());
    }

    private ActorMediaAsset readyPhoto(Long userId) { ActorMediaAsset a = new ActorMediaAsset(); a.setAssetId(81L); a.setUserId(userId); a.setMediaType("photo"); a.setProcessStatus("ready"); a.setStorageProvider("cos"); a.setBucketCode("private"); a.setObjectKey("actor/7/a.jpg"); return a; }
}
