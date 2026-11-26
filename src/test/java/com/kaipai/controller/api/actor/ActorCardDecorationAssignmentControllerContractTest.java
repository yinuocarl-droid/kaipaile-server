package com.kaipai.controller.api.actor;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.DecorationEnvelope;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.UpgradePreviewResp;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.UpgradeReq;
import com.kaipai.service.actor.ActorCardDecorationAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActorCardDecorationAssignmentControllerContractTest {
    private ActorCardDecorationAssignmentService service;
    private MockMvc mvc;
    private DecorationEnvelope target;

    @BeforeEach
    void setUp() throws Exception {
        service = mock(ActorCardDecorationAssignmentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ActorCardDecorationAssignmentController(service)).build();
        target = new DecorationEnvelope(202L, 2L, 3, 1,
                new ObjectMapper().readTree("{\"schemaVersion\":1,\"modules\":[]}"));
    }

    @Test
    void previewReturnsNullableExpectedVersionAndTargetEnvelope() throws Exception {
        when(service.previewUpgrade(7L, 11L)).thenReturn(new UpgradePreviewResp(null, true, null, target));

        mvc.perform(get("/actor-card/11/decoration/upgrade-preview")
                        .principal(new TestingAuthenticationToken(7L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expectedVersionId").doesNotExist())
                .andExpect(jsonPath("$.data.upgradeAvailable").value(true))
                .andExpect(jsonPath("$.data.target.versionId").value(202));
    }

    @Test
    void confirmationAcceptsNullExpectedVersionForALegacyCard() throws Exception {
        when(service.confirmUpgrade(eq(7L), eq(11L), any(UpgradeReq.class))).thenReturn(target);

        mvc.perform(post("/actor-card/11/decoration/upgrade")
                        .principal(new TestingAuthenticationToken(7L, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersionId\":null,\"targetVersionId\":202}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateVersion").value(3));
    }
}
