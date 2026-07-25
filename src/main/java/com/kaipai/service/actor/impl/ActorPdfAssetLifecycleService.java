package com.kaipai.service.actor.impl;

import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.mapper.actor.ActorMediaAssetPageMapper;
import com.kaipai.model.actor.entity.ActorMediaAssetPage;
import com.kaipai.service.actor.PrivateActorMediaStorage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ActorPdfAssetLifecycleService {
    private final ActorMediaAssetMapper assetMapper;
    private final ActorMediaAssetPageMapper pageMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void finalizeReady(
            Long userId,
            Long assetId,
            List<PrivateActorMediaStorage.StoredObjectRef> pageObjects) {
        if (pageObjects == null || pageObjects.isEmpty()) {
            throw new IllegalStateException("PDF processor returned no pages");
        }
        for (int index = 0; index < pageObjects.size(); index++) {
            PrivateActorMediaStorage.StoredObjectRef pageObject = pageObjects.get(index);
            if (pageObject == null || !StringUtils.hasText(pageObject.objectKey())) {
                throw new IllegalStateException("PDF processor returned an invalid page object");
            }
            ActorMediaAssetPage page = new ActorMediaAssetPage();
            page.setAssetId(assetId);
            page.setPageNo(index + 1);
            page.setImageObjectKey(pageObject.objectKey());
            page.setProcessStatus("ready");
            if (pageMapper.insert(page) != 1) {
                throw new IllegalStateException("PDF page insert failed");
            }
        }
        if (assetMapper.markReady(assetId, userId, pageObjects.size()) != 1) {
            throw new IllegalStateException("PDF ready transition failed");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(
            Long userId,
            Long assetId,
            String failureCode,
            String failureMessage) {
        if (assetMapper.markFailed(assetId, userId, failureCode, failureMessage) != 1) {
            throw new IllegalStateException("PDF failed transition failed");
        }
        pageMapper.deleteActiveByAssetId(assetId);
    }
}
