        package com.priestess.core.service.impl;

import com.priestess.core.dto.QrisGenerateRequest;
import com.priestess.core.dto.QrisPayRequest;
import com.priestess.core.dto.TransactionResponse;
import com.priestess.core.dto.TransferRequest;
import com.priestess.core.dto.VoucherRedeemRequest;
import com.priestess.core.dto.WalletResponse;
import com.priestess.core.entity.MerchantOwnerMappingEntity;
import com.priestess.core.entity.TransactionDocument;
import com.priestess.core.entity.VoucherEntity;
import com.priestess.core.entity.WalletEntity;
import com.priestess.core.producer.FinanceEventProducer;
import com.priestess.core.repository.MerchantOwnerMappingRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private final WalletRepository      walletRepository;
    private final VoucherRepository     voucherRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantOwnerMappingRepository merchantOwnerMappingRepository;

    private final FinanceEventProducer  financeEventProducer;

    @Override
    @Transactional
    public WalletResponse getMyWallet(UUID ownerId, String role) {
        String ownerType = "USER";
        if (role != null && role.contains("MERCHANT")) {
            ownerType = "MERCHANT";
        }
        WalletEntity wallet = findWalletByOwner(ownerId, ownerType);
        return toWalletResponse(wallet);
    }

    @Override
    @Transactional
    public TransactionResponse transfer(UUID senderOwnerId, String role, TransferRequest request) {
        String invoiceId = "TRF-" + UUID.randomUUID();
        log.info("[FinanceService] Transfer dimulai — invoiceId={}, from={}, role={}, to={}",
                invoiceId, senderOwnerId, role, request.getRecipientWalletId());

        String ownerType = "USER";
        if (role != null && role.contains("MERCHANT")) {
            ownerType = "MERCHANT";
        }

        WalletEntity senderWallet = findWalletByOwner(senderOwnerId, ownerType);

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

        try {
            validateSufficientBalance(senderWallet, request.getAmount());

            UUID recipientUuid = UUID.fromString(request.getRecipientWalletId());
            WalletEntity recipientWallet = walletRepository.findById(recipientUuid)
                    .or(() -> walletRepository.findByOwnerIdAndOwnerType(recipientUuid, "USER"))
                    .orElseThrow(() -> new IllegalStateException("Wallet penerima tidak ditemukan."));

            if (role != null && role.contains("MERCHANT")) {
                MerchantOwnerMappingEntity mapping = merchantOwnerMappingRepository.findByMerchantUserId(senderOwnerId)
                        .orElseThrow(() -> new IllegalStateException("Data owner untuk merchant ini tidak ditemukan. Hubungi CS."));
                if (!mapping.getOwnerUserId().equals(recipientWallet.getOwnerId())) {
                    throw new IllegalStateException("Akun merchant hanya diperbolehkan transfer ke wallet milik owner merchant tersebut.");
                }
            }

            senderWallet.setBalance(senderWallet.getBalance().subtract(request.getAmount()));
            walletRepository.save(senderWallet);

            recipientWallet.setBalance(recipientWallet.getBalance().add(request.getAmount()));
            walletRepository.save(recipientWallet);

            doc.getRecipient().setWalletId(recipientWallet.getId().toString());
            doc.getRecipient().setUserId(recipientWallet.getOwnerId().toString());
            doc.setStatus("SUCCESS");
            transactionRepository.save(doc);

            log.info("[FinanceService] Transfer berhasil — invoiceId={}", invoiceId);

        } catch (Exception e) {

            doc.setStatus("FAILED");
            doc.setNote("ERROR: " + e.getMessage());
            transactionRepository.save(doc);

            log.error("[FinanceService] Transfer gagal — invoiceId={}, alasan={}", invoiceId, e.getMessage());
            throw new IllegalStateException("Transfer gagal: " + e.getMessage());
        }

        return toTransactionResponse(doc);
    }

    @Override
    @Transactional
    public TransactionResponse transferToOwner(UUID merchantUserId, java.math.BigDecimal amount, String note) {
        String invoiceId = "TRF-OWN-" + UUID.randomUUID();
        log.info("[FinanceService] Transfer ke owner dimulai — invoiceId={}, merchant={}, amount={}",
                invoiceId, merchantUserId, amount);

        MerchantOwnerMappingEntity mapping = merchantOwnerMappingRepository.findByMerchantUserId(merchantUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Data owner untuk merchant ini tidak ditemukan. Silakan hubungi CS."));

        UUID ownerUserId = mapping.getOwnerUserId();

        WalletEntity senderWallet = findWalletByOwner(merchantUserId, "MERCHANT");
        WalletEntity recipientWallet = walletRepository.findByOwnerIdAndOwnerType(ownerUserId, "USER")
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet owner tidak ditemukan. Pastikan owner sudah pernah login."));

        TransactionDocument doc = TransactionDocument.builder()
                .invoiceId(invoiceId)
                .transactionType("TRANSFER_TO_OWNER")
                .status("PENDING")
                .amount(amount)
                .sender(TransactionDocument.PartyInfo.builder()
                        .userId(merchantUserId.toString())
                        .walletId(senderWallet.getId().toString())
                        .build())
                .recipient(TransactionDocument.PartyInfo.builder()
                        .userId(ownerUserId.toString())
                        .walletId(recipientWallet.getId().toString())
                        .build())
                .note(note)
                .build();
        transactionRepository.save(doc);

        try {
            validateSufficientBalance(senderWallet, amount);

            senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
            walletRepository.save(senderWallet);

            recipientWallet.setBalance(recipientWallet.getBalance().add(amount));
            walletRepository.save(recipientWallet);

            doc.setStatus("SUCCESS");
            transactionRepository.save(doc);

            log.info("[FinanceService] Transfer ke owner berhasil — invoiceId={}, owner={}", invoiceId, ownerUserId);

        } catch (Exception e) {
            doc.setStatus("FAILED");
            doc.setNote("ERROR: " + e.getMessage());
            transactionRepository.save(doc);
            log.error("[FinanceService] Transfer ke owner gagal — invoiceId={}, alasan={}", invoiceId, e.getMessage());
            throw new IllegalStateException("Transfer ke owner gagal: " + e.getMessage());
        }

        return toTransactionResponse(doc);
    }

    @Override
    @Transactional
    public TransactionResponse generateQris(UUID merchantOwnerId, QrisGenerateRequest request) {
        String invoiceId = "QRIS-" + UUID.randomUUID();
        WalletEntity merchantWallet = findWalletByOwner(merchantOwnerId, "MERCHANT");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(5);

        TransactionDocument doc = TransactionDocument.builder()
                .invoiceId(invoiceId)
                .transactionType("QRIS_PAYMENT")
                .status("PENDING")
                .amount(request.getAmount())
                .recipient(TransactionDocument.PartyInfo.builder()
                        .userId(merchantOwnerId.toString())
                        .walletId(merchantWallet.getId().toString())
                        .build())
                .rawQrisData(invoiceId)
                .expiresAt(expiresAt)
                .note(request.getNote())
                .build();

        transactionRepository.save(doc);
        log.info("[FinanceService] QRIS invoice dibuat — invoiceId={}, expiresAt={}", invoiceId, expiresAt);

        return toTransactionResponse(doc);
    }

    @Override
    @Transactional
    public TransactionResponse payQris(UUID buyerOwnerId, QrisPayRequest request) {
        String invoiceId = request.getInvoiceId();
        log.info("[FinanceService] Inisiasi pembayaran QRIS (Saga Fase 1) — invoiceId={}, buyer={}",
                invoiceId, buyerOwnerId);

        TransactionDocument doc = transactionRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Invoice QRIS tidak ditemukan: " + invoiceId));

        if (!"PENDING".equals(doc.getStatus())) {
            throw new IllegalStateException(
                    "Invoice " + invoiceId + " sudah tidak bisa dibayar (status: " + doc.getStatus() + ").");
        }

        if (doc.getExpiresAt() != null && LocalDateTime.now().isAfter(doc.getExpiresAt())) {
            doc.setStatus("DENIED");
            doc.setNote("Invoice kedaluwarsa — pembayaran tidak dilakukan dalam batas 5 menit.");
            transactionRepository.save(doc);
            log.warn("[FinanceService] QRIS ditolak (expired) — invoiceId={}, expiredAt={}",
                    invoiceId, doc.getExpiresAt());
            throw new IllegalStateException(
                    "Invoice QRIS sudah kedaluwarsa. Minta merchant untuk generate QRIS baru.");
        }

        WalletEntity buyerWallet = findWalletByOwner(buyerOwnerId, "USER");

        financeEventProducer.publishQrisInitiated(
                invoiceId,
                buyerOwnerId.toString(),
                doc.getAmount()
        );

        log.info("[FinanceService] Event qris.payment.initiated dipublish — invoiceId={}", invoiceId);

        return toTransactionResponse(doc);
    }

    @Override
    @Transactional
    public TransactionResponse redeemVoucher(UUID ownerId, VoucherRedeemRequest request) {
        String invoiceId = "VCH-" + UUID.randomUUID();
        log.info("[FinanceService] Klaim voucher — code={}, ownerId={}", request.getCode(), ownerId);

        VoucherEntity voucher = voucherRepository.findAvailableByCode(request.getCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Kode voucher '" + request.getCode() + "' tidak valid atau sudah digunakan."));

        WalletEntity wallet = findWalletByOwner(ownerId, "USER");

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

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionStatus(String invoiceId) {
        TransactionDocument doc = transactionRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new IllegalStateException(
                        "Transaksi dengan invoice ID '" + invoiceId + "' tidak ditemukan."));
        return toTransactionResponse(doc);
    }

    private WalletEntity findWalletByOwner(UUID ownerId, String defaultOwnerType) {
        return walletRepository.findByOwnerIdAndOwnerType(ownerId, defaultOwnerType)
                .orElseGet(() -> {
                    log.info("[FinanceService] Wallet tidak ditemukan untuk owner: {}. Inisialisasi lazy wallet tipe {}", ownerId, defaultOwnerType);
                    WalletEntity wallet = WalletEntity.builder()
                            .ownerId(ownerId)
                            .ownerType(defaultOwnerType)
                            .balance(BigDecimal.ZERO)
                            .build();
                    return walletRepository.save(wallet);
                });
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
                .expiresAt(d.getExpiresAt())
                .rawQrisData(d.getRawQrisData())
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
