package com.priestess.oracle.config;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GatewayHeaderAuthFilter — Membaca header yang disuntikkan Gateway
 * dan mengisi SecurityContext agar request dianggap terotentikasi.
 *
 * <p>Header yang dibaca:
 * <ul>
 *   <li>{@code X-User-Id}          — UUID pengguna (principal)</li>
 *   <li>{@code X-User-Role}        — Role, contoh: {@code ROLE_USER}</li>
 *   <li>{@code X-User-Permissions} — Permissions dipisah koma</li>
 * </ul>
 */
@Slf4j
@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");

        if (!StringUtils.hasText(userId)) {
            log.debug("[OracleFilter] Request tanpa X-User-Id — {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();

            String role = request.getHeader("X-User-Role");
            if (StringUtils.hasText(role)) {
                authorities.add(new SimpleGrantedAuthority(role));
            }

            String perms = request.getHeader("X-User-Permissions");
            if (StringUtils.hasText(perms)) {
                Arrays.stream(perms.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("[OracleFilter] SecurityContext diisi — userId={}", userId);
        }

        filterChain.doFilter(request, response);
    }
}
