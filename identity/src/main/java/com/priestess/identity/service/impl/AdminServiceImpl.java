package com.priestess.identity.service.impl;

import com.priestess.identity.dto.PendingMerchantResponse;
import com.priestess.identity.dto.PendingUserResponse;
import com.priestess.identity.entity.MerchantEntity;
import com.priestess.identity.entity.UserEntity;
import com.priestess.identity.producer.IdentityEventProducer;
import com.priestess.identity.repository.MerchantRepository;
import com.priestess.identity.repository.UserRepository;
import com.priestess.identity.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.UUID;

/**
 * AdminServiceImpl — Implementasi operasi manajemen sistem.
 *
 * <p>Menangani verifikasi KYC, pembekuan akun, dan verifikasi merchant.
 * Semua operasi bersifat idempotent — memanggil berulang kali tidak
 * akan menghasilkan efek sampingan yang tidak diinginkan.
 *
 * <h2>Integrasi Message Broker (SECTION 6)</h2>
 * <p>Ketika Admin membekukan akun via {@link #suspendUser}, method ini
 * juga mempublikasikan event {@code user.suspended} ke RabbitMQ melalui
 * {@link IdentityEventProducer}. Gateway yang berperan sebagai Consumer
 * akan menerima event ini secara real-time dan langsung menandai sesi
 * user tersebut tidak valid di in-memory cache — efek penendangan instan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository       userRepository;
    private final MerchantRepository   merchantRepository;
    private final StringRedisTemplate  redisTemplate;
    /**
     * Producer untuk mempublikasikan event suspend ke RabbitMQ.
     * Gateway akan mengkonsumsi event ini untuk invalidasi sesi real-time.
     */
    private final IdentityEventProducer identityEventProducer;

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

        // Hapus sesi Redis langsung di Identity service
        String userSessionsKey = "user:sessions:" + userId.toString();
        try {
            Set<String> activeTokens = redisTemplate.opsForSet().members(userSessionsKey);
            if (activeTokens != null) {
                for (String token : activeTokens) {
                    redisTemplate.delete("session:" + token);
                }
            }
            redisTemplate.delete(userSessionsKey);
            log.info("[AdminService] Berhasil menghapus semua sesi Redis untuk userId={} langsung dari DB", userId);
        } catch (Exception e) {
            log.error("[AdminService] Gagal menghapus sesi Redis saat suspend: {}", e.getMessage(), e);
        }

        // Publish event ke RabbitMQ agar Gateway langsung menendang sesi user ini
        // Sesuai SECTION 6: real-time invalidation tanpa menunggu JWT kedaluwarsa
        try {
            identityEventProducer.publishUserSuspended(userId.toString(), user.getUsername());
        } catch (Exception e) {
            // Gagal publish tidak membatalkan suspend (DB sudah terupdate)
            // User akan tetap ter-logout saat token kedaluwarsa (fallback 15 menit)
            log.error("[AdminService] Gagal publish user.suspended ke broker: {}", e.getMessage(), e);
        }
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

        // Aktifkan juga user yang memilikinya jika statusnya PENDING
        UserEntity user = merchant.getUser();
        if (user != null && "PENDING".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("ACTIVE");
            userRepository.save(user);
            log.info("[AdminService] Status user milik merchant otomatis diubah menjadi ACTIVE");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingUserResponse> getPendingUsers() {
        log.info("[AdminService] Mengambil daftar user PENDING.");
        return userRepository.findAllByRole_RoleNameAndStatus("ROLE_USER", "PENDING").stream()
                .map(user -> PendingUserResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .status(user.getStatus())
                        .createdAt(user.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingMerchantResponse> getPendingMerchants() {
        log.info("[AdminService] Mengambil daftar merchant belum terverifikasi.");
        return merchantRepository.findAllByIsVerified(false).stream()
                .map(m -> PendingMerchantResponse.builder()
                        .id(m.getId())
                        .merchantName(m.getMerchantName())
                        .address(m.getAddress())
                        .username(m.getUser() != null ? m.getUser().getUsername() : null)
                        .email(m.getUser() != null ? m.getUser().getEmail() : null)
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
