package com.priestess.core.controller;

import com.priestess.core.dto.QrisGenerateRequest;
import com.priestess.core.dto.QrisPayRequest;
import com.priestess.core.dto.TransactionResponse;
import com.priestess.core.dto.TransferRequest;
import com.priestess.core.dto.VoucherRedeemRequest;
import com.priestess.core.dto.WalletResponse;
import com.priestess.core.service.FinanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * FinanceController — Seluruh endpoint Core Finance Service ({@code /api/finance}).
 *
 * <p>User ID diambil dari header {@code X-User-Id} yang disuntikkan oleh
 * E.O.P Gateway setelah JWT divalidasi. Controller tidak pernah membaca
 * atau memvalidasi JWT secara langsung — itu tugas Gateway.
 *
 * <p>Zero business logic — semua didelegasikan ke {@link FinanceService}.
 */
@Slf4j
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    // =========================================================================
    // GET /api/finance/wallet
    // =========================================================================

    /**
     * Ambil data dompet milik pengguna yang sedang login.
     * Header {@code X-User-Id} diisi oleh Gateway dari klaim JWT {@code sub}.
     */
    @GetMapping("/wallet")
    public ResponseEntity<WalletResponse> getMyWallet(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        log.info("[FinanceController] GET /api/finance/wallet — userId={}, role={}", userId, role);
        return ResponseEntity.ok(financeService.getMyWallet(UUID.fromString(userId), role));
    }

    // =========================================================================
    // POST /api/finance/transfer
    // =========================================================================

    /**
     * Transfer P2P — Debit saldo sender, kredit saldo recipient.
     * Menerapkan pola Dual-Write SECTION 7 (PENDING → SUCCESS/FAILED).
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Status", required = false) String status,
            @Valid @RequestBody TransferRequest request) {
        log.info("[FinanceController] POST /api/finance/transfer — from={}, status={}", userId, status);
        ensureActiveUser(status);
        return ResponseEntity.ok(financeService.transfer(UUID.fromString(userId), request));
    }

    // =========================================================================
    // POST /api/finance/qris/generate
    // =========================================================================

    /**
     * Generate invoice QRIS dinamis — buat transaksi PENDING untuk merchant.
     * Angular Merchant Dashboard akan polling status invoice ini setiap 3-5 detik.
     */
    @PostMapping("/qris/generate")
    public ResponseEntity<TransactionResponse> generateQris(
            @RequestHeader("X-User-Id") String merchantId,
            @RequestHeader(value = "X-User-Status", required = false) String status,
            @Valid @RequestBody QrisGenerateRequest request) {
        log.info("[FinanceController] POST /api/finance/qris/generate — merchantId={}, status={}", merchantId, status);
        ensureActiveUser(status);
        return ResponseEntity.ok(financeService.generateQris(UUID.fromString(merchantId), request));
    }

    // =========================================================================
    // POST /api/finance/qris/pay
    // =========================================================================

    /**
     * Bayar QRIS — Eksekusi pembayaran invoice yang sudah di-generate.
     * Mengubah status MongoDB dari PENDING menjadi SUCCESS (atau FAILED).
     */
    @PostMapping("/qris/pay")
    public ResponseEntity<TransactionResponse> payQris(
            @RequestHeader("X-User-Id") String buyerId,
            @RequestHeader(value = "X-User-Status", required = false) String status,
            @Valid @RequestBody QrisPayRequest request) {
        log.info("[FinanceController] POST /api/finance/qris/pay — buyerId={}, invoiceId={}, status={}",
                buyerId, request.getInvoiceId(), status);
        ensureActiveUser(status);
        return ResponseEntity.ok(financeService.payQris(UUID.fromString(buyerId), request));
    }

    // =========================================================================
    // POST /api/finance/voucher/redeem
    // =========================================================================

    /**
     * Klaim Voucher — Validasi kode, tambah saldo wallet, tandai voucher sebagai digunakan.
     */
    @PostMapping("/voucher/redeem")
    public ResponseEntity<TransactionResponse> redeemVoucher(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Status", required = false) String status,
            @Valid @RequestBody VoucherRedeemRequest request) {
        log.info("[FinanceController] POST /api/finance/voucher/redeem — userId={}, code={}, status={}",
                userId, request.getCode(), status);
        ensureActiveUser(status);
        return ResponseEntity.ok(financeService.redeemVoucher(UUID.fromString(userId), request));
    }

    private void ensureActiveUser(String status) {
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Transaksi ditolak: Akun Anda belum aktif (PENDING KYC).");
        }
    }

    // =========================================================================
    // GET /api/finance/transactions/{invoiceId}
    // =========================================================================

    /**
     * Cek status transaksi — Endpoint untuk polling status invoice oleh Angular.
     *
     * <p>Sesuai SECTION 8 blueprint: Angular menggunakan {@code RxJS interval}
     * untuk memanggil endpoint ini setiap 3-5 detik. Begitu status berubah
     * menjadi {@code SUCCESS}, polling berhenti dan layar diperbarui.
     */
    @GetMapping("/transactions/{invoiceId}")
    public ResponseEntity<TransactionResponse> getTransactionStatus(
            @PathVariable String invoiceId) {
        log.info("[FinanceController] GET /api/finance/transactions/{}", invoiceId);
        return ResponseEntity.ok(financeService.getTransactionStatus(invoiceId));
    }
}
