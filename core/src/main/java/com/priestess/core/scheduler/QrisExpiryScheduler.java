package com.priestess.core.scheduler;

import com.priestess.core.entity.TransactionDocument;
import com.priestess.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QrisExpiryScheduler {

    private final TransactionRepository transactionRepository;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void denyExpiredQrisInvoices() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<TransactionDocument> expiredList = transactionRepository.findExpiredPendingQris(now);

            if (expiredList.isEmpty()) {
                log.debug("[QrisExpiryScheduler] Tidak ada invoice QRIS yang kedaluwarsa saat ini.");
                return;
            }

            log.info("[QrisExpiryScheduler] Ditemukan {} invoice QRIS kedaluwarsa — mengubah ke DENIED.",
                    expiredList.size());

            for (TransactionDocument doc : expiredList) {
                doc.setStatus("DENIED");
                doc.setNote("Invoice kedaluwarsa — pembayaran tidak dilakukan dalam batas 5 menit.");
                transactionRepository.save(doc);
                log.info("[QrisExpiryScheduler] Invoice DENIED — invoiceId={}, expiredAt={}",
                        doc.getInvoiceId(), doc.getExpiresAt());
            }

        } catch (Exception e) {
            log.error("[QrisExpiryScheduler] Error saat memproses expiry QRIS: {}", e.getMessage(), e);
        }
    }
}
