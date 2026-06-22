package com.priestess.identity.controller;

import com.priestess.identity.dto.RegisterMerchantByOwnerRequest;
import com.priestess.identity.dto.RegisterResponse;
import com.priestess.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IdentityController — Endpoint identity terproteksi E.O.P ({@code /api/identity}).
 *
 * <p>Berbeda dengan {@code AuthController}, semua endpoint di sini
 * <b>memerlukan JWT yang valid</b> (dikontrol oleh {@code SecurityConfig}).
 * User ID diekstrak dari header {@code X-User-Id} yang disuntikkan oleh Gateway
 * setelah validasi JWT.
 *
 * <p>Zero business logic — semua didelegasikan ke {@link AuthService}.
 */
@Slf4j
@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final AuthService authService;

    // =========================================================================
    // POST /api/identity/merchant/register
    // =========================================================================

    /**
     * Mendaftarkan merchant baru dimana owner adalah user yang sedang login.
     *
     * <p>Endpoint ini hanya bisa diakses oleh user yang sudah login (JWT valid).
     * Owner diidentifikasi otomatis dari header {@code X-User-Id} yang disuntikkan
     * Gateway — tidak perlu input nomor telepon owner di body request.
     *
     * <p>Syarat: User harus memiliki status {@code ACTIVE} (sudah terverifikasi KYC).
     *
     * @param ownerId ID user owner dari header JWT (disuntikkan Gateway)
     * @param request DTO berisi username, password, merchantName, address
     * @return 201 Created dengan data registrasi merchant
     */
    @PostMapping("/merchant/register")
    public ResponseEntity<RegisterResponse> registerMerchant(
            @RequestHeader("X-User-Id") String ownerId,
            @RequestHeader(value = "X-User-Status", required = false) String status,
            @Valid @RequestBody RegisterMerchantByOwnerRequest request) {
        log.info("[IdentityController] POST /api/identity/merchant/register — ownerId={}, status={}", ownerId, status);
        RegisterResponse response = authService.registerMerchantByOwner(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
