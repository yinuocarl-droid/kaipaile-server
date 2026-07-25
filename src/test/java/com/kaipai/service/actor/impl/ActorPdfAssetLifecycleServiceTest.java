package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.mapper.actor.ActorMediaAssetPageMapper;
import com.kaipai.model.actor.entity.ActorMediaAssetPage;
import com.kaipai.service.actor.PrivateActorMediaStorage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@SpringJUnitConfig(ActorPdfAssetLifecycleServiceTest.TestConfiguration.class)
class ActorPdfAssetLifecycleServiceTest {
    @Autowired private ActorPdfAssetLifecycleService lifecycle;
    @Autowired private ActorMediaAssetMapper assetMapper;
    @Autowired private ActorMediaAssetPageMapper pageMapper;
    @Autowired private RecordingTransactionManager transactionManager;

    @BeforeEach
    void resetState() {
        reset(assetMapper, pageMapper);
        transactionManager.resetCounts();
    }

    @Test
    void finalizeReadyRunsThroughProxyAndCommitsAllPagesWithConditionalReadyUpdate() {
        when(pageMapper.insert(any())).thenReturn(1);
        when(assetMapper.markReady(90L, 7L, 2)).thenReturn(1);

        lifecycle.finalizeReady(7L, 90L, List.of(page("page-1.jpg"), page("page-2.jpg")));

        assertTrue(AopUtils.isAopProxy(lifecycle));
        ArgumentCaptor<ActorMediaAssetPage> pages = ArgumentCaptor.forClass(ActorMediaAssetPage.class);
        verify(pageMapper, org.mockito.Mockito.times(2)).insert(pages.capture());
        assertEquals(List.of(1, 2), pages.getAllValues().stream().map(ActorMediaAssetPage::getPageNo).toList());
        verify(assetMapper).markReady(90L, 7L, 2);
        assertEquals(1, transactionManager.commits);
        assertEquals(0, transactionManager.rollbacks);
    }

    @Test
    void pageInsertFailureRollsBackBeforeReadyUpdate() {
        when(pageMapper.insert(any())).thenReturn(1, 0);

        assertThrows(IllegalStateException.class,
                () -> lifecycle.finalizeReady(7L, 90L, List.of(page("page-1.jpg"), page("page-2.jpg"))));

        verify(assetMapper, never()).markReady(any(), any(), any());
        assertEquals(0, transactionManager.commits);
        assertEquals(1, transactionManager.rollbacks);
    }

    @Test
    void readyUpdateFailureRollsBackInsertedPages() {
        when(pageMapper.insert(any())).thenReturn(1);
        when(assetMapper.markReady(90L, 7L, 1)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> lifecycle.finalizeReady(7L, 90L, List.of(page("page-1.jpg"))));

        assertEquals(0, transactionManager.commits);
        assertEquals(1, transactionManager.rollbacks);
    }

    @Test
    void markFailedConditionallyClearsPagesInItsOwnTransaction() {
        when(assetMapper.markFailed(90L, 7L, "PDF_RENDER_FAILED", "PDF 页转换失败")).thenReturn(1);

        lifecycle.markFailed(7L, 90L, "PDF_RENDER_FAILED", "PDF 页转换失败");

        verify(pageMapper).deleteActiveByAssetId(90L);
        assertEquals(1, transactionManager.commits);
        assertEquals(0, transactionManager.rollbacks);
    }

    @Test
    void staleFailureCannotDeletePagesFromAnAssetThatIsNoLongerProcessing() {
        when(assetMapper.markFailed(90L, 7L, "PDF_FINALIZE_FAILED", "PDF 处理结果保存失败")).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> lifecycle.markFailed(7L, 90L, "PDF_FINALIZE_FAILED", "PDF 处理结果保存失败"));

        verify(pageMapper, never()).deleteActiveByAssetId(any());
        assertEquals(0, transactionManager.commits);
        assertEquals(1, transactionManager.rollbacks);
    }

    private PrivateActorMediaStorage.StoredObjectRef page(String objectKey) {
        return new PrivateActorMediaStorage.StoredObjectRef("cos", "private", objectKey, null);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean ActorMediaAssetMapper assetMapper() { return mock(ActorMediaAssetMapper.class); }
        @Bean ActorMediaAssetPageMapper pageMapper() { return mock(ActorMediaAssetPageMapper.class); }
        @Bean RecordingTransactionManager transactionManager() { return new RecordingTransactionManager(); }
        @Bean ActorPdfAssetLifecycleService lifecycle(
                ActorMediaAssetMapper assetMapper,
                ActorMediaAssetPageMapper pageMapper) {
            return new ActorPdfAssetLifecycleService(assetMapper, pageMapper);
        }
    }

    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        void resetCounts() { commits = 0; rollbacks = 0; }

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits += 1; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks += 1; }
    }
}
