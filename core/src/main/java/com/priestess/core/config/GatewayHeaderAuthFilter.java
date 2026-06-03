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

/**
 * GatewayHeaderAuthFilter — Filter keamanan yang mengisi SecurityContext
 * dari header yang disuntikkan oleh E.O.P Gateway.
 *
 * <h2>Mekanisme Kerja</h2>
 * <p>Filter ini dieksekusi satu kali per request (extends {@link OncePerRequestFilter}).
 * Alurnya:
 * <ol>
 *   <li>Baca header {@code X-User-Id} dari request yang masuk.</li>
 *   <li>Jika header ada dan tidak kosong, buat objek
 *       {@link UsernamePasswordAuthenticationToken} dengan authorities
 *       dari header {@code X-User-Role} dan {@code X-User-Permissions}.</li>
 *   <li>Set objek autentikasi ke {@link SecurityContextHolder} agar
 *       Spring Security menganggap request ini sudah terotentikasi.</li>
 *   <li>Jika header tidak ada, biarkan SecurityContext kosong — Spring Security
 *       akan menolak request dengan 401 sesuai konfigurasi di {@link SecurityConfig}.</li>
 * </ol>
 *
 * <h2>Header yang Dibaca</h2>
 * <ul>
 *   <li>{@code X-User-Id}          — UUID pengguna (principal)</li>
 *   <li>{@code X-User-Role}        — Role tunggal, contoh: {@code ROLE_USER}</li>
 *   <li>{@code X-User-Permissions} — Permission dipisah koma, contoh:
 *       {@code WALLET_VIEW,TRANSFER,QRIS_PAY}</li>
 * </ul>
 */
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

        // Jika tidak ada X-User-Id, skip — SecurityConfig akan handle 401
        if (!StringUtils.hasText(userId)) {
            log.debug("[GatewayFilter] Request masuk tanpa X-User-Id — {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // Hanya isi SecurityContext jika belum ada autentikasi sebelumnya
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

    /**
     * Membangun daftar GrantedAuthority dari header role dan permissions.
     *
     * <p>Contoh hasil untuk request dengan:
     * <ul>
     *   <li>{@code X-User-Role: ROLE_USER}</li>
     *   <li>{@code X-User-Permissions: WALLET_VIEW,TRANSFER,QRIS_PAY}</li>
     * </ul>
     * akan menghasilkan: {@code [ROLE_USER, WALLET_VIEW, TRANSFER, QRIS_PAY]}.
     *
     * @param role        nilai header X-User-Role
     * @param permissions nilai header X-User-Permissions (dipisah koma)
     * @return daftar {@link SimpleGrantedAuthority}
     */
    private List<SimpleGrantedAuthority> buildAuthorities(String role, String permissions) {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();

        // Tambahkan role sebagai authority
        if (StringUtils.hasText(role)) {
            authorities.add(new SimpleGrantedAuthority(role));
        }

        // Tambahkan setiap permission sebagai authority terpisah
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
