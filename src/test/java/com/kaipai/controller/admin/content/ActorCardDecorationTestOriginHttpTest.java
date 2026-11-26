package com.kaipai.controller.admin.content;

import com.kaipai.common.config.SecurityConfig;
import com.kaipai.common.filter.JwtFilter;
import com.kaipai.service.actor.ActorCardDecorationAssignmentService;
import com.kaipai.service.actor.ActorCardDecorationService;
import com.kaipai.service.actor.ActorCardPublicService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Loads the deployed test profile, not a test-only allowed-origin override. */
@ActiveProfiles("test")
@WebMvcTest(controllers = {ActorCardDecorationController.class, ActorCardDecorationBindingController.class})
@ContextConfiguration(classes = {SecurityConfig.class, ActorCardDecorationController.class, ActorCardDecorationBindingController.class})
class ActorCardDecorationTestOriginHttpTest {
    @Autowired MockMvc mvc;
    @MockBean JwtFilter jwtFilter;
    @MockBean ActorCardDecorationService decorations;
    @MockBean ActorCardDecorationAssignmentService assignments;
    @MockBean ActorCardPublicService cards;

    @Test
    void testAdminBrowserMayPreflightAuthenticatedDecorationWrites() throws Exception {
        mvc.perform(options("/admin/content/templates/7/decoration/draft")
                        .header("Origin", "https://test.kplyyk.com")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://test.kplyyk.com"));
        verifyNoInteractions(decorations, assignments, cards);
    }

    @Test
    void testProfileDoesNotGrantArbitraryOrProductionOriginAccess() throws Exception {
        for (String origin : new String[]{"https://untrusted.invalid", "https://test.kplyyk.com.evil.invalid", "https://kplyyk.com"}) {
            mvc.perform(options("/admin/content/templates/7/decoration/draft")
                            .header("Origin", origin).header("Access-Control-Request-Method", "PUT"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
        verifyNoInteractions(decorations, assignments, cards);
    }
}
