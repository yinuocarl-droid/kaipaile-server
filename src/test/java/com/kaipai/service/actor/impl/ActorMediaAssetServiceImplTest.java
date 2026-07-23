package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.*;
import com.kaipai.mapper.card.ShareCardAssetMapper;
import com.kaipai.model.actor.entity.*;
import com.kaipai.service.actor.PrivateActorMediaStorage;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    private ActorMediaAsset readyPhoto(Long userId) { ActorMediaAsset a = new ActorMediaAsset(); a.setAssetId(81L); a.setUserId(userId); a.setMediaType("photo"); a.setProcessStatus("ready"); a.setStorageProvider("cos"); a.setBucketCode("private"); a.setObjectKey("actor/7/a.jpg"); return a; }
}
