package com.kaipai.common.filter;

import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.common.util.JwtUtil;
import com.kaipai.service.ai.config.AiResumeNotificationProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String CALLBACK_PATH = "/internal/ai/resume/notification-receipts/provider";
    private static final String CALLBACK_PRINCIPAL = "ai-notification-provider-callback";

    private final JwtUtil jwtUtil;
    private final AiResumeNotificationProperties aiResumeNotificationProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken callbackAuth = buildProviderCallbackAuthentication(request);
            if (callbackAuth != null) {
                SecurityContextHolder.getContext().setAuthentication(callbackAuth);
            } else {
                String token = extractToken(request);
                if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
                    Claims claims = jwtUtil.parseClaims(token);
                    UsernamePasswordAuthenticationToken auth = buildAuthentication(claims);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken buildAuthentication(Claims claims) {
        String loginType = claims.get("loginType", String.class);
        if ("ADMIN".equalsIgnoreCase(loginType)) {
            Set<String> permissions = extractStringSet(claims.get("permissions", Collection.class));
            Set<String> roleCodes = extractStringSet(claims.get("roleCodes", Collection.class));
            Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            authorities.addAll(permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet()));
            AdminAuthenticatedUser admin = AdminAuthenticatedUser.builder()
                    .adminUserId(Long.valueOf(claims.getSubject()))
                    .account(claims.get("account", String.class))
                    .userName(claims.get("userName", String.class))
                    .roleCodes(roleCodes)
                    .permissions(permissions)
                    .build();
            return new UsernamePasswordAuthenticationToken(admin, null, authorities);
        }

        Long userId = Long.valueOf(claims.getSubject());
        Integer userType = claims.get("userType", Integer.class);
        String role = "ROLE_USER_" + userType;
        return new UsernamePasswordAuthenticationToken(
                userId, null, Collections.singletonList(new SimpleGrantedAuthority(role)));
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private UsernamePasswordAuthenticationToken buildProviderCallbackAuthentication(HttpServletRequest request) {
        if (!isProviderCallbackRequest(request)) {
            return null;
        }
        String headerName = trimToNull(aiResumeNotificationProperties.getCallbackHeader());
        String expectedToken = trimToNull(aiResumeNotificationProperties.getCallbackToken());
        String actualToken = headerName == null ? null : trimToNull(request.getHeader(headerName));
        if (expectedToken == null || !expectedToken.equals(actualToken)) {
            return null;
        }
        return new UsernamePasswordAuthenticationToken(
                CALLBACK_PRINCIPAL,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SYSTEM"))
        );
    }

    private boolean isProviderCallbackRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String requestUri = trimToNull(request.getRequestURI());
        if (requestUri == null) {
            return false;
        }
        return CALLBACK_PATH.equals(requestUri)
                || requestUri.endsWith(CALLBACK_PATH)
                || requestUri.endsWith(CALLBACK_PATH + "/");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Set<String> extractStringSet(Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return values.stream()
                .map(String::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
