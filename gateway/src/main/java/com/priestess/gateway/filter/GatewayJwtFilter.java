package com.priestess.gateway.filter;

import com.priestess.gateway.filter.SuspendedUserCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class GatewayJwtFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private static final String AUTHORIZATION_HEADER    = "Authorization";
    private static final String BEARER_PREFIX           = "Bearer ";
    private static final String HEADER_USER_ID          = "X-User-Id";
    private static final String HEADER_USER_NAME        = "X-User-Name";
    private static final String HEADER_USER_EMAIL       = "X-User-Email";
    private static final String HEADER_USER_ROLE        = "X-User-Role";
    private static final String HEADER_USER_STATUS      = "X-User-Status";
    private static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";

    private static final String CLAIM_USERNAME    = "username";
    private static final String CLAIM_EMAIL       = "email";
    private static final String CLAIM_ROLE        = "role";
    private static final String CLAIM_STATUS      = "status";
    private static final String CLAIM_PERMISSIONS = "permissions";

    @Value("${jwt.secret}")
    private String secretKeyString;

    @Autowired
    private SuspendedUserCache suspendedUserCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next)
            throws Exception {

        String requestPath = request.path();
        log.debug("[GatewayJwtFilter] Memfilter request: {} {}", request.method(), requestPath);

        String authHeader = request.headers().firstHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[GatewayJwtFilter] DITOLAK 401 — Tidak ada Bearer token pada path: {}",
                    requestPath);
            return buildUnauthorizedResponse("Akses ditolak: Token autentikasi tidak ditemukan.");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = parseAndValidateToken(token);
            log.debug("[GatewayJwtFilter] Token secara kriptografis valid untuk sub={}", claims.getSubject());

        } catch (ExpiredJwtException e) {
            log.warn("[GatewayJwtFilter] DITOLAK 401 — Token kadaluarsa pada path: {}", requestPath);
            return buildUnauthorizedResponse("Akses ditolak: Sesi Anda telah habis. Silakan login kembali.");

        } catch (JwtException e) {
            log.warn("[GatewayJwtFilter] DITOLAK 401 — Token tidak valid pada path: {} | Alasan: {}",
                    requestPath, e.getMessage());
            return buildUnauthorizedResponse("Akses ditolak: Token tidak valid atau telah dimanipulasi.");
        }

        String userId = claims.getSubject();

        String sessionKey = "session:" + token;
        String sessionRaw = null;
        try {
            sessionRaw = redisTemplate.opsForValue().get(sessionKey);
        } catch (Exception e) {
            log.error("[GatewayJwtFilter] Gagal menghubungi Redis untuk validasi session: {}", e.getMessage());
            return buildUnauthorizedResponse("Akses ditolak: Gagal melakukan validasi sesi.");
        }

        if (sessionRaw == null) {
            log.warn("[GatewayJwtFilter] DITOLAK 401 — Session tidak ditemukan atau expired di Redis. Path: {}", requestPath);
            return buildUnauthorizedResponse("Akses ditolak: Sesi Anda telah habis atau tidak aktif. Silakan login kembali.");
        }

        String username;
        String email;
        String role;
        String status;
        String permissions;

        try {
            String[] parts = sessionRaw.split(":::", -1);
            if (parts.length >= 6) {
                username = parts[1];
                email = parts[2];
                role = parts[3];
                status = parts[4];
                permissions = parts[5];
            } else {
                log.warn("[GatewayJwtFilter] Format sesi di Redis tidak valid: {}", sessionRaw);
                return buildUnauthorizedResponse("Akses ditolak: Struktur sesi tidak valid.");
            }
        } catch (Exception e) {
            log.error("[GatewayJwtFilter] Gagal memparsing data sesi dari Redis: {}", e.getMessage());
            return buildUnauthorizedResponse("Akses ditolak: Struktur sesi tidak valid.");
        }

        if (suspendedUserCache.isSuspended(userId) || "SUSPENDED".equalsIgnoreCase(status)) {
            log.warn("[GatewayJwtFilter] DITOLAK 403 — User {} dalam status SUSPENDED. Path: {}",
                    userId, requestPath);
            return buildForbiddenResponse("Akses ditolak: Akun Anda telah dibekukan oleh Administrator.");
        }

        ServerRequest mutatedRequest = ServerRequest.from(request)
                .header(HEADER_USER_ID,          userId)
                .header(HEADER_USER_NAME,        username != null ? username : "")
                .header(HEADER_USER_EMAIL,       email != null ? email : "")
                .header(HEADER_USER_ROLE,         role != null ? role : "")
                .header(HEADER_USER_STATUS,       status != null ? status : "")
                .header(HEADER_USER_PERMISSIONS,  permissions != null ? permissions : "")
                .build();

        log.debug("[GatewayJwtFilter] Header disuntikkan — X-User-Id={}, X-User-Name={}, X-User-Email={}, X-User-Role={}, X-User-Status={}",
                userId, username, email, role, status);

        return next.handle(mutatedRequest);
    }

    private Claims parseAndValidateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

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

    private ServerResponse buildForbiddenResponse(String message) {
        String jsonBody = String.format(
                "{\"status\":403,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message,
                java.time.LocalDateTime.now()
        );

        return ServerResponse
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody);
    }
}
