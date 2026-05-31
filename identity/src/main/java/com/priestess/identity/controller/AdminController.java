package com.priestess.identity.controller;

import com.priestess.identity.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * AdminController — Endpoint manajemen sistem yang hanya dapat diakses Admin.
 *
 * <p>Semua endpoint di sini dilindungi ganda:
 * <ol>
 *   <li>JWT valid dari Gateway (header {@code X-User-Role} sudah diisi).</li>
 *   <li>Anotasi {@code @PreAuthorize} dievaluasi oleh {@code CustomPermissionEvaluator}.</li>
 * </ol>
 *
 * <p>Base URL: {@code /api/admin}
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // =========================================================================
    // PATCH /api/admin/users/{userId}/kyc
    // =========================================================================

    /**
     * Verifikasi KYC — Ubah status user dari PENDING menjadi ACTIVE.
     *
     * <p>Hanya user dengan permission {@code VERIFY_KYC} yang dapat mengaksesnya.
     * Response: 200 OK + pesan konfirmasi
     */
    @PreAuthorize("hasPermission(null, 'VERIFY_KYC')")
    @PatchMapping("/users/{userId}/kyc")
    public ResponseEntity<String> verifyKyc(@PathVariable UUID userId) {
        log.info("[AdminController] PATCH /api/admin/users/{}/kyc", userId);
        adminService.verifyKyc(userId);
        return ResponseEntity.ok("KYC berhasil diverifikasi. Status user diubah menjadi ACTIVE.");
    }

    // =========================================================================
    // PATCH /api/admin/users/{userId}/suspend
    // =========================================================================

    /**
     * Pembekuan Akun — Ubah status user menjadi SUSPENDED.
     *
     * <p>User yang dibekukan masih bisa menggunakan Access Token yang sedang
     * berjalan hingga maksimal 15 menit. Begitu token habis dan klien memanggil
     * {@code /api/auth/refresh}, Identity Service akan menolak penerbitan
     * token baru, sehingga user ter-logout paksa (SECTION 6 blueprint).
     *
     * Response: 200 OK + pesan konfirmasi
     */
    @PreAuthorize("hasPermission(null, 'SUSPEND_USER')")
    @PatchMapping("/users/{userId}/suspend")
    public ResponseEntity<String> suspendUser(@PathVariable UUID userId) {
        log.info("[AdminController] PATCH /api/admin/users/{}/suspend", userId);
        adminService.suspendUser(userId);
        return ResponseEntity.ok("Akun berhasil dibekukan. User akan ter-logout paksa dalam maksimal 15 menit.");
    }

    // =========================================================================
    // PATCH /api/admin/merchants/{merchantId}/verify
    // =========================================================================

    /**
     * Verifikasi Merchant — Set flag {@code is_verified = true} pada tabel merchants.
     *
     * Response: 200 OK + pesan konfirmasi
     */
    @PreAuthorize("hasPermission(null, 'VERIFY_KYC')")
    @PatchMapping("/merchants/{merchantId}/verify")
    public ResponseEntity<String> verifyMerchant(@PathVariable UUID merchantId) {
        log.info("[AdminController] PATCH /api/admin/merchants/{}/verify", merchantId);
        adminService.verifyMerchant(merchantId);
        return ResponseEntity.ok("Merchant berhasil diverifikasi.");
    }
}
