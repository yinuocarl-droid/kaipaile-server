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
