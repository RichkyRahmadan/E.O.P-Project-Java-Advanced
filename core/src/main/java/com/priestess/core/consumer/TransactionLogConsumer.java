package com.priestess.core.consumer;

import com.priestess.core.config.RabbitMQConfig;
import com.priestess.core.entity.TransactionDocument;
import com.priestess.core.producer.FinanceEventProducer;
import com.priestess.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * TransactionLogConsumer — Consumer yang memperbarui status dokumen MongoDB
 * berdasarkan hasil akhir pembayaran QRIS dari Message Broker.
 *
 * <p>Sesuai rules-eop-priestess.md SECTION 5 (Fase Sinkronisasi Log):
 * <blockquote>
 * "Consumer lain yang bertugas menjaga konsistensi MongoDB mendengarkan Topic
 * {@code qris-payment-success} atau {@code qris-payment-failed}. Dokumen transaksi
 * di MongoDB diperbarui statusnya dari {@code PENDING} menjadi {@code SUCCESS} atau
 * {@code FAILED} berdasarkan event yang diterima dari Broker (<i>Eventual Consistency</i>)."
 * </blockquote>
 *
 * <h2>Eventual Consistency</h2>
 * <p>Saat Consumer ini mengeksekusi, PostgreSQL sudah selesai di-commit atau di-rollback
 * oleh {@code QrisPaymentConsumer}. Consumer ini hanya bertugas menyinkronkan
 * status di MongoDB agar konsisten dengan kondisi PostgreSQL.
 *
 * <p>Ini adalah inti dari pola <b>Eventual Consistency</b> dalam arsitektur
 * Event-Driven: dua database (PostgreSQL dan MongoDB) tidak harus konsisten
 * pada titik waktu yang sama, namun <i>pada akhirnya</i> akan konsisten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionLogConsumer {

    private final TransactionRepository transactionRepository;

    // =========================================================================
    // CONSUME: QRIS Payment SUCCESS
    // =========================================================================

    /**
     * Mendengarkan event {@code qris.payment.success} dari RabbitMQ.
     *
     * <p>Memperbarui dokumen transaksi di MongoDB dari status {@code PENDING}
     * menjadi {@code SUCCESS}. Ini adalah Fase 3 terakhir dari Saga QRIS Payment.
     *
     * @param event payload berisi invoiceId dan informasi buyer
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_QRIS_SUCCESS)
    public void handleQrisPaymentSuccess(FinanceEventProducer.QrisResultEvent event) {
        String invoiceId = event.getInvoiceId();
        log.info("[TransactionLogConsumer] Menerima qris.payment.success — invoiceId={}", invoiceId);

        try {
            TransactionDocument doc = transactionRepository.findByInvoiceId(invoiceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Dokumen transaksi tidak ditemukan: " + invoiceId));

            // Update sender info (diisi oleh QrisPaymentConsumer, tapi simpan ulang untuk amannya)
            if (event.getBuyerOwnerId() != null && doc.getSender() == null) {
                doc.setSender(TransactionDocument.PartyInfo.builder()
                        .userId(event.getBuyerOwnerId())
                        .walletId(event.getBuyerWalletId())
                        .build());
            }

            // FASE 3 (Eventual Consistency) — Update MongoDB ke SUCCESS
            doc.setStatus("SUCCESS");
            transactionRepository.save(doc);

            log.info("[TransactionLogConsumer] MongoDB diperbarui → SUCCESS untuk invoiceId={}", invoiceId);

        } catch (Exception e) {
            log.error("[TransactionLogConsumer] Gagal update SUCCESS untuk invoiceId={}: {}",
                    invoiceId, e.getMessage(), e);
        }
    }

    // =========================================================================
    // CONSUME: QRIS Payment FAILED
    // =========================================================================

    /**
     * Mendengarkan event {@code qris.payment.failed} dari RabbitMQ.
     *
     * <p>Memperbarui dokumen transaksi di MongoDB dari status {@code PENDING}
     * menjadi {@code FAILED}, dengan mencatat alasan kegagalan di field {@code note}.
     * PostgreSQL sudah di-rollback oleh {@code QrisPaymentConsumer}.
     *
     * @param event payload berisi invoiceId dan pesan error
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_QRIS_FAILED)
    public void handleQrisPaymentFailed(FinanceEventProducer.QrisResultEvent event) {
        String invoiceId = event.getInvoiceId();
        log.info("[TransactionLogConsumer] Menerima qris.payment.failed — invoiceId={}", invoiceId);

        try {
            TransactionDocument doc = transactionRepository.findByInvoiceId(invoiceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Dokumen transaksi tidak ditemukan: " + invoiceId));

            // FASE 3 (Eventual Consistency) — Update MongoDB ke FAILED
            doc.setStatus("FAILED");
            doc.setNote("ERROR: " + event.getErrorReason());
            transactionRepository.save(doc);

            log.info("[TransactionLogConsumer] MongoDB diperbarui → FAILED untuk invoiceId={}", invoiceId);

        } catch (Exception e) {
            log.error("[TransactionLogConsumer] Gagal update FAILED untuk invoiceId={}: {}",
                    invoiceId, e.getMessage(), e);
        }
    }
}
