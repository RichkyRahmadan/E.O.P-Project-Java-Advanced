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

/**
 * JWTUtil — Kelas utilitas sentral untuk seluruh siklus hidup token E.O.P.
 *
 * <p>Mengelola pembuatan, validasi, dan ekstraksi klaim dari dua jenis token
 * sesuai Dual-Token Flow yang tertera pada SECTION 6 blueprint E.O.P:
 * <ul>
 *   <li><b>Access Token (JWT)</b> — Berumur 15 menit, bersifat stateless.
 *       Membawa klaim {@code sub} (User UUID), {@code username}, {@code role},
 *       dan {@code permissions} (string menu dipisah koma).</li>
 *   <li><b>Refresh Token</b> — Berumur 7 hari (masa berlaku dikontrol di DB).
 *       Berupa string acak kriptografis yang HANYA disimpan di tabel
 *       {@code users.refresh_token} PostgreSQL, BUKAN di JWT.</li>
 * </ul>
 *
 * <p>Kunci kriptografi diambil dari properti {@code jwt.secret} di
 * {@code application.properties}. Panjang minimum secret key adalah 256-bit
 * (32 karakter) untuk memenuhi standar algoritma HMAC-SHA256.
 *
 * @see JWTFilter
 */
@Slf4j
@Component
public class JWTUtil {

    // =========================================================================
    // KLAIM KUSTOM — Nama klaim yang digunakan di dalam JWT payload
    // =========================================================================
    public static final String CLAIM_USERNAME    = "username";
    public static final String CLAIM_EMAIL       = "email";
    public static final String CLAIM_ROLE        = "role";
    public static final String CLAIM_STATUS      = "status";
    public static final String CLAIM_PERMISSIONS = "permissions";

    // =========================================================================
    // KONFIGURASI DURASI TOKEN
    // =========================================================================

    /** Durasi Access Token dalam milidetik: 15 menit. */
    private static final long ACCESS_TOKEN_EXPIRY_MS = 15L * 60 * 1000;

    /**
     * Panjang byte Refresh Token acak sebelum di-encode Base64URL.
     * 48 byte → 64 karakter Base64URL (aman untuk VARCHAR(500)).
     */
    private static final int REFRESH_TOKEN_BYTE_LENGTH = 48;

    // =========================================================================
    // DEPENDENCY
    // =========================================================================

    /**
     * Secret key yang di-inject dari {@code application.properties}.
     * Contoh konfigurasi:
     * <pre>
     *   jwt.secret=Priestess_EOP_Secret_Key_Minimum_32_Chars_Here!
     * </pre>
     */
    @Value("${jwt.secret}")
    private String secretKeyString;

    /** Generator angka acak kriptografis. Aman untuk digunakan di lingkungan multi-thread. */
    private final SecureRandom secureRandom = new SecureRandom();

    // =========================================================================
    // INTERNAL HELPER — Konversi secret string menjadi SecretKey HMAC-SHA256
    // =========================================================================

    /**
     * Membangun {@link SecretKey} dari {@code jwt.secret} string.
     * Dipanggil setiap kali dibutuhkan — overhead minimal karena operasinya ringan.
     *
     * @return instance {@link SecretKey} yang siap digunakan JJWT 0.12.x
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // =========================================================================
    // GENERATE TOKEN
    // =========================================================================

    /**
     * Membuat Access Token JWT untuk pengguna yang berhasil diautentikasi.
     *
     * <p>Payload klaim yang disematkan:
     * <ul>
     *   <li>{@code sub}         — UUID pengguna (sebagai string).</li>
     *   <li>{@code username}    — Nama pengguna untuk referensi UI.</li>
     *   <li>{@code email}       — Alamat email pengguna.</li>
     *   <li>{@code role}        — Nama role, contoh: {@code ROLE_MERCHANT}.</li>
     *   <li>{@code status}      — Status user, contoh: {@code PENDING}.</li>
     *   <li>{@code permissions} — String menu dipisah koma, contoh:
     *                             {@code "SCAN_QRIS,GENERATE_QRIS_DYNAMIC"}.
     *                             Digunakan oleh Gateway dan {@code CustomPermissionEvaluator}.</li>
     *   <li>{@code iat}         — Waktu penerbitan token (issued at).</li>
     *   <li>{@code exp}         — Waktu kadaluarsa token (expiration): 15 menit dari {@code iat}.</li>
     * </ul>
     *
     * @param userId      UUID unik pengguna; menjadi klaim {@code sub}
     * @param username    nama pengguna
     * @param email       alamat email pengguna
     * @param roleName    nama role pengguna (contoh: {@code ROLE_USER})
     * @param status      status akun pengguna
     * @param permissions daftar nama menu yang boleh diakses oleh role ini
     * @return string JWT yang telah ditandatangani (format: header.payload.signature)
     */
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

