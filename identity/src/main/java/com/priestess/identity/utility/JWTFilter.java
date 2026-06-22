package com.priestess.identity.utility;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    private final JWTUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        log.debug("[JWTFilter] Memproses request: {} {}", request.getMethod(), requestURI);

        String token = extractBearerToken(request);

        if (token == null) {
            log.debug("[JWTFilter] Tidak ada Bearer token di header. Melanjutkan sebagai anonymous.");
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtUtil.isTokenValid(token)) {
            log.warn("[JWTFilter] Token tidak valid atau kadaluarsa pada request: {}", requestURI);

            filterChain.doFilter(request, response);
            return;
        }

        String userId      = jwtUtil.extractUserId(token);
        String username    = jwtUtil.extractUsername(token);
        String role        = jwtUtil.extractRole(token);
        String permissions = jwtUtil.extractPermissions(token);

        log.debug("[JWTFilter] Token valid untuk userId={}, username={}, role={}", userId, username, role);

        List<SimpleGrantedAuthority> authorities = buildAuthorities(role, permissions);

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);

        authToken.setDetails(username);

        SecurityContextHolder.getContext().setAuthentication(authToken);
        log.debug("[JWTFilter] SecurityContext berhasil di-set untuk userId={}", userId);

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private List<SimpleGrantedAuthority> buildAuthorities(String role, String permissions) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        authorities.add(new SimpleGrantedAuthority(role));

        if (StringUtils.hasText(permissions)) {
            List<SimpleGrantedAuthority> permissionAuthorities = Arrays
                    .stream(permissions.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            authorities.addAll(permissionAuthorities);
        }

        return Collections.unmodifiableList(authorities);
    }
}
