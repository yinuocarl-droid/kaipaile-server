package com.kaipai.controller.admin.content;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.BindingResp;
import com.kaipai.model.actor.card.dto.ActorCardPublicRespDTO;
import com.kaipai.service.actor.ActorCardDecorationAssignmentService;
import com.kaipai.service.actor.ActorCardPublicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.access.prepost.PreAuthorize;

class ActorCardDecorationBindingControllerContractTest {
    private ActorCardDecorationAssignmentService assignment;
    private ActorCardPublicService publicService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        assignment = mock(ActorCardDecorationAssignmentService.class);
        publicService = mock(ActorCardPublicService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new ActorCardDecorationBindingController(assignment, publicService)).build();
    }

    @Test
    void unboundDefaultHasAStableRevisionZeroResponse() throws Exception {
        when(assignment.getDefaultBinding()).thenReturn(
                new BindingResp("default", 0L, 0, null, null, null, null));

        mvc.perform(get("/admin/content/actor-card-decoration-bindings/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceType").value("default"))
                .andExpect(jsonPath("$.data.revision").value(0));
    }

    @Test
    void saveUsesExplicitPublishedVersionAndExpectedRevision() throws Exception {
        when(assignment.saveBackgroundBinding(eq(55L), any())).thenReturn(
                new BindingResp("background", 55L, 1, 2L, 202L, 3, null));

        mvc.perform(put("/admin/content/actor-card-decoration-bindings/backgrounds/55")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0,\"templateId\":2,\"versionId\":202,\"note\":\"绑定\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceId").value(55))
                .andExpect(jsonPath("$.data.versionId").value(202));
    }

    @Test
    void authorizedRealCardPreviewUsesCurrentBindingWithoutMutatingAssignment() throws Exception {
        ActorCardPublicRespDTO preview = new ActorCardPublicRespDTO();
        preview.setId(11L);
        when(publicService.getAdminPreview(eq(11L), any())).thenReturn(preview);

        mvc.perform(get("/admin/content/actor-card-decoration-bindings/preview/cards/11")
                        .param("generationMode", "person_background_ai")
                        .param("backgroundId", "55"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));

        verify(assignment).previewForMode(11L, "person_background_ai", 55L);
        verify(assignment, never()).selectForMode(any(), any(), any(), any());
    }

    @Test
    void viewingAndRuntimeBindingMutationHaveSeparateAuthorities() throws Exception {
        assertEquals("hasAuthority('page.content.templates')",
                ActorCardDecorationBindingController.class
                        .getMethod("preview", Long.class, String.class, Long.class)
                        .getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('action.content.template.publish')",
                ActorCardDecorationBindingController.class
                        .getMethod("saveDefault",
                                com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.BindingSaveReq.class)
                        .getAnnotation(PreAuthorize.class).value());
    }
}
