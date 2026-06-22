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

@Slf4j
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    @GetMapping("/wallet")
    public ResponseEntity<WalletResponse> getMyWallet(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        log.info("[FinanceController] GET /api/finance/wallet — userId={}, role={}", userId, role);
        return ResponseEntity.ok(financeService.getMyWallet(UUID.fromString(userId), role));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Status", required = false) String status,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody TransferRequest request) {
        log.info("[FinanceController] POST /api/finance/transfer — from={}, status={}, role={}", userId, status, role);
        ensureActiveUser(status);
        return ResponseEntity.ok(financeService.transfer(UUID.fromString(userId), role, request));
    }

    @PostMapping("/transfer/to-owner")
    public ResponseEntity<TransactionResponse> transferToOwner(
            @RequestHeader("X-User-Id") String merchantId,
            @RequestHeader(value = "X-User-Status", required = false) String status,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        log.info("[FinanceController] POST /api/finance/transfer/to-owner — merchantId={}, status={}", merchantId, status);
        ensureActiveUser(status);
        java.math.BigDecimal amount = new java.math.BigDecimal(body.get("amount").toString());
        String note = body.containsKey("note") ? body.get("note").toString() : null;
        return ResponseEntity.ok(financeService.transferToOwner(UUID.fromString(merchantId), amount, note));
    }

    @PostMapping("/qris/generate")
    public ResponseEntity<TransactionResponse> generateQris(
            @RequestHeader("X-User-Id") String merchantId,
            @RequestHeader(value = "X-User-Status", required = false) String status,
            @Valid @RequestBody QrisGenerateRequest request) {
        log.info("[FinanceController] POST /api/finance/qris/generate — merchantId={}, status={}", merchantId, status);
        ensureActiveUser(status);
        return ResponseEntity.ok(financeService.generateQris(UUID.fromString(merchantId), request));
    }

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

    @GetMapping("/transactions/{invoiceId}")
    public ResponseEntity<TransactionResponse> getTransactionStatus(
            @PathVariable String invoiceId) {
        log.info("[FinanceController] GET /api/finance/transactions/{}", invoiceId);
        return ResponseEntity.ok(financeService.getTransactionStatus(invoiceId));
    }
}
