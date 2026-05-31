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
 * <p>Menerapkan pola <b>Dual-Write State Machine</b> sesuai SECTION 7 blueprint:
 * <ol>
 *   <li>Simpan log {@code PENDING} ke MongoDB.</li>
 *   <li>Mutasi saldo di PostgreSQL dalam {@code @Transactional} dengan
 *       {@code @Version} Optimistic Locking.</li>
 *   <li>Jika sukses → update MongoDB ke {@code SUCCESS}.
 *       Jika gagal → rollback PostgreSQL otomatis + update MongoDB ke {@code FAILED}.</li>
 * </ol>
 *
 * <h2>Penting: Tidak Ada Self-Invocation</h2>
 * <p>Spring {@code @Transactional} bekerja via <b>AOP Proxy</b>. Jika sebuah
 * method {@code @Transactional} dipanggil dari method lain dalam kelas yang SAMA,
 * panggilan tersebut melewati proxy sehingga transaksi tidak aktif.
 * Oleh karena itu, seluruh logika PostgreSQL digabungkan langsung ke dalam
 * method utama yang sudah {@code @Transactional} — tidak ada pemisahan menjadi
 * method {@code protected} yang kemudian dipanggil secara internal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private final WalletRepository      walletRepository;
    private final VoucherRepository     voucherRepository;
    private final TransactionRepository transactionRepository;

    // =========================================================================
    // GET MY WALLET
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(UUID ownerId) {
        WalletEntity wallet = findWalletByOwner(ownerId);
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

    @Override
    @Transactional
    public TransactionResponse payQris(UUID buyerOwnerId, QrisPayRequest request) {
        String invoiceId = request.getInvoiceId();
        log.info("[FinanceService] Pembayaran QRIS — invoiceId={}, buyer={}", invoiceId, buyerOwnerId);

        TransactionDocument doc = transactionRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Invoice QRIS tidak ditemukan: " + invoiceId));

        if (!"PENDING".equals(doc.getStatus())) {
            throw new IllegalStateException(
                    "Invoice " + invoiceId + " sudah tidak bisa dibayar (status: " + doc.getStatus() + ").");
        }

        try {
            WalletEntity buyerWallet = findWalletByOwner(buyerOwnerId);
            UUID merchantWalletId    = UUID.fromString(doc.getRecipient().getWalletId());
            WalletEntity merchantWallet = walletRepository.findById(merchantWalletId)
                    .orElseThrow(() -> new IllegalStateException("Wallet merchant tidak ditemukan."));

            validateSufficientBalance(buyerWallet, doc.getAmount());

            buyerWallet.setBalance(buyerWallet.getBalance().subtract(doc.getAmount()));
            walletRepository.save(buyerWallet);

            merchantWallet.setBalance(merchantWallet.getBalance().add(doc.getAmount()));
            walletRepository.save(merchantWallet);

            doc.setSender(TransactionDocument.PartyInfo.builder()
                    .userId(buyerOwnerId.toString())
                    .walletId(buyerWallet.getId().toString())
                    .build());
            doc.setStatus("SUCCESS");
            transactionRepository.save(doc);

            log.info("[FinanceService] QRIS berhasil dibayar — invoiceId={}", invoiceId);

        } catch (Exception e) {
            doc.setStatus("FAILED");
            doc.setNote("ERROR: " + e.getMessage());
            transactionRepository.save(doc);

            log.error("[FinanceService] QRIS gagal — invoiceId={}, alasan={}", invoiceId, e.getMessage());
            throw new IllegalStateException("Pembayaran QRIS gagal: " + e.getMessage());
        }

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
