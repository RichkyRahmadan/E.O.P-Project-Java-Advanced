package com.priestess.core.service.impl;

import com.priestess.core.dto.QrisGenerateRequest;
import com.priestess.core.dto.QrisPayRequest;
import com.priestess.core.dto.TransactionResponse;
import com.priestess.core.dto.TransferRequest;
import com.priestess.core.dto.VoucherRedeemRequest;
import com.priestess.core.dto.WalletResponse;
import com.priestess.core.entity.TransactionDocument;
import com.priestess.core.entity.VoucherEntity;
import com.priestess.core.entity.WalletEntity;
import com.priestess.core.producer.FinanceEventProducer;
import com.priestess.core.repository.TransactionRepository;
import com.priestess.core.repository.VoucherRepository;
import com.priestess.core.repository.WalletRepository;
import com.priestess.core.service.FinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FinanceServiceImpl — Implementasi lengkap seluruh operasi finansial.
 *
 * <p>Menerapkan pola <b>Event-Driven Saga Pattern</b> via RabbitMQ sesuai
 * rules-eop-priestess.md SECTION 5 (Workflow Transaksi Stateful & Event-Driven):
 *
 * <h2>Alur Pembayaran QRIS (Saga Choreography)</h2>
 * <ol>
 *   <li><b>Fase 1 — Inisiasi (method ini):</b> Simpan log {@code PENDING} ke MongoDB.
 *       Publish event {@code qris.payment.initiated} ke RabbitMQ. Return LANGSUNG
 *       ke client dengan status {@code PENDING}.</li>
 *   <li><b>Fase 2 — Pemrosesan Saldo ({@link com.priestess.core.consumer.QrisPaymentConsumer}):</b>
 *       Consumer mendengarkan event, membuka {@code @Transactional} di PostgreSQL,
 *       mutasi saldo dengan Optimistic Locking. Publish {@code qris.payment.success}
 *       atau {@code qris.payment.failed} ke broker.</li>
 *   <li><b>Fase 3 — Sinkronisasi Log ({@link com.priestess.core.consumer.TransactionLogConsumer}):</b>
 *       Consumer memperbarui MongoDB dari {@code PENDING} menjadi {@code SUCCESS}
 *       atau {@code FAILED} (Eventual Consistency).</li>
 * </ol>
 *
 * <h2>HTTP Polling untuk Status Real-Time</h2>
 * <p>Angular Frontend melakukan polling ke
 * {@code GET /api/finance/transactions/status/{invoiceId}} setiap 3-5 detik
 * untuk memantau kapan status bergeser dari {@code PENDING} (sesuai SECTION 7).
 *
 * <h2>Operasi Lain (Transfer, Voucher, Top-Up)</h2>
 * <p>Masih menggunakan Dual-Write sinkron karena tidak ada cross-service dependency.
 * Hanya QRIS Payment yang memerlukan Saga Pattern karena melibatkan dua wallet
 * (buyer dan merchant) yang mungkin berada dalam kondisi race condition tinggi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private final WalletRepository      walletRepository;
    private final VoucherRepository     voucherRepository;
    private final TransactionRepository transactionRepository;
    /**
     * Producer untuk mempublikasikan event QRIS ke RabbitMQ.
     * Sesuai SECTION 3 blueprint: komunikasi ke broker dilakukan via Producer bean.
     */
    private final FinanceEventProducer  financeEventProducer;

    // =========================================================================
    // GET MY WALLET
    // =========================================================================

    @Override
    @Transactional
    public WalletResponse getMyWallet(UUID ownerId, String role) {
        WalletEntity wallet;
        try {
            wallet = findWalletByOwner(ownerId);
        } catch (IllegalStateException e) {
            String ownerType = "USER";
            if (role != null && role.contains("MERCHANT")) {
                ownerType = "MERCHANT";
            }
            log.info("[FinanceService] Wallet tidak ditemukan untuk owner: {}. Inisialisasi lazy wallet tipe {}", ownerId, ownerType);
            wallet = WalletEntity.builder()
                    .ownerId(ownerId)
                    .ownerType(ownerType)
                    .balance(BigDecimal.ZERO)
                    .build();
            wallet = walletRepository.save(wallet);
        }
        return toWalletResponse(wallet);
    }

    // =========================================================================
    // TRANSFER P2P
    // =========================================================================

    /**
     * Transfer P2P dengan pola Dual-Write:
     * Langkah 1 (MongoDB PENDING) berjalan di luar transaksi PostgreSQL.
     * Langkah 2 (PostgreSQL debit+kredit) dibungkus {@code @Transactional}.
     * Langkah 3 (MongoDB SUCCESS/FAILED) dieksekusi berdasarkan hasil Langkah 2.
     *
     * <p>Seluruh logika PostgreSQL ada di dalam method ini secara langsung
     * untuk menghindari masalah Spring AOP self-invocation.
     */
    @Override
    @Transactional
    public TransactionResponse transfer(UUID senderOwnerId, TransferRequest request) {
        String invoiceId = "TRF-" + UUID.randomUUID();
        log.info("[FinanceService] Transfer dimulai — invoiceId={}, from={}, to={}",
                invoiceId, senderOwnerId, request.getRecipientWalletId());

        // LANGKAH 1 — Baca data awal (masih dalam @Transactional yang sama)
        WalletEntity senderWallet = findWalletByOwner(senderOwnerId);

        // Simpan PENDING ke MongoDB SEBELUM mutasi PostgreSQL
        TransactionDocument doc = TransactionDocument.builder()
                .invoiceId(invoiceId)
                .transactionType("TRANSFER")
                .status("PENDING")
                .amount(request.getAmount())
                .sender(TransactionDocument.PartyInfo.builder()
                        .userId(senderOwnerId.toString())
                        .walletId(senderWallet.getId().toString())
                        .build())
                .recipient(TransactionDocument.PartyInfo.builder()
                        .walletId(request.getRecipientWalletId())
                        .build())
                .note(request.getNote())
                .build();
        transactionRepository.save(doc);

        // LANGKAH 2 — Eksekusi mutasi PostgreSQL (dalam @Transactional yang sama)
        try {
            validateSufficientBalance(senderWallet, request.getAmount());

            WalletEntity recipientWallet = walletRepository
                    .findById(UUID.fromString(request.getRecipientWalletId()))
                    .orElseThrow(() -> new IllegalStateException("Wallet penerima tidak ditemukan."));

            // Debit sender — @Version Optimistic Locking aktif secara otomatis
            senderWallet.setBalance(senderWallet.getBalance().subtract(request.getAmount()));
            walletRepository.save(senderWallet);

            // Kredit recipient
            recipientWallet.setBalance(recipientWallet.getBalance().add(request.getAmount()));
            walletRepository.save(recipientWallet);

            // LANGKAH 3 — Update MongoDB SUCCESS
            doc.getRecipient().setUserId(recipientWallet.getOwnerId().toString());
            doc.setStatus("SUCCESS");
            transactionRepository.save(doc);

            log.info("[FinanceService] Transfer berhasil — invoiceId={}", invoiceId);

        } catch (Exception e) {
            // LANGKAH 3 (GAGAL) — PostgreSQL di-rollback otomatis oleh @Transactional,
            // update MongoDB ke FAILED sebagai bukti kegagalan
            doc.setStatus("FAILED");
            doc.setNote("ERROR: " + e.getMessage());
            transactionRepository.save(doc);

            log.error("[FinanceService] Transfer gagal — invoiceId={}, alasan={}", invoiceId, e.getMessage());
            throw new IllegalStateException("Transfer gagal: " + e.getMessage());
        }

        return toTransactionResponse(doc);
    }

    // =========================================================================
    // GENERATE QRIS DINAMIS
    // =========================================================================

    @Override
    @Transactional
    public TransactionResponse generateQris(UUID merchantOwnerId, QrisGenerateRequest request) {
        String invoiceId = "QRIS-" + UUID.randomUUID();
        WalletEntity merchantWallet = findWalletByOwner(merchantOwnerId);

        // Raw QRIS data — format sederhana (gunakan library QRIS resmi di produksi)
        String rawQrisData = String.format("EOP-QRIS|INV:%s|AMT:%.2f|MID:%s",
                invoiceId, request.getAmount(), merchantWallet.getId());

        TransactionDocument doc = TransactionDocument.builder()
                .invoiceId(invoiceId)
                .transactionType("QRIS_PAYMENT")
                .status("PENDING")
                .amount(request.getAmount())
                .recipient(TransactionDocument.PartyInfo.builder()
                        .userId(merchantOwnerId.toString())
                        .walletId(merchantWallet.getId().toString())
                        .build())
                .rawQrisData(rawQrisData)
                .note(request.getNote())
                .build();

        transactionRepository.save(doc);
        log.info("[FinanceService] QRIS invoice dibuat — invoiceId={}", invoiceId);

        return toTransactionResponse(doc);
    }

    // =========================================================================
    // PAY QRIS
    // =========================================================================

    /**
     * Memulai alur pembayaran QRIS via Saga Pattern (Fase 1 dari 3).
     *
     * <p>Method ini TIDAK lagi memproses mutasi saldo secara sinkron.
     * Sebaliknya, ia hanya memvalidasi bahwa invoice PENDING dan buyer ada,
     * lalu mempublikasikan event ke RabbitMQ. Pemrosesan saldo dilanjutkan
     * secara asinkron oleh {@code QrisPaymentConsumer}.
     *
     * <p>Response yang dikembalikan masih berstatus {@code PENDING}. Angular
     * Frontend harus melakukan HTTP Polling ke endpoint status untuk memantau
     * perubahan ke {@code SUCCESS} atau {@code FAILED} (sesuai SECTION 7).
     */
    @Override
    @Transactional
    public TransactionResponse payQris(UUID buyerOwnerId, QrisPayRequest request) {
        String invoiceId = request.getInvoiceId();
        log.info("[FinanceService] Inisiasi pembayaran QRIS (Saga Fase 1) — invoiceId={}, buyer={}",
                invoiceId, buyerOwnerId);

        // Validasi invoice ada dan masih PENDING
        TransactionDocument doc = transactionRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Invoice QRIS tidak ditemukan: " + invoiceId));

        if (!"PENDING".equals(doc.getStatus())) {
            throw new IllegalStateException(
                    "Invoice " + invoiceId + " sudah tidak bisa dibayar (status: " + doc.getStatus() + ").");
        }

        // Validasi buyer wallet ada (early check sebelum publish ke broker)
        WalletEntity buyerWallet = findWalletByOwner(buyerOwnerId);

        // FASE 1: Publish event ke RabbitMQ — pemrosesan saldo dilanjutkan oleh Consumer
        // QrisPaymentConsumer akan mendengarkan event ini dan memproses mutasi saldo
        financeEventProducer.publishQrisInitiated(
                invoiceId,
                buyerOwnerId.toString(),
                doc.getAmount()
        );

        log.info("[FinanceService] Event qris.payment.initiated dipublish — invoiceId={}", invoiceId);

        // Return PENDING — Angular harus polling status untuk menunggu hasil akhir
        return toTransactionResponse(doc);
    }

    // =========================================================================
    // REDEEM VOUCHER
    // =========================================================================

    @Override
    @Transactional
    public TransactionResponse redeemVoucher(UUID ownerId, VoucherRedeemRequest request) {
        String invoiceId = "VCH-" + UUID.randomUUID();
        log.info("[FinanceService] Klaim voucher — code={}, ownerId={}", request.getCode(), ownerId);

        VoucherEntity voucher = voucherRepository.findAvailableByCode(request.getCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Kode voucher '" + request.getCode() + "' tidak valid atau sudah digunakan."));

        WalletEntity wallet = findWalletByOwner(ownerId);

        TransactionDocument doc = TransactionDocument.builder()
                .invoiceId(invoiceId)
                .transactionType("VOUCHER_REDEEM")
                .status("PENDING")
                .amount(voucher.getAmount())
                .recipient(TransactionDocument.PartyInfo.builder()
                        .userId(ownerId.toString())
                        .walletId(wallet.getId().toString())
                        .build())
                .note("Klaim voucher: " + voucher.getCode())
                .build();
        transactionRepository.save(doc);

        try {
            wallet.setBalance(wallet.getBalance().add(voucher.getAmount()));
            walletRepository.save(wallet);

            voucher.setIsRedeemed(true);
            voucher.setRedeemedBy(ownerId);
            voucher.setRedeemedAt(LocalDateTime.now());
            voucherRepository.save(voucher);

            doc.setStatus("SUCCESS");
            transactionRepository.save(doc);

            log.info("[FinanceService] Voucher berhasil diklaim — code={}, nominal={}",
                    voucher.getCode(), voucher.getAmount());

        } catch (Exception e) {
            doc.setStatus("FAILED");
            doc.setNote("ERROR: " + e.getMessage());
            transactionRepository.save(doc);

            log.error("[FinanceService] Klaim voucher gagal — code={}, alasan={}", request.getCode(), e.getMessage());
            throw new IllegalStateException("Klaim voucher gagal: " + e.getMessage());
        }

        return toTransactionResponse(doc);
    }

    // =========================================================================
    // GET TRANSACTION STATUS (untuk polling Angular)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionStatus(String invoiceId) {
        TransactionDocument doc = transactionRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new IllegalStateException(
                        "Transaksi dengan invoice ID '" + invoiceId + "' tidak ditemukan."));
        return toTransactionResponse(doc);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private WalletEntity findWalletByOwner(UUID ownerId) {
        return walletRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet tidak ditemukan untuk owner: " + ownerId));
    }

    private void validateSufficientBalance(WalletEntity wallet, BigDecimal amount) {
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Saldo tidak mencukupi. Saldo Anda: Rp " + wallet.getBalance() +
                    " | Dibutuhkan: Rp " + amount);
        }
    }

    private WalletResponse toWalletResponse(WalletEntity w) {
        return WalletResponse.builder()
                .id(w.getId()).ownerId(w.getOwnerId())
                .ownerType(w.getOwnerType()).balance(w.getBalance())
                .updatedAt(w.getUpdatedAt()).build();
    }

    private TransactionResponse toTransactionResponse(TransactionDocument d) {
        return TransactionResponse.builder()
                .invoiceId(d.getInvoiceId())
                .transactionType(d.getTransactionType())
                .status(d.getStatus())
                .amount(d.getAmount())
                .note(d.getNote())
                .createdAt(d.getCreatedAt())
                .sender(d.getSender() != null ? TransactionResponse.PartyInfo.builder()
                        .userId(d.getSender().getUserId())
                        .walletId(d.getSender().getWalletId())
                        .displayName(d.getSender().getDisplayName()).build() : null)
                .recipient(d.getRecipient() != null ? TransactionResponse.PartyInfo.builder()
                        .userId(d.getRecipient().getUserId())
                        .walletId(d.getRecipient().getWalletId())
                        .displayName(d.getRecipient().getDisplayName()).build() : null)
                .build();
    }
}
