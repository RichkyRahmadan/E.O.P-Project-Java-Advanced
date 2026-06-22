package com.priestess.identity.utility;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JWTUtil {

    public static final String CLAIM_USERNAME    = "username";
    public static final String CLAIM_EMAIL       = "email";
    public static final String CLAIM_ROLE        = "role";
    public static final String CLAIM_STATUS      = "status";
    public static final String CLAIM_PERMISSIONS = "permissions";

    private static final long ACCESS_TOKEN_EXPIRY_MS = 15L * 60 * 1000;

    private static final int REFRESH_TOKEN_BYTE_LENGTH = 48;

    @Value("${jwt.secret}")
    private String secretKeyString;

    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID userId,
                                      String username,
                                      String email,
                                      String roleName,
                                      String status,
                                      List<String> permissions) {
        Instant now = Instant.now();
        String permissionString = String.join(",", permissions);

        String token = Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, roleName)
                .claim(CLAIM_STATUS, status)
                .claim(CLAIM_PERMISSIONS, permissionString)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ACCESS_TOKEN_EXPIRY_MS)))
                .signWith(getSigningKey())
                .compact();

        log.debug("[JWTUtil] Access Token diterbitkan untuk userId={}, role={}, status={}, permissions={}",
                userId, roleName, status, permissionString);
        return token;
    }

    public String generateRefreshToken() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        log.debug("[JWTUtil] Refresh Token baru digenerate (panjang: {} karakter)", token.length());
        return token;
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("[JWTUtil] Token kadaluarsa: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("[JWTUtil] Token tidak valid atau signature mismatch: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[JWTUtil] Error tidak terduga saat validasi token: {}", e.getMessage());
        }
        return false;
    }

    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractUsername(String token) {
        return parseClaims(token).get(CLAIM_USERNAME, String.class);
    }

    public String extractEmail(String token) {
        return parseClaims(token).get(CLAIM_EMAIL, String.class);
    }

    public String extractRole(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    public String extractPermissions(String token) {
        String perms = parseClaims(token).get(CLAIM_PERMISSIONS, String.class);
        return (perms != null) ? perms : "";
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
