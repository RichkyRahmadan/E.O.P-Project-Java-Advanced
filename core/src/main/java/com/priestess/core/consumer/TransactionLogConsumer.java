package com.priestess.core.consumer;

import com.priestess.core.config.RabbitMQConfig;
import com.priestess.core.entity.TransactionDocument;
import com.priestess.core.producer.FinanceEventProducer;
import com.priestess.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionLogConsumer {

    private final TransactionRepository transactionRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_QRIS_SUCCESS)
    public void handleQrisPaymentSuccess(FinanceEventProducer.QrisResultEvent event) {
        String invoiceId = event.getInvoiceId();
        log.info("[TransactionLogConsumer] Menerima qris.payment.success — invoiceId={}", invoiceId);

        try {
            TransactionDocument doc = transactionRepository.findByInvoiceId(invoiceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Dokumen transaksi tidak ditemukan: " + invoiceId));

            if (event.getBuyerOwnerId() != null && doc.getSender() == null) {
                doc.setSender(TransactionDocument.PartyInfo.builder()
                        .userId(event.getBuyerOwnerId())
                        .walletId(event.getBuyerWalletId())
                        .build());
            }

            doc.setStatus("SUCCESS");
            transactionRepository.save(doc);

            log.info("[TransactionLogConsumer] MongoDB diperbarui → SUCCESS untuk invoiceId={}", invoiceId);

        } catch (Exception e) {
            log.error("[TransactionLogConsumer] Gagal update SUCCESS untuk invoiceId={}: {}",
                    invoiceId, e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_QRIS_FAILED)
    public void handleQrisPaymentFailed(FinanceEventProducer.QrisResultEvent event) {
        String invoiceId = event.getInvoiceId();
        log.info("[TransactionLogConsumer] Menerima qris.payment.failed — invoiceId={}", invoiceId);

        try {
            TransactionDocument doc = transactionRepository.findByInvoiceId(invoiceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Dokumen transaksi tidak ditemukan: " + invoiceId));

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
