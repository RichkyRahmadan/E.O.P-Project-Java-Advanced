package com.priestess.identity.controller;

import com.priestess.identity.dto.PendingMerchantResponse;
import com.priestess.identity.dto.PendingUserResponse;
import com.priestess.identity.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PreAuthorize("hasPermission(null, 'ADMIN_KYC')")
    @PatchMapping("/users/{userId}/kyc")
    public ResponseEntity<String> verifyKyc(@PathVariable UUID userId) {
        log.info("[AdminController] PATCH /api/admin/users/{}/kyc", userId);
        adminService.verifyKyc(userId);
        return ResponseEntity.ok("KYC berhasil diverifikasi. Status user diubah menjadi ACTIVE.");
    }

    @PreAuthorize("hasPermission(null, 'ADMIN_SUSPEND')")
    @PatchMapping("/users/{userId}/suspend")
    public ResponseEntity<String> suspendUser(@PathVariable UUID userId) {
        log.info("[AdminController] PATCH /api/admin/users/{}/suspend", userId);
        adminService.suspendUser(userId);
        return ResponseEntity.ok("Akun berhasil dibekukan. User akan ter-logout paksa dalam maksimal 15 menit.");
    }

    @PreAuthorize("hasPermission(null, 'ADMIN_MERCHANT_VERIFY')")
    @PatchMapping("/merchants/{merchantId}/verify")
    public ResponseEntity<String> verifyMerchant(@PathVariable UUID merchantId) {
        log.info("[AdminController] PATCH /api/admin/merchants/{}/verify", merchantId);
        adminService.verifyMerchant(merchantId);
        return ResponseEntity.ok("Merchant berhasil diverifikasi.");
    }

    @PreAuthorize("hasPermission(null, 'ADMIN_KYC')")
    @GetMapping("/users/pending")
    public ResponseEntity<List<PendingUserResponse>> getPendingUsers() {
        log.info("[AdminController] GET /api/admin/users/pending");
        return ResponseEntity.ok(adminService.getPendingUsers());
    }

    @PreAuthorize("hasPermission(null, 'ADMIN_MERCHANT_VERIFY')")
    @GetMapping("/merchants/pending")
    public ResponseEntity<List<PendingMerchantResponse>> getPendingMerchants() {
        log.info("[AdminController] GET /api/admin/merchants/pending");
        return ResponseEntity.ok(adminService.getPendingMerchants());
    }
}
