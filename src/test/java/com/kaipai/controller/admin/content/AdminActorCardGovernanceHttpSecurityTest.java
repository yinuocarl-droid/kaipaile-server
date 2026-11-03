package com.kaipai.controller.admin.content;

import com.kaipai.common.config.SecurityConfig;
import com.kaipai.common.exception.GlobalExceptionHandler;
import com.kaipai.common.filter.JwtFilter;
import com.kaipai.model.actor.card.dto.AdminActorCardGovernancePageDTO;
import com.kaipai.model.actor.card.dto.AdminActorCardGovernanceQueryDTO;
import com.kaipai.service.actor.AdminActorCardGovernanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 00-265: 真实 SecurityFilterChain + 方法级鉴权 + 查询参数绑定。 */
@WebMvcTest(controllers = AdminActorCardGovernanceController.class)
@ContextConfiguration(classes = {SecurityConfig.class, GlobalExceptionHandler.class, AdminActorCardGovernanceController.class})
class AdminActorCardGovernanceHttpSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean JwtFilter jwtFilter;
    @MockBean AdminActorCardGovernanceService governance;

    @BeforeEach
    void jwtPassThrough() throws Exception {
        doAnswer(call -> { ((FilterChain) call.getArgument(2)).doFilter(call.getArgument(0), call.getArgument(1)); return null; })
                .when(jwtFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    @Test
    void anonymousAndAdminWithoutPagePermissionAreRejected() throws Exception {
        mvc.perform(get("/admin/content/actor-cards")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/content/actor-cards").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(governance);
    }

    @Test
    void sharePagePermissionReadsAndBindsQueryParameters() throws Exception {
        AdminActorCardGovernancePageDTO page = new AdminActorCardGovernancePageDTO();
        page.getStatusCounts().setTotal(4);
        page.getStatusCounts().setPublished(3);
        page.getStatusCounts().setDraft(1);
        when(governance.list(any())).thenReturn(page);
        var reader = user("reader").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("page.content.share-cards"));

        mvc.perform(get("/admin/content/actor-cards").with(reader)
                        .param("pageNo", "2").param("pageSize", "50").param("userId", "7")
                        .param("status", "draft").param("style", "urban"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCounts.total").value(4))
                .andExpect(jsonPath("$.data.statusCounts.published").value(3));

        ArgumentCaptor<AdminActorCardGovernanceQueryDTO> captor = ArgumentCaptor.forClass(AdminActorCardGovernanceQueryDTO.class);
        verify(governance).list(captor.capture());
        assertEquals(2, captor.getValue().getPageNo());
        assertEquals(50, captor.getValue().getPageSize());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("draft", captor.getValue().getStatus());
        assertEquals("urban", captor.getValue().getStyle());
    }

    @Test
    void pageSizeAboveOneHundredIsRejected() throws Exception {
        var reader = user("reader").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("page.content.share-cards"));
        // @Max(100) 校验失败由 GlobalExceptionHandler 收口，关键是请求不能到达 service
        mvc.perform(get("/admin/content/actor-cards").with(reader).param("pageSize", "500"));
        verifyNoInteractions(governance);
    }
}
