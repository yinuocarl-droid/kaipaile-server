package com.kaipai.controller.api.actor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaipai.model.actor.card.dto.ActorCardMediaBindingItemRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardGenerateRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardPublicRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardRespDTO;
import com.kaipai.service.actor.ActorCardBackgroundService;
import com.kaipai.service.actor.ActorCardDraftService;
import com.kaipai.service.actor.ActorCardExpandImageService;
import com.kaipai.service.actor.ActorCardGenerateService;
import com.kaipai.service.actor.ActorCardMediaBindingService;
import com.kaipai.service.actor.ActorCardPublicService;
import com.kaipai.service.actor.ActorCardPublishService;
import java.util.List;
import org.mockito.InOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActorCardMediaBindingControllerContractTest {

    private ActorCardMediaBindingService bindingService;
    private ActorCardPublicService publicService;
    private ActorCardDraftService draftService;
    private ActorCardExpandImageService expandService;
    private ActorCardGenerateService generateService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        bindingService = mock(ActorCardMediaBindingService.class);
        publicService = mock(ActorCardPublicService.class);
        draftService = mock(ActorCardDraftService.class);
        expandService = mock(ActorCardExpandImageService.class);
        generateService = mock(ActorCardGenerateService.class);
        ActorCardController controller = new ActorCardController(
                draftService,
                bindingService,
                mock(ActorCardBackgroundService.class),
                expandService,
                generateService,
                mock(ActorCardPublishService.class),
                publicService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getReturnsLegacyOrBoundItemsForRequestedUsage() throws Exception {
        ActorCardMediaBindingRespDTO response = new ActorCardMediaBindingRespDTO();
        response.setCardId(11L);
        response.setUsage("life_photo");
        ActorCardMediaBindingItemRespDTO item = new ActorCardMediaBindingItemRespDTO();
        item.setAssetId(51L);
        item.setSortOrder(0);
        item.setPreviewUrl("https://signed.test/51");
        response.setItems(List.of(item));
        when(bindingService.list(7L, 11L, "life_photo", null)).thenReturn(response);

        mockMvc.perform(get("/actor-card/draft/11/media")
                        .param("usage", "life_photo")
                        .principal(new TestingAuthenticationToken(7L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usage").value("life_photo"))
                .andExpect(jsonPath("$.data.items[0].assetId").value(51))
                .andExpect(jsonPath("$.data.items[0].previewUrl").value("https://signed.test/51"));
    }

    @Test
    void putAcceptsAssetIdsAndSortOrderWithoutLegacyUrl() throws Exception {
        ActorCardMediaBindingRespDTO response = new ActorCardMediaBindingRespDTO();
        response.setCardId(11L);
        response.setUsage("source_image");
        response.setItems(List.of());
        when(bindingService.replace(any(), any(), any())).thenReturn(response);

        mockMvc.perform(put("/actor-card/draft/11/media")
                        .principal(new TestingAuthenticationToken(7L, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usage": "source_image",
                                  "items": [{"assetId": 51, "sortOrder": 0}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usage").value("source_image"));

        verify(bindingService).replace(any(Long.class), any(Long.class), any(ActorCardMediaBindingReqDTO.class));
        verify(expandService).requestAutomaticExpansion(7L, 11L);
    }

    @Test
    void stepOneSaveTriggersInternalExpansionAfterTheSaveSucceeds() throws Exception {
        mockMvc.perform(put("/actor-card/draft/11/step")
                        .principal(new TestingAuthenticationToken(7L, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentStep": 1,
                                  "sourceImageUrl": "https://cdn.test/source.jpg",
                                  "backgroundImageUrl": "https://cdn.test/source.jpg"
                                }
                                """))
                .andExpect(status().isOk());

        verify(draftService).saveStep(any(Long.class), any(Long.class), any());
        verify(expandService).requestAutomaticExpansion(7L, 11L);
    }

    @Test
    void draftPreviewUsesAuthenticatedOwnerAndPublicViewContract() throws Exception {
        ActorCardPublicRespDTO response = new ActorCardPublicRespDTO();
        response.setId(11L);
        response.setTitle("演员卡预览");
        when(publicService.getOwnerPreview(7L, 11L)).thenReturn(response);

        mockMvc.perform(get("/actor-card/draft/11/preview")
                        .principal(new TestingAuthenticationToken(7L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.title").value("演员卡预览"));

        verify(publicService).getOwnerPreview(7L, 11L);
    }

    @Test
    void historicalDraftReadRequestsIdempotentInternalExpansion() throws Exception {
        ActorCardRespDTO response = new ActorCardRespDTO();
        response.setId(11L);
        response.setTitle("历史演员卡");
        when(draftService.getDraft(7L, 11L)).thenReturn(response);

        mockMvc.perform(get("/actor-card/draft/11")
                        .principal(new TestingAuthenticationToken(7L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.title").value("历史演员卡"));

        InOrder order = inOrder(draftService, expandService);
        order.verify(draftService).getDraft(7L, 11L);
        order.verify(expandService).requestAutomaticExpansion(7L, 11L);
    }

    @Test
    void directGenerateRequestsInternalExpansionBeforeLayoutGeneration() throws Exception {
        ActorCardGenerateRespDTO response = new ActorCardGenerateRespDTO();
        response.setTaskId("acgen_test");
        response.setStatus("pending");
        when(generateService.submit(7L, 11L)).thenReturn(response);

        mockMvc.perform(post("/actor-card/draft/11/generate")
                        .principal(new TestingAuthenticationToken(7L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value("acgen_test"))
                .andExpect(jsonPath("$.data.status").value("pending"));

        InOrder order = inOrder(expandService, generateService);
        order.verify(expandService).requestAutomaticExpansion(7L, 11L);
        order.verify(generateService).submit(7L, 11L);
    }

    @Test
    void anonymousPublicViewDoesNotTriggerInternalExpansion() throws Exception {
        ActorCardPublicRespDTO response = new ActorCardPublicRespDTO();
        response.setId(11L);
        when(publicService.getPublicView(11L)).thenReturn(response);

        mockMvc.perform(get("/actor-card/public/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));

        verify(expandService, never()).requestAutomaticExpansion(any(), any());
    }
}
