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

/**
 * JWTFilter — Filter HTTP yang berjalan SEKALI per request ({@link OncePerRequestFilter}).
 *
 * <p>Bertanggung jawab atas validasi stateless Access Token JWT di setiap HTTP
 * request yang masuk ke Identity Service. Proses yang dilakukan secara berurutan:
 * <ol>
 *   <li>Mengekstrak nilai header {@code Authorization} dengan skema {@code Bearer }.</li>
 *   <li>Memvalidasi token via {@link JWTUtil#isTokenValid(String)}.</li>
 *   <li>Mengekstrak {@code userId}, {@code role}, dan {@code permissions} dari klaim JWT.</li>
 *   <li>Membangun objek {@link UsernamePasswordAuthenticationToken} sebagai representasi
 *       principal yang sudah terautentikasi.</li>
 *   <li>Menyuntikkan objek autentikasi ke {@link SecurityContextHolder} sehingga
 *       Spring Security mengenali request ini sebagai request yang sudah ter-autentikasi,
 *       dan anotasi {@code @PreAuthorize} di Controller dapat dievaluasi.</li>
 * </ol>
 *
 * <p>Filter ini TIDAK melempar exception jika token tidak ada atau tidak valid.
 * Ia hanya melanjutkan chain tanpa menyuntikkan autentikasi, sehingga endpoint
 * yang dilindungi akan mengembalikan HTTP 401 Unauthorized secara natural dari
 * lapisan {@code SecurityFilterChain}.
 *
 * @see JWTUtil
 * @see com.priestess.identity.config.SecurityConfig
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    // =========================================================================
    // KONSTANTA
    // =========================================================================

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    // =========================================================================
    // DEPENDENCY
    // =========================================================================

    private final JWTUtil jwtUtil;

    // =========================================================================
    // FILTER LOGIC
    // =========================================================================

    /**
     * Inti dari logika filter. Dipanggil sekali per HTTP request.
     *
     * @param request     objek HTTP request yang masuk
     * @param response    objek HTTP response yang akan dikirim
     * @param filterChain rantai filter berikutnya; wajib dipanggil agar request diteruskan
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        log.debug("[JWTFilter] Memproses request: {} {}", request.getMethod(), requestURI);

        // --- Langkah 1: Ekstrak token dari header Authorization ---
        String token = extractBearerToken(request);

        if (token == null) {
            log.debug("[JWTFilter] Tidak ada Bearer token di header. Melanjutkan sebagai anonymous.");
            filterChain.doFilter(request, response);
            return;
        }

        // --- Langkah 2: Validasi token via JWTUtil ---
        if (!jwtUtil.isTokenValid(token)) {
            log.warn("[JWTFilter] Token tidak valid atau kadaluarsa pada request: {}", requestURI);
            // Tidak set SecurityContext → Spring Security akan menolak request ini
            filterChain.doFilter(request, response);
            return;
        }

        // --- Langkah 3: Ekstrak klaim dari token yang sudah tervalidasi ---
        String userId      = jwtUtil.extractUserId(token);
        String username    = jwtUtil.extractUsername(token);
        String role        = jwtUtil.extractRole(token);
        String permissions = jwtUtil.extractPermissions(token);

        log.debug("[JWTFilter] Token valid untuk userId={}, username={}, role={}", userId, username, role);

        // --- Langkah 4: Bangun daftar GrantedAuthority dari role dan permissions ---
        // Role diubah menjadi GrantedAuthority standar Spring Security.
        // Setiap nama menu dalam 'permissions' juga didaftarkan sebagai authority
        // agar CustomPermissionEvaluator dapat mengevaluasi @PreAuthorize("hasPermission(...)").
        List<SimpleGrantedAuthority> authorities = buildAuthorities(role, permissions);

        // --- Langkah 5: Buat objek autentikasi dan suntikkan ke SecurityContextHolder ---
        // Principal diisi dengan userId (UUID string) agar bisa diakses dari Controller
        // via SecurityContextHolder.getContext().getAuthentication().getName().
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);

        // Simpan detail tambahan (username) ke dalam Details agar mudah diakses Service
        authToken.setDetails(username);

        SecurityContextHolder.getContext().setAuthentication(authToken);
        log.debug("[JWTFilter] SecurityContext berhasil di-set untuk userId={}", userId);

        // --- Langkah 6: Lanjutkan ke filter/handler berikutnya ---
        filterChain.doFilter(request, response);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Mengekstrak raw JWT string dari header {@code Authorization}.
     *
     * <p>Hanya menerima format: {@code Authorization: Bearer <token>}.
     * Mengembalikan {@code null} jika header tidak ada, kosong, atau
     * tidak menggunakan skema Bearer.
     *
     * @param request HTTP request yang masuk
     * @return string JWT bersih (tanpa prefix "Bearer "), atau {@code null}
     */
    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * Membangun daftar {@link SimpleGrantedAuthority} dari role dan string permissions.
     *
     * <p>Struktur authority yang dihasilkan:
     * <ul>
     *   <li>Satu authority dari role, contoh: {@code ROLE_MERCHANT}</li>
     *   <li>Satu authority per menu dalam permissions, contoh: {@code GENERATE_QRIS_DYNAMIC}</li>
     * </ul>
     *
     * <p>Daftar authority ini dikonsumsi oleh {@code CustomPermissionEvaluator}
     * saat mengevaluasi {@code @PreAuthorize("hasPermission(null, 'GENERATE_QRIS_DYNAMIC')")}.
     *
     * @param role        nama role pengguna, contoh: {@code "ROLE_ADMIN"}
     * @param permissions string permissions dipisah koma, contoh: {@code "SCAN_QRIS,TOP_UP"}
     * @return daftar authority yang sudah terkumpul (immutable)
     */
    private List<SimpleGrantedAuthority> buildAuthorities(String role, String permissions) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        // Selalu tambahkan role sebagai authority
        authorities.add(new SimpleGrantedAuthority(role));

        // Parse dan tambahkan setiap permission sebagai authority individual
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
