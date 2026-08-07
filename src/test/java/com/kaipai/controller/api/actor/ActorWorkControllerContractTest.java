package com.kaipai.controller.api.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaipai.model.actor.dto.ActorWorkAssetRespDTO;
import com.kaipai.model.actor.dto.ActorWorkAssetsReplaceDTO;
import com.kaipai.service.actor.ActorMediaAssetService;
import com.kaipai.service.actor.ActorWorkService;
import java.util.List;
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
    void getWorkAssetsReturnsOnlyTheOrderedSevenFieldSnapshot() throws Exception {
        ActorWorkAssetRespDTO still = workAsset(81L, "still", 1, "photo",
                "work_still", "scene-01.jpg", "ready");
        ActorWorkAssetRespDTO clip = workAsset(82L, "clip", 1, "video",
                null, null, "ready");
        when(mediaAssetService.workAssets(7L, 12L)).thenReturn(List.of(still, clip));

        mockMvc.perform(get("/actor/works/12/assets")
                        .principal(new TestingAuthenticationToken(7L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].length()").value(7))
                .andExpect(jsonPath("$.data[0].assetId").value(81))
                .andExpect(jsonPath("$.data[0].usageCode").value("still"))
                .andExpect(jsonPath("$.data[0].sortNo").value(1))
                .andExpect(jsonPath("$.data[0].mediaType").value("photo"))
                .andExpect(jsonPath("$.data[0].categoryCode").value("work_still"))
                .andExpect(jsonPath("$.data[0].originalName").value("scene-01.jpg"))
                .andExpect(jsonPath("$.data[0].processStatus").value("ready"))
                .andExpect(jsonPath("$.data[1].assetId").value(82))
                .andExpect(jsonPath("$.data[1].usageCode").value("clip"))
                .andExpect(jsonPath("$.data[0].accessUrl").doesNotExist())
                .andExpect(jsonPath("$.data[0].bucket").doesNotExist())
                .andExpect(jsonPath("$.data[0].objectKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].storage").doesNotExist());

        verify(mediaAssetService).workAssets(7L, 12L);
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

    private ActorWorkAssetRespDTO workAsset(
            Long assetId,
            String usageCode,
            Integer sortNo,
            String mediaType,
            String categoryCode,
            String originalName,
            String processStatus) {
        ActorWorkAssetRespDTO asset = new ActorWorkAssetRespDTO();
        asset.setAssetId(assetId);
        asset.setUsageCode(usageCode);
        asset.setSortNo(sortNo);
        asset.setMediaType(mediaType);
        asset.setCategoryCode(categoryCode);
        asset.setOriginalName(originalName);
        asset.setProcessStatus(processStatus);
        return asset;
    }
}
