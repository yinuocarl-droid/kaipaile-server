package com.kaipai.controller.admin.content;

import com.kaipai.common.config.SecurityConfig;
import com.kaipai.common.filter.JwtFilter;
import com.kaipai.common.exception.GlobalExceptionHandler;
import com.kaipai.service.actor.ActorCardDecorationAssignmentService;
import com.kaipai.service.actor.ActorCardDecorationService;
import com.kaipai.service.actor.ActorCardPublicService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Actual application SecurityFilterChain + method-security proxies, not annotation reflection. */
@WebMvcTest(controllers = {ActorCardDecorationController.class, ActorCardDecorationBindingController.class, AdminActorCardBackgroundController.class})
@ContextConfiguration(classes = {SecurityConfig.class, GlobalExceptionHandler.class, ActorCardDecorationController.class, ActorCardDecorationBindingController.class, AdminActorCardBackgroundController.class})
class ActorCardDecorationHttpSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean JwtFilter jwtFilter;
    @MockBean ActorCardDecorationService decorations;
    @MockBean ActorCardDecorationAssignmentService assignments;
    @MockBean ActorCardPublicService cards;
    @MockBean com.kaipai.service.actor.AdminActorCardBackgroundService backgrounds;

    @BeforeEach
    void jwtPassThrough() throws Exception {
        // Authentication is supplied by spring-security-test; production URL and
        // @PreAuthorize enforcement are real. No JWT key, Redis, or remote service.
        doAnswer(call -> { ((FilterChain) call.getArgument(2)).doFilter(call.getArgument(0), call.getArgument(1)); return null; })
                .when(jwtFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    @Test
    void anonymousAndNonAdminCannotReadPrivateBindings() throws Exception {
        mvc.perform(get("/admin/content/actor-card-decoration-bindings/default")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/content/actor-card-decoration-bindings/default")
                .with(user("actor").authorities(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("page.content.templates"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assignments, cards);
    }

    @Test
    void adminStillNeedsPagePermissionForBindingsAndRealCardPreview() throws Exception {
        mvc.perform(get("/admin/content/actor-card-decoration-bindings/default").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/content/actor-card-decoration-bindings/preview/cards/11").param("generationMode", "person_ai")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        verifyNoInteractions(assignments, cards);
    }

    @Test
    void pagePermissionReadsButCannotPublishBindings() throws Exception {
        var reader = user("reader").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("page.content.templates"));
        mvc.perform(get("/admin/content/actor-card-decoration-bindings/default").with(reader)).andExpect(status().isOk());
        mvc.perform(put("/admin/content/actor-card-decoration-bindings/default").with(reader).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0,\"templateId\":7,\"versionId\":9,\"note\":\"test\"}"))
                .andExpect(status().isForbidden());
        verify(assignments).getDefaultBinding();
        verify(assignments, never()).saveDefaultBinding(any());
    }

    @Test
    void publishPermissionSavesBindingButDoesNotGrantRollback() throws Exception {
        var publisher = user("publisher").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("action.content.template.publish"));
        mvc.perform(put("/admin/content/actor-card-decoration-bindings/backgrounds/55").with(publisher).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0,\"templateId\":7,\"versionId\":9,\"note\":\"test\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/content/templates/7/decoration/activate/1").with(publisher).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1,\"note\":\"test\"}")).andExpect(status().isForbidden());
        verify(assignments).saveBackgroundBinding(eq(55L), any());
        verify(decorations, never()).activate(any(), anyInt(), any());
    }

    @Test
    void editAndRollbackAreIndependentActions() throws Exception {
        var editor = user("editor").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("action.content.template.edit"));
        mvc.perform(put("/admin/content/templates/7/decoration/draft").with(editor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0,\"config\":{\"schemaVersion\":1,\"modules\":[]}}"))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/content/templates/7/decoration/publish").with(editor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1,\"note\":\"test\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/content/templates/7/decoration/activate/1")
                .with(user("rollback").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("action.content.template.rollback")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1,\"note\":\"test\"}"))
                .andExpect(status().isOk());
        verify(decorations).save(eq(7L), any());
        verify(decorations, never()).publish(any(), any());
        verify(decorations).activate(eq(7L), eq(1), any());
    }
    @Test void publicCatalogReadAndWriteUseSeparateRealAuthorities() throws Exception {
        mvc.perform(get("/admin/content/actor-card-backgrounds")).andExpect(status().isUnauthorized());
        var viewer = user("viewer").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("page.content.templates"));
        mvc.perform(get("/admin/content/actor-card-backgrounds").with(viewer)).andExpect(status().isOk());
        mvc.perform(put("/admin/content/templates/7/decoration/editor-background").with(viewer).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0,\"backgroundId\":9}")).andExpect(status().isForbidden());
        var editor = user("editor").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("action.content.template.edit"));
        mvc.perform(put("/admin/content/templates/7/decoration/editor-background").with(editor).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0,\"backgroundId\":9}")).andExpect(status().isOk());
        verify(backgrounds).saveEditorBackground(eq(7L),any());
    }

}
