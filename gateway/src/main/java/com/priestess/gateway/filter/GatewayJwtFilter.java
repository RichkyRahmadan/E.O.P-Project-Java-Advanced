package com.priestess.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * GatewayJwtFilter — Filter keamanan stateless untuk E.O.P Gateway (WebMVC).
 *
 * <p>Mengimplementasikan {@link HandlerFilterFunction} dari Spring MVC Gateway
 * (bukan Reactive WebFlux). Filter ini diregistrasikan sebagai
 * {@link org.springframework.cloud.gateway.server.mvc.filter.GatewayFilterFunctions}
 * kustom dan dapat dipasang pada route tertentu melalui konfigurasi YAML.
 *
 * <h2>Alur Kerja Filter (5 Langkah)</h2>
 * <ol>
 *   <li><b>Periksa Header Authorization</b> — Jika tidak ada atau tidak diawali
 *       {@code Bearer }, tolak request dengan HTTP 401 langsung.</li>
 *   <li><b>Validasi JWT Signature</b> — Bongkar dan verifikasi token menggunakan
 *       secret key yang sama dengan Identity Service (HMAC-SHA256). Jika kadaluarsa
 *       atau tanda tangan tidak cocok, tolak dengan HTTP 401.</li>
 *   <li><b>Ekstrak Klaim</b> — Ambil {@code sub} (User ID), {@code role}, dan
 *       {@code permissions} dari payload JWT yang sudah terverifikasi.</li>
 *   <li><b>Header Mutation</b> — Suntikkan klaim tersebut sebagai header internal:
 *       {@code X-User-Id}, {@code X-User-Role}, {@code X-User-Permissions}.</li>
 *   <li><b>Teruskan Request</b> — Lanjutkan ke handler/service tujuan.</li>
 * </ol>
 *
 * <h2>Stateless — Tanpa Database / Redis</h2>
 * <p>Filter ini tidak mengakses database apapun. Validitas token ditentukan
 * murni dari verifikasi kriptografis signature JWT menggunakan secret key bersama.
 * Ini memungkinkan Gateway berjalan tanpa dependensi ke Identity Service
 * (kecuali untuk endpoint {@code /api/auth/**} yang di-bypass oleh routing YAML).
 *
 * @see com.priestess.gateway.config.GatewayFilterConfig
 */
@Slf4j
@Component
public class GatewayJwtFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    // =========================================================================
    // KONSTANTA
    // =========================================================================

    private static final String AUTHORIZATION_HEADER  = "Authorization";
    private static final String BEARER_PREFIX         = "Bearer ";
    private static final String HEADER_USER_ID        = "X-User-Id";
    private static final String HEADER_USER_ROLE      = "X-User-Role";
    private static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";

    /** Nama klaim JWT — harus identik dengan yang digunakan di JWTUtil Identity Service. */
    private static final String CLAIM_ROLE        = "role";
    private static final String CLAIM_PERMISSIONS = "permissions";

    // =========================================================================
    // DEPENDENCY
    // =========================================================================

    /**
     * Secret key yang sama persis dengan Identity Service.
     * Diambil dari properti {@code jwt.secret} di {@code application.yml}.
     * Panjang minimum 32 karakter (256-bit) untuk HMAC-SHA256.
     */
    @Value("${jwt.secret}")
    private String secretKeyString;

    // =========================================================================
    // FILTER LOGIC
    // =========================================================================

    /**
     * Inti logika filter — dieksekusi untuk setiap request pada route yang terdaftar.
     *
     * @param request objek request dari klien
     * @param next    handler berikutnya dalam rantai; panggil untuk meneruskan request
     * @return {@link ServerResponse} — baik error 401 jika validasi gagal,
     *         atau response dari service tujuan jika berhasil
     * @throws Exception jika terjadi error tidak terduga di dalam filter chain
     */
    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
            throws Exception {

        String requestPath = request.path();
        log.debug("[GatewayJwtFilter] Memfilter request: {} {}", request.method(), requestPath);

        // =====================================================================
        // LANGKAH 1 — Periksa keberadaan dan format Bearer Token
        // =====================================================================
        String authHeader = request.headers().firstHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[GatewayJwtFilter] DITOLAK 401 — Tidak ada Bearer token pada path: {}",
                    requestPath);
            return buildUnauthorizedResponse("Akses ditolak: Token autentikasi tidak ditemukan.");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // =====================================================================
        // LANGKAH 2 & 3 — Validasi Signature JWT dan Ekstrak Klaim
        // =====================================================================
        Claims claims;
        try {
            claims = parseAndValidateToken(token);
            log.debug("[GatewayJwtFilter] Token valid untuk sub={}", claims.getSubject());

        } catch (ExpiredJwtException e) {
            log.warn("[GatewayJwtFilter] DITOLAK 401 — Token kadaluarsa pada path: {}", requestPath);
            return buildUnauthorizedResponse("Akses ditolak: Sesi Anda telah habis. Silakan login kembali.");

        } catch (JwtException e) {
            log.warn("[GatewayJwtFilter] DITOLAK 401 — Token tidak valid pada path: {} | Alasan: {}",
                    requestPath, e.getMessage());
            return buildUnauthorizedResponse("Akses ditolak: Token tidak valid atau telah dimanipulasi.");
        }

        // =====================================================================
        // LANGKAH 4 — Header Mutation: Suntikkan klaim ke request internal
        // =====================================================================
        String userId      = claims.getSubject();
        String role        = claims.get(CLAIM_ROLE, String.class);
        String permissions = claims.getOrDefault(CLAIM_PERMISSIONS, "").toString();

        // Mutasi request: tambahkan header baru tanpa mengubah header asli dari klien
        ServerRequest mutatedRequest = ServerRequest.from(request)
                .header(HEADER_USER_ID,          userId)
                .header(HEADER_USER_ROLE,         role)
                .header(HEADER_USER_PERMISSIONS,  permissions)
                .build();

        log.debug("[GatewayJwtFilter] Header disuntikkan — X-User-Id={}, X-User-Role={}", userId, role);

        // =====================================================================
        // LANGKAH 5 — Teruskan request yang telah dimutasi ke service tujuan
        // =====================================================================
        return next.handle(mutatedRequest);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Me-parse dan memverifikasi JWT menggunakan HMAC-SHA256 dengan secret key bersama.
     *
     * @param token raw JWT string (tanpa prefix "Bearer ")
     * @return objek {@link Claims} berisi seluruh payload token jika valid
     * @throws JwtException (atau subkelas-nya) jika token tidak valid atau kadaluarsa
     */
    private Claims parseAndValidateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Membangun respons HTTP 401 Unauthorized langsung dari Gateway
     * tanpa meneruskan request ke service tujuan.
     *
     * <p>Response body berisi JSON sederhana agar Angular Interceptor dapat
     * membaca dan memproses pesan error secara konsisten.
     *
     * @param message pesan error yang aman ditampilkan ke klien
     * @return {@link ServerResponse} dengan status 401 dan body JSON
     */
    private ServerResponse buildUnauthorizedResponse(String message) {
        String jsonBody = String.format(
                "{\"status\":401,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message,
                java.time.LocalDateTime.now()
        );

        return ServerResponse
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody);
    }
}
