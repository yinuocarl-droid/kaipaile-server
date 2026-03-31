package com.kaipai.common.filter;

import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.common.util.JwtUtil;
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

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            Claims claims = jwtUtil.parseClaims(token);
            UsernamePasswordAuthenticationToken auth = buildAuthentication(claims);
            SecurityContextHolder.getContext().setAuthentication(auth);
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

    private Set<String> extractStringSet(Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return values.stream()
                .map(String::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
