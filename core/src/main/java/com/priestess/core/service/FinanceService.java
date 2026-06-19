package com.priestess.core.service;

import com.priestess.core.dto.TransactionResponse;
import com.priestess.core.dto.TransferRequest;
import com.priestess.core.dto.QrisGenerateRequest;
import com.priestess.core.dto.QrisPayRequest;
import com.priestess.core.dto.VoucherRedeemRequest;
import com.priestess.core.dto.WalletResponse;

import java.util.UUID;

/**
 * FinanceService — Kontrak seluruh operasi finansial Core Finance Service.
 */
public interface FinanceService {

    /** Ambil data dompet milik pengguna yang sedang login. */
    WalletResponse getMyWallet(UUID ownerId, String role);

    /** Transfer P2P antar dompet — pola Dual-Write SECTION 7. */
    TransactionResponse transfer(UUID senderOwnerId, TransferRequest request);

    /** Generate invoice QRIS dinamis dengan status PENDING. */
    TransactionResponse generateQris(UUID merchantOwnerId, QrisGenerateRequest request);

    /** Eksekusi pembayaran QRIS — debit buyer, kredit merchant. */
    TransactionResponse payQris(UUID buyerOwnerId, QrisPayRequest request);

    /** Klaim kode voucher dan tambahkan saldo. */
    TransactionResponse redeemVoucher(UUID ownerId, VoucherRedeemRequest request);

    /** Cek status transaksi berdasarkan invoice ID (untuk polling Angular). */
    TransactionResponse getTransactionStatus(String invoiceId);
}
