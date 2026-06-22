package com.priestess.core.scheduler;

import com.priestess.core.entity.TransactionDocument;
import com.priestess.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * QrisExpiryScheduler — Scheduler otomatis untuk mengganti status invoice QRIS
 * yang kedaluwarsa dari {@code PENDING} menjadi {@code DENIED}.
 *
 * <h2>Mekanisme Kerja</h2>
 * <p>Berjalan setiap 60 detik menggunakan {@code fixedDelay}. Setiap eksekusi:
 * <ol>
 *   <li>Query MongoDB: cari semua QRIS_PAYMENT dengan status {@code PENDING}
 *       dan {@code expiresAt < now()}.</li>
 *   <li>Set status masing-masing menjadi {@code DENIED} dengan catatan alasan.</li>
 *   <li>Simpan kembali ke MongoDB.</li>
 * </ol>
 *
 * <h2>Waktu Batas Pembayaran</h2>
 * <p>Setiap invoice QRIS memiliki {@code expiresAt = createdAt + 5 menit}.
 * Nilai ini diset oleh {@code FinanceServiceImpl.generateQris()} saat invoice dibuat.
 *
 * <p>{@code @EnableScheduling} diaktifkan di {@link com.priestess.core.CoreApplication}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QrisExpiryScheduler {

    private final TransactionRepository transactionRepository;

    /**
     * Job pengecekan expiry QRIS — berjalan setiap 60 detik.
     *
     * <p>Menggunakan {@code fixedDelay} agar tidak ada overlap eksekusi:
     * jeda 60 detik dihitung setelah task sebelumnya selesai dieksekusi.
     * {@code initialDelay} 30 detik agar tidak langsung berjalan saat startup.
     */
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
