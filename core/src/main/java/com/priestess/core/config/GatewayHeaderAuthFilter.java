package com.priestess.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID      = "X-User-Id";
    private static final String HEADER_USER_ROLE    = "X-User-Role";
    private static final String HEADER_USER_PERMS   = "X-User-Permissions";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(HEADER_USER_ID);

        if (!StringUtils.hasText(userId)) {
            log.debug("[GatewayFilter] Request masuk tanpa X-User-Id — {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            List<SimpleGrantedAuthority> authorities = buildAuthorities(
                    request.getHeader(HEADER_USER_ROLE),
                    request.getHeader(HEADER_USER_PERMS)
            );

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authToken);

            log.debug("[GatewayFilter] SecurityContext diisi — userId={}, authorities={}",
                    userId, authorities);
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> buildAuthorities(String role, String permissions) {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();

        if (StringUtils.hasText(role)) {
            authorities.add(new SimpleGrantedAuthority(role));
        }

        if (StringUtils.hasText(permissions)) {
            List<SimpleGrantedAuthority> permAuthorities = Arrays.stream(permissions.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            authorities.addAll(permAuthorities);
        }

        return Collections.unmodifiableList(authorities);
    }
}
