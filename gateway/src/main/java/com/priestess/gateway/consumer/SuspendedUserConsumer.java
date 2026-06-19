package com.priestess.gateway.consumer;

import com.priestess.gateway.config.RabbitMQConfig;
import com.priestess.gateway.filter.SuspendedUserCache;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * SuspendedUserConsumer — Consumer yang mendengarkan event pembekuan akun dari Identity Service.
 *
 * <p>Sesuai rules-eop-priestess.md SECTION 3: "Kelas beranotasi {@code @Component} /
 * {@code @RabbitListener} yang bertugas mendengarkan event dari broker dan memicu
 * logika bisnis."
 *
 * <p>Sesuai SECTION 6 (Security Mechanism):
 * <blockquote>
 * "Ketika Admin membekukan akun di Identity Service, service tersebut akan langsung
 * melempar pesan {@code user.suspended} ke Message Broker. E.O.P Gateway yang
 * bertindak sebagai Consumer akan menangkap pesan tersebut secara real-time dan
 * langsung menandai sesi user tersebut tidak valid saat itu juga di memori,
 * memberikan efek penendangan user secara instan tanpa menunggu token kedaluwarsa
 * 15 menit."
 * </blockquote>
 *
 * <h2>Cara Kerja</h2>
 * <ol>
 *   <li>Admin memanggil {@code PATCH /api/admin/users/{userId}/suspend}.</li>
 *   <li>Identity Service update status DB ke SUSPENDED dan publish {@code user.suspended}.</li>
 *   <li>Consumer ini {@code @RabbitListener} menerima event secara real-time.</li>
 *   <li>userId ditambahkan ke {@link SuspendedUserCache}.</li>
 *   <li>{@code GatewayJwtFilter} pada request berikutnya dari userId tersebut
 *       akan memeriksa cache dan menolak request dengan HTTP 403 Forbidden.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuspendedUserConsumer {

    private final SuspendedUserCache suspendedUserCache;
    private final StringRedisTemplate redisTemplate;

    /**
     * Mendengarkan event {@code user.suspended} dari RabbitMQ.
     *
     * <p>Dipanggil secara asinkron oleh Spring AMQP saat Identity Service
     * mempublikasikan event pembekuan akun. Tidak ada delay — Gateway langsung
     * memperbarui cache dalam millisecond setelah Admin menekan tombol suspend.
     *
     * @param event payload berisi userId, username, dan timestamp pembekuan
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_USER_SUSPENDED)
    public void handleUserSuspended(UserSuspendedEvent event) {
        log.warn("[SuspendedUserConsumer] MENERIMA user.suspended — userId={}, username={}, at={}",
                event.getUserId(), event.getUsername(), event.getSuspendedAt());

        try {
            // 1. Tambahkan ke cache in-memory gateway
            suspendedUserCache.addSuspendedUser(event.getUserId());
            log.info("[SuspendedUserConsumer] userId={} berhasil ditambahkan ke suspended cache. " +
                    "Semua request berikutnya dari user ini akan ditolak 403 Forbidden.",
                    event.getUserId());

            // 2. Hapus semua sesi user dari Redis agar token langsung invalid
            String userSessionsKey = "user:sessions:" + event.getUserId();
            Set<String> activeTokens = redisTemplate.opsForSet().members(userSessionsKey);
            if (activeTokens != null) {
                for (String token : activeTokens) {
                    redisTemplate.delete("session:" + token);
                }
            }
            redisTemplate.delete(userSessionsKey);
            log.info("[SuspendedUserConsumer] Berhasil menghapus semua sesi Redis untuk userId={}", event.getUserId());

        } catch (Exception e) {
            log.error("[SuspendedUserConsumer] Gagal memproses penangguhan akun userId={}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }

    // =========================================================================
    // INNER DTO — Struktur payload event yang diterima dari Identity Service
    // HARUS IDENTIK dengan IdentityEventProducer.UserSuspendedEvent
    // =========================================================================

    /**
     * Payload event suspend yang diterima dari Identity Service via RabbitMQ.
     * Struktur harus sesuai dengan {@code IdentityEventProducer.UserSuspendedEvent}.
     */
    @Data
    @NoArgsConstructor
    public static class UserSuspendedEvent {
        private String userId;
        private String username;
        private String suspendedAt;
    }
}
