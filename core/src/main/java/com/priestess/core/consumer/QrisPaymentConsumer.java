package com.priestess.core.consumer;

import com.priestess.core.config.RabbitMQConfig;
import com.priestess.core.entity.TransactionDocument;
import com.priestess.core.entity.WalletEntity;
import com.priestess.core.producer.FinanceEventProducer;
import com.priestess.core.repository.TransactionRepository;
import com.priestess.core.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * QrisPaymentConsumer — Consumer Stateful yang memproses pembayaran QRIS via Saga Pattern.
 *
 * <p>Sesuai rules-eop-priestess.md SECTION 3: "Kelas beranotasi {@code @Component} /
 * {@code @RabbitListener} yang bertugas mendengarkan event dari broker dan memicu
 * logika bisnis."
 *
 * <p>Sesuai SECTION 5 (Workflow Transaksi Stateful): Consumer ini menjalankan
 * <b>Fase Pemrosesan Saldo (Stateful Consumer)</b>:
 * <ol>
 *   <li>Mendengarkan Topic {@code qris.payment.initiated}.</li>
 *   <li>Membuka blok {@code @Transactional} di PostgreSQL untuk menguji kelayakan
 *       dompet via <b>Optimistic Locking</b> ({@code @Version}).</li>
 *   <li>Jika Saldo Cukup: Kredit & Debit commit di PostgreSQL, lalu publish
 *       event {@code qris.payment.success}.</li>
 *   <li>Jika Saldo Kurang/Error: PostgreSQL di-rollback otomatis, lalu publish
 *       event {@code qris.payment.failed}.</li>
 * </ol>
 *
 * <h2>Perbedaan dengan Arsitektur Lama (Dual-Write Sinkron)</h2>
 * <p>Sebelumnya, {@code FinanceServiceImpl.payQris()} langsung memproses saldo
 * dalam satu request HTTP yang sama. Sekarang, pemrosesan saldo dipisah ke Consumer
 * ini agar berjalan secara asinkron — request HTTP HTTP dari buyer hanya menghasilkan
 * publikasi event, lalu langsung return. Angular kemudian melakukan HTTP Polling
 * ke {@code GET /api/finance/transactions/status/{invoiceId}} sesuai SECTION 7.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QrisPaymentConsumer {

    private final WalletRepository      walletRepository;
    private final TransactionRepository transactionRepository;
    private final FinanceEventProducer  financeEventProducer;

    /**
     * Mendengarkan event {@code qris.payment.initiated} dari RabbitMQ.
     *
     * <p>Method ini dipanggil secara asinkron oleh Spring AMQP saat ada pesan baru
     * di queue. Seluruh logika PostgreSQL dibungkus {@code @Transactional} agar
     * Optimistic Locking berfungsi dan rollback otomatis terjadi jika ada error.
     *
     * @param event payload event berisi invoiceId, buyerOwnerId, dan amount
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_QRIS_INITIATED)
    @Transactional
    public void handleQrisPaymentInitiated(FinanceEventProducer.QrisPaymentEvent event) {
        String invoiceId    = event.getInvoiceId();
        String buyerOwnerId = event.getBuyerOwnerId();

        log.info("[QrisPaymentConsumer] Menerima event qris.payment.initiated — invoiceId={}, buyer={}",
                invoiceId, buyerOwnerId);

        try {
            // ================================================================
            // FASE 2A — Cari dokumen transaksi PENDING di MongoDB
            // ================================================================
            TransactionDocument doc = transactionRepository.findByInvoiceId(invoiceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Invoice tidak ditemukan di MongoDB: " + invoiceId));

            if (!"PENDING".equals(doc.getStatus())) {
                log.warn("[QrisPaymentConsumer] Invoice {} sudah tidak PENDING (status: {}), skip.",
                        invoiceId, doc.getStatus());
                return;
            }

            // ================================================================
            // FASE 2B — Mutasi saldo PostgreSQL dengan Optimistic Locking (@Version)
            // ================================================================
            WalletEntity buyerWallet = walletRepository
                    .findByOwnerId(UUID.fromString(buyerOwnerId))
                    .orElseThrow(() -> new IllegalStateException(
                            "Wallet buyer tidak ditemukan: " + buyerOwnerId));

            UUID merchantWalletId = UUID.fromString(doc.getRecipient().getWalletId());
            WalletEntity merchantWallet = walletRepository.findById(merchantWalletId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Wallet merchant tidak ditemukan: " + merchantWalletId));

            // Validasi saldo cukup sebelum debit
            if (buyerWallet.getBalance().compareTo(doc.getAmount()) < 0) {
                throw new IllegalStateException(
                        "Saldo tidak mencukupi. Saldo: " + buyerWallet.getBalance() +
                        " | Dibutuhkan: " + doc.getAmount());
            }

            // Debit buyer (@Version Optimistic Locking aktif — mencegah double-spend)
            buyerWallet.setBalance(buyerWallet.getBalance().subtract(doc.getAmount()));
            walletRepository.save(buyerWallet);

            // Kredit merchant
            merchantWallet.setBalance(merchantWallet.getBalance().add(doc.getAmount()));
            walletRepository.save(merchantWallet);

            // Update sender info di dokumen (untuk log jejak transaksi)
            doc.setSender(TransactionDocument.PartyInfo.builder()
                    .userId(buyerOwnerId)
                    .walletId(buyerWallet.getId().toString())
                    .build());
            transactionRepository.save(doc);

            log.info("[QrisPaymentConsumer] Saldo berhasil dimutasi — invoiceId={}", invoiceId);

            // ================================================================
            // FASE 2C — Publish event SUCCESS untuk diproses TransactionLogConsumer
            // ================================================================
            financeEventProducer.publishQrisSuccess(
                    invoiceId,
                    buyerOwnerId,
                    buyerWallet.getId().toString()
            );

        } catch (Exception e) {
            // PostgreSQL di-rollback otomatis karena @Transactional
            log.error("[QrisPaymentConsumer] Gagal memproses pembayaran QRIS — invoiceId={}, alasan={}",
                    invoiceId, e.getMessage(), e);

            // ================================================================
            // FASE 2C (GAGAL) — Publish event FAILED
            // ================================================================
            financeEventProducer.publishQrisFailed(invoiceId, e.getMessage());
        }
    }
}
