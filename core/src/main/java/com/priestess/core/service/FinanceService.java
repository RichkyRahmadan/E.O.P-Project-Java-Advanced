package com.priestess.core.service;

import com.priestess.core.dto.TransactionResponse;
import com.priestess.core.dto.TransferRequest;
import com.priestess.core.dto.QrisGenerateRequest;
import com.priestess.core.dto.QrisPayRequest;
import com.priestess.core.dto.VoucherRedeemRequest;
import com.priestess.core.dto.WalletResponse;

import java.util.UUID;

public interface FinanceService {

    WalletResponse getMyWallet(UUID ownerId, String role);

    TransactionResponse transfer(UUID senderOwnerId, String role, TransferRequest request);

    TransactionResponse transferToOwner(UUID merchantUserId, java.math.BigDecimal amount, String note);

    TransactionResponse generateQris(UUID merchantOwnerId, QrisGenerateRequest request);

    TransactionResponse payQris(UUID buyerOwnerId, QrisPayRequest request);

    TransactionResponse redeemVoucher(UUID ownerId, VoucherRedeemRequest request);

    TransactionResponse getTransactionStatus(String invoiceId);
}
