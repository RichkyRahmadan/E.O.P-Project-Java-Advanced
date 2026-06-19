package com.priestess.identity.service;

import com.priestess.identity.dto.PendingMerchantResponse;
import com.priestess.identity.dto.PendingUserResponse;

import java.util.List;
import java.util.UUID;

/**
 * AdminService — Kontrak operasi manajemen sistem oleh Admin.
 */
public interface AdminService {

    /** Ubah status user dari PENDING menjadi ACTIVE (verifikasi KYC). */
    void verifyKyc(UUID userId);

    /** Bekukan akun user — ubah status menjadi SUSPENDED. */
    void suspendUser(UUID userId);

    /** Set is_verified = true pada entitas Merchant. */
    void verifyMerchant(UUID merchantId);

    /** Mengambil daftar user ber-role USER yang masih berstatus PENDING. */
    List<PendingUserResponse> getPendingUsers();

    /** Mengambil daftar merchant yang belum diverifikasi (is_verified = false). */
    List<PendingMerchantResponse> getPendingMerchants();
}
