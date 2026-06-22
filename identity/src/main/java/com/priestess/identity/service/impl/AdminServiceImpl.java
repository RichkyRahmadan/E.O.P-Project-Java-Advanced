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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository       userRepository;
    private final MerchantRepository   merchantRepository;
    private final StringRedisTemplate  redisTemplate;

    private final IdentityEventProducer identityEventProducer;

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

        refreshActiveSessionsInRedis(user);
    }

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

        try {
            identityEventProducer.publishUserSuspended(userId.toString(), user.getUsername());
        } catch (Exception e) {

            log.error("[AdminService] Gagal publish user.suspended ke broker: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void verifyMerchant(UUID merchantId) {
        MerchantEntity merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Merchant dengan ID " + merchantId + " tidak ditemukan."));

        merchant.setIsVerified(true);
        merchantRepository.save(merchant);
        log.info("[AdminService] Merchant diverifikasi — merchantId={}", merchantId);

        UserEntity user = merchant.getUser();
        if (user != null && "PENDING".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("ACTIVE");
            userRepository.save(user);
            log.info("[AdminService] Status user milik merchant otomatis diubah menjadi ACTIVE");

            refreshActiveSessionsInRedis(user);
        }
    }

    private void refreshActiveSessionsInRedis(UserEntity user) {
        String userSessionsKey = "user:sessions:" + user.getId().toString();
        try {
            Set<String> activeTokens = redisTemplate.opsForSet().members(userSessionsKey);
            if (activeTokens == null || activeTokens.isEmpty()) {
                log.debug("[AdminService] Tidak ada sesi aktif di Redis untuk userId={} — tidak perlu diperbarui.",
                        user.getId());
                return;
            }

            for (String token : activeTokens) {
                String sessionKey = "session:" + token;
                String sessionRaw = redisTemplate.opsForValue().get(sessionKey);
                if (sessionRaw == null) continue;

                String[] parts = sessionRaw.split(":::", -1);
                if (parts.length >= 6) {

                    parts[4] = "ACTIVE";
                    String updatedSession = String.join(":::", parts);

                    Long ttlSeconds = redisTemplate.getExpire(sessionKey, TimeUnit.SECONDS);
                    long remainingTtl = (ttlSeconds != null && ttlSeconds > 0) ? ttlSeconds : 900L;

                    redisTemplate.opsForValue().set(sessionKey, updatedSession, remainingTtl, TimeUnit.SECONDS);
                    log.debug("[AdminService] Sesi Redis diperbarui ke ACTIVE — token prefix={}, ttlSisa={}s",
                            token.substring(0, Math.min(token.length(), 12)), remainingTtl);
                }
            }

            log.info("[AdminService] {} sesi Redis berhasil diperbarui ke ACTIVE untuk userId={}",
                    activeTokens.size(), user.getId());

        } catch (Exception e) {

            log.error("[AdminService] Gagal memperbarui sesi Redis saat verifikasi: {}", e.getMessage(), e);
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
