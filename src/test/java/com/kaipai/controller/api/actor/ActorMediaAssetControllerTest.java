package com.kaipai.controller.api.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.kaipai.common.result.PageResult;
import com.kaipai.model.actor.dto.*;
import com.kaipai.service.actor.ActorMediaAssetService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.mock.web.MockMultipartFile;

class ActorMediaAssetControllerTest {
    @Test void exposesOwnerAssetCrudCurrentResumeAndShortAccessUrl() {
        ActorMediaAssetService service = mock(ActorMediaAssetService.class);
        ActorMediaAssetController controller = new ActorMediaAssetController(service);
        var auth = new UsernamePasswordAuthenticationToken(7L, null);
        ActorAssetQueryDTO query = new ActorAssetQueryDTO();
        when(service.list(7L, query)).thenReturn(new PageResult<>(1, List.of(new ActorAssetRespDTO())));
        when(service.asset(7L, 81L)).thenReturn(new ActorAssetRespDTO());
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] {1});

        assertEquals(1L, controller.list(auth, query).getData().getTotal());
        controller.get(auth, 81L);
        controller.create(auth, "photo", "model_card", file);
        controller.update(auth, 81L, new ActorAssetUpdateDTO());
        controller.setCurrentResume(auth, new ActorCurrentResumeUpdateDTO());
        controller.accessUrl(auth, 81L);
        controller.delete(auth, 81L);

        verify(service).asset(7L, 81L);
        verify(service).upload(7L, "photo", "model_card", file);
        verify(service).delete(7L, 81L);
    }
}
