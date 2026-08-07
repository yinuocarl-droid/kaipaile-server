package com.kaipai.service.actor.impl;

import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.mapper.actor.ActorMediaAssetPageMapper;
import com.kaipai.model.actor.entity.ActorMediaAssetPage;
import com.kaipai.service.actor.PrivateActorMediaStorage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class ActorPdfAssetLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(ActorPdfAssetLifecycleService.class);

    private final ActorMediaAssetMapper assetMapper;
    private final ActorMediaAssetPageMapper pageMapper;
    private final TransactionTemplate requiresNewTransaction;

    public ActorPdfAssetLifecycleService(
            ActorMediaAssetMapper assetMapper,
            ActorMediaAssetPageMapper pageMapper,
            PlatformTransactionManager transactionManager) {
        this.assetMapper = assetMapper;
        this.pageMapper = pageMapper;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

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

    public void markFailed(
            Long userId,
            Long assetId,
            String failureCode,
            String failureMessage) {
        requiresNewTransaction.executeWithoutResult(status -> {
            if (assetMapper.markFailed(assetId, userId, failureCode, failureMessage) != 1) {
                throw new IllegalStateException("PDF failed transition failed");
            }
        });
        try {
            requiresNewTransaction.executeWithoutResult(status -> pageMapper.deleteActiveByAssetId(assetId));
        } catch (RuntimeException cleanupFailure) {
            log.warn("Failed to clean up PDF pages after asset {} entered failed state", assetId, cleanupFailure);
        }
    }
}
