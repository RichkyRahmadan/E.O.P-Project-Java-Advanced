package com.priestess.core.producer;

import com.priestess.core.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * FinanceEventProducer — Publisher event finansial ke RabbitMQ.
 *
 * <p>Sesuai rules-eop-priestess.md SECTION 3: "Kelas beranotasi {@code @Component}
 * yang bertugas mengirimkan pesan/event ke Message Broker (Kafka/RabbitMQ)."
 *
 * <p>Setiap method dalam kelas ini mempublikasikan satu jenis event ke exchange
 * {@code eop.finance.exchange} dengan routing key yang sesuai nama queue tujuan.
 *
 * <h2>Event yang Dipublikasikan</h2>
 * <ul>
 *   <li>{@code qris.payment.initiated} — Setelah QRIS invoice dibuat di MongoDB (status PENDING).
 *       Consumer ({@code QrisPaymentConsumer}) akan memproses mutasi saldo.</li>
 *   <li>{@code qris.payment.success} — Setelah mutasi saldo PostgreSQL BERHASIL.</li>
 *   <li>{@code qris.payment.failed}  — Setelah mutasi saldo PostgreSQL GAGAL/ROLLBACK.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceEventProducer {

    private final RabbitTemplate rabbitTemplate;

    // =========================================================================
    // PUBLISH: QRIS Payment Initiated
    // =========================================================================

    /**
     * Mempublikasikan event bahwa invoice QRIS baru telah dibuat dan menunggu
     * pembayaran dari buyer. Dipanggil SETELAH dokumen {@code PENDING} tersimpan
     * di MongoDB.
     *
     * @param invoiceId    ID invoice QRIS yang baru dibuat
     * @param buyerOwnerId UUID owner/buyer yang membayar
     * @param amount       nominal pembayaran
     */
    public void publishQrisInitiated(String invoiceId, String buyerOwnerId, BigDecimal amount) {
        QrisPaymentEvent event = QrisPaymentEvent.builder()
                .invoiceId(invoiceId)
                .buyerOwnerId(buyerOwnerId)
                .amount(amount)
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_FINANCE,
                RabbitMQConfig.QUEUE_QRIS_INITIATED,
                event
        );

        log.info("[FinanceEventProducer] PUBLISHED qris.payment.initiated — invoiceId={}, buyer={}",
                invoiceId, buyerOwnerId);
    }

    // =========================================================================
    // PUBLISH: QRIS Payment Success
    // =========================================================================

    /**
     * Mempublikasikan event bahwa mutasi saldo QRIS di PostgreSQL BERHASIL.
     * Dipanggil oleh {@code QrisPaymentConsumer} setelah commit transaksi sukses.
     *
     * @param invoiceId ID invoice QRIS yang berhasil dibayar
     * @param buyerOwnerId UUID buyer
     * @param buyerWalletId UUID wallet buyer
     */
    public void publishQrisSuccess(String invoiceId, String buyerOwnerId, String buyerWalletId) {
        QrisResultEvent event = QrisResultEvent.builder()
                .invoiceId(invoiceId)
                .buyerOwnerId(buyerOwnerId)
                .buyerWalletId(buyerWalletId)
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_FINANCE,
                RabbitMQConfig.QUEUE_QRIS_SUCCESS,
                event
        );

        log.info("[FinanceEventProducer] PUBLISHED qris.payment.success — invoiceId={}", invoiceId);
    }

    // =========================================================================
    // PUBLISH: QRIS Payment Failed
    // =========================================================================

    /**
     * Mempublikasikan event bahwa mutasi saldo QRIS GAGAL (PostgreSQL sudah di-rollback).
     * Dipanggil oleh {@code QrisPaymentConsumer} setelah exception tertangkap.
     *
     * @param invoiceId ID invoice QRIS yang gagal
     * @param errorReason pesan error untuk dicatat di MongoDB field {@code note}
     */
    public void publishQrisFailed(String invoiceId, String errorReason) {
        QrisResultEvent event = QrisResultEvent.builder()
                .invoiceId(invoiceId)
                .errorReason(errorReason)
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_FINANCE,
                RabbitMQConfig.QUEUE_QRIS_FAILED,
                event
        );

        log.info("[FinanceEventProducer] PUBLISHED qris.payment.failed — invoiceId={}, reason={}",
                invoiceId, errorReason);
    }

    // =========================================================================
    // INNER DTO CLASSES — Event payload yang dikirim ke RabbitMQ
    // =========================================================================

    /**
     * Payload event saat QRIS baru saja diinisiasi (buyer siap membayar).
     * Berisi semua informasi yang dibutuhkan Consumer untuk memproses saldo.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QrisPaymentEvent {
        private String     invoiceId;
        private String     buyerOwnerId;
        private BigDecimal amount;
    }

    /**
     * Payload event hasil akhir pembayaran QRIS (SUCCESS atau FAILED).
     * Consumer {@code TransactionLogConsumer} akan menggunakannya
     * untuk memperbarui status dokumen di MongoDB.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class QrisResultEvent {
        private String invoiceId;
        private String buyerOwnerId;
        private String buyerWalletId;
        private String errorReason;
    }
}