    /**
     * Membuat Refresh Token berupa string acak kriptografis yang aman.
     *
     * <p>Token ini BUKAN JWT — berupa string acak 48-byte yang di-encode Base64URL
     * sehingga aman disimpan di kolom {@code VARCHAR(500)} dan ditransmisikan via HTTP.
     * Masa berlaku 7 hari dikontrol secara implisit: setiap kali klien memanggil
     * {@code /api/auth/refresh}, Identity Service memverifikasi token ini di database.
     *
     * @return string Refresh Token Base64URL-encoded, sepanjang ±64 karakter
     */
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        log.debug("[JWTUtil] Refresh Token baru digenerate (panjang: {} karakter)", token.length());
        return token;
    }

    // =========================================================================
    // VALIDASI TOKEN
    // =========================================================================

    /**
     * Memvalidasi integritas dan masa berlaku sebuah JWT Access Token.
     *
     * <p>Validasi yang dilakukan:
     * <ol>
     *   <li>Verifikasi tanda tangan kriptografi (signature) menggunakan secret key.</li>
     *   <li>Pemeriksaan masa berlaku (expiration claim {@code exp}).</li>
     *   <li>Pemeriksaan format token yang valid.</li>
     * </ol>
     *
     * @param token string JWT yang akan divalidasi
     * @return {@code true} jika token valid dan belum kadaluarsa;
     *         {@code false} untuk semua kondisi kegagalan lainnya
     */
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

    // =========================================================================
    // EKSTRAKSI KLAIM
    // =========================================================================

    /**
     * Mengekstrak User ID (klaim {@code sub}) dari token.
     *
     * @param token JWT Access Token yang valid
     * @return UUID pengguna sebagai string
     */
    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Mengekstrak username (klaim {@code username}) dari token.
     *
     * @param token JWT Access Token yang valid
     * @return nama pengguna
     */
    public String extractUsername(String token) {
        return parseClaims(token).get(CLAIM_USERNAME, String.class);
    }

    /**
     * Mengekstrak email (klaim {@code email}) dari token.
     *
     * @param token JWT Access Token yang valid
     * @return email pengguna
     */
    public String extractEmail(String token) {
        return parseClaims(token).get(CLAIM_EMAIL, String.class);
    }

    /**
     * Mengekstrak nama role (klaim {@code role}) dari token.
     *
     * @param token JWT Access Token yang valid
     * @return nama role, contoh: {@code "ROLE_ADMIN"}
     */
    public String extractRole(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    /**
     * Mengekstrak string permissions (klaim {@code permissions}) dari token.
     *
     * <p>String ini dipisahkan oleh koma. Gunakan {@code String.split(",")} untuk
     * mendapatkan array nama menu individual jika diperlukan.
     *
     * @param token JWT Access Token yang valid
     * @return string permissions dipisah koma, contoh: {@code "SCAN_QRIS,TOP_UP"}
     *         atau string kosong ({@code ""}) jika tidak ada permission.
     */
    public String extractPermissions(String token) {
        String perms = parseClaims(token).get(CLAIM_PERMISSIONS, String.class);
        return (perms != null) ? perms : "";
    }

    // =========================================================================
    // INTERNAL PARSER
    // =========================================================================

    /**
     * Me-parse JWT dan mengembalikan seluruh klaim payload-nya.
     *
     * <p>Method internal ini secara otomatis memverifikasi tanda tangan menggunakan
     * secret key. Jika token tidak valid atau kadaluarsa, JJWT akan melempar
     * {@link JwtException} (atau subkelas-nya) yang harus ditangkap oleh pemanggil.
     *
     * @param token string JWT yang akan di-parse
     * @return objek {@link Claims} berisi semua klaim dalam payload token
     * @throws JwtException jika tanda tangan tidak cocok, token malformed, atau kadaluarsa
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
