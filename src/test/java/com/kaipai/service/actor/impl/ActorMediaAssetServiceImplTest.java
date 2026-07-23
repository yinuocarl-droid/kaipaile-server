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
    private ShareCardAssetMapper shareAssetMapper;
    private PrivateActorMediaStorage storage;
    private ActorMediaAssetServiceImpl service;

    @BeforeEach void setUp() {
        assetMapper = mock(ActorMediaAssetMapper.class); profileMapper = mock(ActorProfileMapper.class);
        profileAssetMapper = mock(ActorProfileAssetMapper.class); workAssetMapper = mock(ActorWorkAssetMapper.class);
        shareAssetMapper = mock(ShareCardAssetMapper.class); storage = mock(PrivateActorMediaStorage.class);
        service = new ActorMediaAssetServiceImpl(assetMapper, profileMapper, profileAssetMapper, workAssetMapper, shareAssetMapper, storage);
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

    private ActorMediaAsset readyPhoto(Long userId) { ActorMediaAsset a = new ActorMediaAsset(); a.setAssetId(81L); a.setUserId(userId); a.setMediaType("photo"); a.setProcessStatus("ready"); a.setStorageProvider("cos"); a.setBucketCode("private"); a.setObjectKey("actor/7/a.jpg"); return a; }
}
