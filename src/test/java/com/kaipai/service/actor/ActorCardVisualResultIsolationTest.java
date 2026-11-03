package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardVisualInputMapper;
import org.junit.jupiter.api.Test;

class ActorCardVisualResultIsolationTest {

    @Test
    void currentFingerprintAcceptsTheResultButAStaleTaskCannotOverwriteIt() {
        ActorCardVisualInputMapper visualMapper = mock(ActorCardVisualInputMapper.class);
        ActorCardExpandResultService service = new ActorCardExpandResultService(
                mock(ActorCardMapper.class), mock(ActorCardMediaBindingService.class), visualMapper);
        when(visualMapper.applyResultIfCurrent(
                7L, 11L, "current", "task-current", "https://managed.test/current.png"))
                .thenReturn(1);
        when(visualMapper.applyResultIfCurrent(
                7L, 11L, "stale", "task-stale", "https://managed.test/stale.png"))
                .thenReturn(0);

        assertTrue(service.applyVisualIfCurrent(
                7L, 11L, "current", "task-current", "https://managed.test/current.png"));
        assertFalse(service.applyVisualIfCurrent(
                7L, 11L, "stale", "task-stale", "https://managed.test/stale.png"));

        verify(visualMapper).applyResultIfCurrent(
                7L, 11L, "stale", "task-stale", "https://managed.test/stale.png");
    }
}
