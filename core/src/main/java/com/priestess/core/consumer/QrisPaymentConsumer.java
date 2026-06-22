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

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QrisPaymentConsumer {

    private final WalletRepository      walletRepository;
    private final TransactionRepository transactionRepository;
    private final FinanceEventProducer  financeEventProducer;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_QRIS_INITIATED)
    @Transactional
    public void handleQrisPaymentInitiated(FinanceEventProducer.QrisPaymentEvent event) {
        String invoiceId    = event.getInvoiceId();
        String buyerOwnerId = event.getBuyerOwnerId();

        log.info("[QrisPaymentConsumer] Menerima event qris.payment.initiated — invoiceId={}, buyer={}",
                invoiceId, buyerOwnerId);

        try {

            TransactionDocument doc = transactionRepository.findByInvoiceId(invoiceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Invoice tidak ditemukan di MongoDB: " + invoiceId));

            if (!"PENDING".equals(doc.getStatus())) {
                log.warn("[QrisPaymentConsumer] Invoice {} sudah tidak PENDING (status: {}), skip.",
                        invoiceId, doc.getStatus());
                return;
            }

            WalletEntity buyerWallet = walletRepository
                    .findByOwnerIdAndOwnerType(UUID.fromString(buyerOwnerId), "USER")
                    .orElseGet(() -> {
                        log.info("[QrisPaymentConsumer] Wallet buyer tidak ditemukan: {}. Inisialisasi lazy wallet tipe USER", buyerOwnerId);
                        WalletEntity wallet = WalletEntity.builder()
                                .ownerId(UUID.fromString(buyerOwnerId))
                                .ownerType("USER")
                                .balance(BigDecimal.ZERO)
                                .build();
                        return walletRepository.save(wallet);
                    });

            UUID merchantWalletId = UUID.fromString(doc.getRecipient().getWalletId());
            WalletEntity merchantWallet = walletRepository.findById(merchantWalletId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Wallet merchant tidak ditemukan: " + merchantWalletId));

            if (buyerWallet.getBalance().compareTo(doc.getAmount()) < 0) {
                throw new IllegalStateException(
                        "Saldo tidak mencukupi. Saldo: " + buyerWallet.getBalance() +
                        " | Dibutuhkan: " + doc.getAmount());
            }

            buyerWallet.setBalance(buyerWallet.getBalance().subtract(doc.getAmount()));
            walletRepository.save(buyerWallet);

            merchantWallet.setBalance(merchantWallet.getBalance().add(doc.getAmount()));
            walletRepository.save(merchantWallet);

            doc.setSender(TransactionDocument.PartyInfo.builder()
                    .userId(buyerOwnerId)
                    .walletId(buyerWallet.getId().toString())
                    .build());
            transactionRepository.save(doc);

            log.info("[QrisPaymentConsumer] Saldo berhasil dimutasi — invoiceId={}", invoiceId);

            financeEventProducer.publishQrisSuccess(
                    invoiceId,
                    buyerOwnerId,
                    buyerWallet.getId().toString()
            );

        } catch (Exception e) {

            log.error("[QrisPaymentConsumer] Gagal memproses pembayaran QRIS — invoiceId={}, alasan={}",
                    invoiceId, e.getMessage(), e);

            financeEventProducer.publishQrisFailed(invoiceId, e.getMessage());
        }
    }
}
