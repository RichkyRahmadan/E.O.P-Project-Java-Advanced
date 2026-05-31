package com.priestess.identity.service.impl;

import com.priestess.identity.entity.MerchantEntity;
import com.priestess.identity.entity.UserEntity;
import com.priestess.identity.repository.MerchantRepository;
import com.priestess.identity.repository.UserRepository;
import com.priestess.identity.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * AdminServiceImpl — Implementasi operasi manajemen sistem.
 *
 * <p>Menangani verifikasi KYC, pembekuan akun, dan verifikasi merchant.
 * Semua operasi bersifat idempotent — memanggil berulang kali tidak
 * akan menghasilkan efek sampingan yang tidak diinginkan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository     userRepository;
    private final MerchantRepository merchantRepository;

    // =========================================================================
    // VERIFY KYC
    // =========================================================================

    @Override
    @Transactional
    public void verifyKyc(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "User dengan ID " + userId + " tidak ditemukan."));

        if ("ACTIVE".equalsIgnoreCase(user.getStatus())) {
            log.info("[AdminService] verifyKyc: user {} sudah ACTIVE, tidak ada perubahan.", userId);
            return;
        }

        user.setStatus("ACTIVE");
        userRepository.save(user);
        log.info("[AdminService] KYC diverifikasi — userId={}, status diubah ke ACTIVE.", userId);
    }

    // =========================================================================
    // SUSPEND USER
    // =========================================================================

    @Override
    @Transactional
    public void suspendUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "User dengan ID " + userId + " tidak ditemukan."));

        if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            log.info("[AdminService] suspendUser: user {} sudah SUSPENDED, tidak ada perubahan.", userId);
            return;
        }

        user.setStatus("SUSPENDED");
        userRepository.save(user);
        log.info("[AdminService] Akun dibekukan — userId={}, status diubah ke SUSPENDED.", userId);
    }

    // =========================================================================
    // VERIFY MERCHANT
    // =========================================================================

    @Override
    @Transactional
    public void verifyMerchant(UUID merchantId) {
        MerchantEntity merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Merchant dengan ID " + merchantId + " tidak ditemukan."));

        merchant.setIsVerified(true);
        merchantRepository.save(merchant);
        log.info("[AdminService] Merchant diverifikasi — merchantId={}", merchantId);
    }
}
