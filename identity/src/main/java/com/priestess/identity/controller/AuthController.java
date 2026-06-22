package com.priestess.identity.controller;

import com.priestess.identity.dto.AuthResponse;
import com.priestess.identity.dto.LoginRequest;
import com.priestess.identity.dto.RefreshTokenRequest;
import com.priestess.identity.dto.RegisterMerchantRequest;
import com.priestess.identity.dto.RegisterUserRequest;
import com.priestess.identity.dto.RegisterResponse;
import com.priestess.identity.service.AuthService;
import com.priestess.identity.dto.UserResolutionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController — Seluruh endpoint publik autentikasi E.O.P ({@code /api/auth}).
 *
 * <p>Semua endpoint di sini dikonfigurasi sebagai {@code permitAll()} di
 * {@code SecurityConfig} — dapat diakses tanpa JWT. Zero business logic;
 * semua didelegasikan ke {@link AuthService}.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // =========================================================================
    // POST /api/auth/login
    // =========================================================================

    /**
     * Login — Verifikasi credential dan terbitkan Dual Token.
     * Response: 200 OK + AuthResponse
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("[AuthController] POST /api/auth/login — username: {}", request.getUsername());
        return ResponseEntity.ok(authService.login(request));
    }

    // =========================================================================
    // POST /api/auth/refresh
    // =========================================================================

    /**
     * Refresh — Perbarui Access Token menggunakan Refresh Token dari DB.
     * Sekaligus mengimplementasikan mitigasi SUSPENDED dari SECTION 6:
     * jika akun dibekukan, refresh ditolak dan user ter-logout paksa.
     * Response: 200 OK + AuthResponse (token baru)
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("[AuthController] POST /api/auth/refresh");
        return ResponseEntity.ok(authService.refresh(request));
    }

    // =========================================================================
    // POST /api/auth/register
    // =========================================================================

    /**
     * Registrasi User — Daftarkan akun baru dengan role USER, status PENDING.
     * Response: 201 Created + RegisterResponse
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> registerUser(@Valid @RequestBody RegisterUserRequest request) {
        log.info("[AuthController] POST /api/auth/register — username: {}", request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerUser(request));
    }

    // =========================================================================
    // POST /api/auth/register/merchant
    // =========================================================================

    /**
     * Registrasi Merchant — Daftarkan akun bisnis baru dengan role MERCHANT, status PENDING.
     * Response: 201 Created + RegisterResponse
     */
    @PostMapping("/register/merchant")
    public ResponseEntity<RegisterResponse> registerMerchant(
            @Valid @RequestBody RegisterMerchantRequest request) {
        log.info("[AuthController] POST /api/auth/register/merchant — username: {}", request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerMerchant(request));
    }

    // =========================================================================
    // POST /api/auth/logout
    // =========================================================================

    /**
     * Logout — Hapus Refresh Token dari database untuk menonaktifkan sesi.
     * Access Token tetap hidup hingga 15 menit kadaluarsa (stateless — tidak
     * bisa di-invalidate dari server sebelum expired).
     * Response: 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("X-Refresh-Token") String refreshToken) {
        log.info("[AuthController] POST /api/auth/logout");
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // GET /api/auth/resolve
    // =========================================================================

    /**
     * Resolve User — Cari user berdasarkan username atau email.
     * Response: 200 OK + UserResolutionResponse
     */
    @GetMapping("/resolve")
    public ResponseEntity<UserResolutionResponse> resolveUser(@RequestParam("query") String query) {
        log.info("[AuthController] GET /api/auth/resolve — query: {}", query);
        return ResponseEntity.ok(authService.resolveUser(query));
    }
}
