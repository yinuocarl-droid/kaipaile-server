package com.kaipai.controller.api.actor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaipai.model.actor.card.dto.ActorCardVisualInputDTO.CapabilitiesResp;
import com.kaipai.model.actor.card.dto.ActorCardVisualInputDTO.Resp;
import com.kaipai.model.actor.card.dto.ActorCardVisualInputDTO.SaveReq;
import com.kaipai.service.actor.ActorCardExpandImageService;
import com.kaipai.service.actor.ActorCardVisualInputService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActorCardVisualInputControllerContractTest {

    private ActorCardVisualInputService service;
    private ActorCardExpandImageService expandService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ActorCardVisualInputService.class);
        expandService = mock(ActorCardExpandImageService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new ActorCardVisualInputController(service, expandService)).build();
    }

    @Test
    void legacyCardReturnsNullDataInsteadOfAnInventedInput() throws Exception {
        when(service.get(7L, 11L)).thenReturn(null);

        mvc.perform(get("/actor-card/draft/11/visual-input")
                        .principal(new TestingAuthenticationToken(7L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void saveKeepsPersonAndBackgroundAsIndependentIdsThenSubmitsBackendGeneration() throws Exception {
        Resp response = response();
        when(service.save(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(11L), any(SaveReq.class))).thenReturn(response);
        when(service.get(7L, 11L)).thenReturn(response);

        mvc.perform(put("/actor-card/draft/11/visual-input")
                        .principal(new TestingAuthenticationToken(7L, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"generationMode":"person_background_ai","personAssetId":51,
                                 "backgroundId":31,"expectedRevision":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personAssetId").value(51))
                .andExpect(jsonPath("$.data.backgroundId").value(31));

        verify(expandService).submitVisualInput(7L, 11L);
    }

    @Test
    void exposesExplicitDualReferenceAvailabilityWithoutSecrets() throws Exception {
        when(service.capabilities()).thenReturn(new CapabilitiesResp(
                "kplyyk", "gpt-image-2", true, false, "当前 provider 不支持双参考图"));

        mvc.perform(get("/actor-card/draft/11/visual-input/capabilities")
                        .principal(new TestingAuthenticationToken(7L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerCode").value("kplyyk"))
                .andExpect(jsonPath("$.data.personBackgroundAiAvailable").value(false));
    }

    @Test
    void retryTargetsOnlyTheCurrentSavedInput() throws Exception {
        when(service.get(7L, 11L)).thenReturn(response());

        mvc.perform(post("/actor-card/draft/11/visual-input/retry")
                        .principal(new TestingAuthenticationToken(7L, null)))
                .andExpect(status().isOk());

        verify(expandService).retryVisualInput(7L, 11L);
    }

    private Resp response() {
        return new Resp(11L, "person_background_ai", "asset", 51L, null, null, 31L,
                1L, "f".repeat(64), null, null);
    }
}
