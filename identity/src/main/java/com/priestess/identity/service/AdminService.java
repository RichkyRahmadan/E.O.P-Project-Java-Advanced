package com.priestess.identity.service;

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
}
