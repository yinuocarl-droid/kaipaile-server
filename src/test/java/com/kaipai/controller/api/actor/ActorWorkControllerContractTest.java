package com.kaipai.controller.api.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaipai.model.actor.dto.ActorWorkAssetsReplaceDTO;
import com.kaipai.service.actor.ActorMediaAssetService;
import com.kaipai.service.actor.ActorWorkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActorWorkControllerContractTest {

    private ActorMediaAssetService mediaAssetService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ActorWorkService workService = mock(ActorWorkService.class);
        mediaAssetService = mock(ActorMediaAssetService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ActorWorkController(workService, mediaAssetService))
                .build();
    }

    @Test
    void putWorkAssetsUsesTheCompleteSetReplacementContract() throws Exception {
        mockMvc.perform(put("/actor/works/12/assets")
                        .principal(new TestingAuthenticationToken(7L, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bindings": [
                                    {"assetId":81,"usageCode":"still","sortNo":1},
                                    {"assetId":82,"usageCode":"clip","sortNo":1}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ActorWorkAssetsReplaceDTO> request =
                ArgumentCaptor.forClass(ActorWorkAssetsReplaceDTO.class);
        verify(mediaAssetService).replaceWorkAssets(eq(7L), eq(12L), request.capture());
        assertEquals(2, request.getValue().getBindings().size());
        assertEquals(81L, request.getValue().getBindings().get(0).getAssetId());
        assertEquals("clip", request.getValue().getBindings().get(1).getUsageCode());
    }
}
