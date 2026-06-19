package com.priestess.oracle.producer;

import com.priestess.oracle.config.RabbitMQConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * SupportEventProducer — Publisher event pengaduan ke RabbitMQ.
 *
 * <p>Sesuai rules-eop-priestess.md SECTION 3 dan SECTION 8:
 * <blockquote>
 * "Support & Oracle Service bertindak sebagai Consumer asinkron murni
 * yang dipicu oleh masuknya event pengaduan dari broker,
 * bukan dari hit API HTTP langsung."
 * </blockquote>
 *
 * <h2>Alur Submit Keluhan</h2>
 * <ol>
 *   <li>{@code SupportController.submitComplaint()} menerima POST dari user.</li>
 *   <li>Controller memanggil {@code publishComplaintSubmitted()} di kelas ini.</li>
 *   <li>Event dikirim ke queue {@code complaint.processing} di RabbitMQ.</li>
 *   <li>{@code ComplaintConsumer} mendengarkan queue, menyimpan ke MongoDB,
 *       lalu memicu analisis AI secara asinkron.</li>
 *   <li>Controller langsung mengembalikan HTTP 202 Accepted tanpa menunggu
 *       proses AI selesai.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportEventProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Mempublikasikan event bahwa keluhan baru telah diterima dari user.
     *
     * <p>Consumer {@code ComplaintConsumer} akan memproses event ini secara asinkron.
     * Ini memisahkan HTTP layer (SupportController) dari processing layer (ComplaintConsumer),
     * sesuai prinsip Event-Driven Architecture.
     *
     * @param userId   UUID user yang mengajukan keluhan
     * @param username username pengirim
     * @param email    email pengirim untuk notifikasi
     * @param invoiceId ID invoice yang dikeluhkan (opsional, bisa null)
     * @param rawMessage teks keluhan mentah dari user
     */
    public void publishComplaintSubmitted(String userId, String username, String email,
                                          String invoiceId, String rawMessage) {
        ComplaintSubmittedEvent event = ComplaintSubmittedEvent.builder()
                .userId(userId)
                .username(username)
                .email(email)
                .invoiceId(invoiceId)
                .rawMessage(rawMessage)
                .submittedAt(java.time.LocalDateTime.now().toString())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_SUPPORT,
                RabbitMQConfig.QUEUE_COMPLAINT_PROCESSING,
                event
        );

        log.info("[SupportEventProducer] PUBLISHED complaint.processing — userId={}, invoiceId={}",
                userId, invoiceId);
    }

    // =========================================================================
    // INNER DTO — Payload event yang dikirim ke RabbitMQ
    // =========================================================================

    /**
     * Payload event saat user submit keluhan.
     * ComplaintConsumer akan meng-deserialize objek ini dari JSON.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplaintSubmittedEvent {
        private String userId;
        private String username;
        private String email;
        private String invoiceId;
        private String rawMessage;
        private String submittedAt;
    }
}
