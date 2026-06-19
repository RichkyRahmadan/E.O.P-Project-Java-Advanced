package com.priestess.identity.producer;

import com.priestess.identity.config.RabbitMQConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * IdentityEventProducer — Publisher event identitas ke RabbitMQ.
 *
 * <p>Sesuai rules-eop-priestess.md SECTION 3: "Kelas beranotasi {@code @Component}
 * yang bertugas mengirimkan pesan/event ke Message Broker."
 *
 * <h2>Event yang Dipublikasikan</h2>
 * <ul>
 *   <li>{@code user.suspended} — Dipublikasikan saat Admin membekukan akun user.
 *       Gateway Consumer akan menerima event ini dan segera menandai userId
 *       tersebut sebagai suspended di in-memory cache, sehingga setiap request
 *       berikutnya dari user tersebut langsung ditolak tanpa menunggu JWT kedaluwarsa.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityEventProducer {

    private final RabbitTemplate rabbitTemplate;

    // =========================================================================
    // PUBLISH: User Suspended
    // =========================================================================

    /**
     * Mempublikasikan event bahwa akun user telah dibekukan oleh Admin.
     *
     * <p>Event ini diterima oleh {@code SuspendedUserConsumer} di Gateway.
     * Gateway kemudian menambahkan {@code userId} ke in-memory cache
     * dan menolak semua request dari token yang membawa sub (userId) tersebut
     * secara real-time — jauh sebelum Access Token kedaluwarsa 15 menit.
     *
     * @param userId UUID pengguna yang dibekukan (format String)
     * @param username username untuk keperluan logging
     */
    public void publishUserSuspended(String userId, String username) {
        UserSuspendedEvent event = UserSuspendedEvent.builder()
                .userId(userId)
                .username(username)
                .suspendedAt(java.time.LocalDateTime.now().toString())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_IDENTITY,
                RabbitMQConfig.QUEUE_USER_SUSPENDED,
                event
        );

        log.info("[IdentityEventProducer] PUBLISHED user.suspended — userId={}, username={}",
                userId, username);
    }

    // =========================================================================
    // INNER DTO — Payload event suspend
    // =========================================================================

    /**
     * Payload event yang dikirim saat user dibekukan.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSuspendedEvent {
        private String userId;
        private String username;
        private String suspendedAt;
    }
}
