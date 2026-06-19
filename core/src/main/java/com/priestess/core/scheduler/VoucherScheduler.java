package com.priestess.core.scheduler;

import com.priestess.core.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * VoucherScheduler — Komponen scheduler otomatis untuk manajemen voucher.
 *
 * <p>Mengimplementasikan fitur {@code @Scheduled} sesuai coding-style.md
 * Section 6.2 (Scheduler / Time-Driven). Scheduler ini berjalan otomatis
 * berdasarkan interval waktu internal tanpa perlu ada hit/request dari user.
 *
 * <h2>Jenis Penjadwalan yang Digunakan</h2>
 * <ul>
 *   <li><b>fixedDelay</b> — Job pemeriksaan statistik voucher berjalan
 *       dengan jeda 10 menit setelah task sebelumnya selesai dieksekusi.</li>
 *   <li><b>cron</b> — Job log ringkasan harian berjalan tepat pukul 00:05 setiap hari.</li>
 * </ul>
 *
 * <p>{@code @EnableScheduling} wajib diaktifkan di {@link com.priestess.core.CoreApplication}
 * agar anotasi {@code @Scheduled} pada class ini diproses oleh Spring.
 *
 * @see com.priestess.core.CoreApplication
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherScheduler {

    private final VoucherRepository voucherRepository;

    // =========================================================================
    // JOB 1: Pemeriksaan Statistik Voucher (fixedDelay: 10 menit)
    // =========================================================================

    /**
     * Memeriksa jumlah voucher yang masih tersedia (belum diklaim) secara berkala.
     *
     * <p>Menggunakan {@code fixedDelay} — jeda 10 menit dihitung setelah task
     * sebelumnya SELESAI dieksekusi. Cocok untuk operasi pemantauan yang tidak
     * perlu presisi waktu ketat.
     *
     * <p>{@code initialDelay} 60 detik agar scheduler tidak langsung berjalan
     * saat aplikasi baru pertama kali startup.
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void checkAvailableVouchers() {
        try {
            long availableCount = voucherRepository.countByIsRedeemed(false);
            long redeemedCount  = voucherRepository.countByIsRedeemed(true);

            log.info("[VoucherScheduler] Statistik voucher — Tersedia: {}, Sudah diklaim: {}",
                    availableCount, redeemedCount);

            if (availableCount == 0) {
                log.warn("[VoucherScheduler] ⚠️ Stok voucher HABIS! Pertimbangkan untuk menambah voucher baru.");
            } else if (availableCount < 5) {
                log.warn("[VoucherScheduler] ⚠️ Stok voucher menipis! Hanya tersisa {} voucher.", availableCount);
            }

        } catch (Exception e) {
            log.error("[VoucherScheduler] Error saat memeriksa statistik voucher: {}", e.getMessage(), e);
        }
    }

    // =========================================================================
    // JOB 2: Log Ringkasan Harian (cron: setiap hari pukul 00:05)
    // =========================================================================

    /**
     * Mencatat ringkasan harian kondisi voucher ke log sistem.
     *
     * <p>Menggunakan ekspresi {@code cron} — berjalan tepat pukul 00:05 pagi setiap hari
     * (5 menit setelah tengah malam) untuk memastikan log harian akurat.
     *
     * <p>Format cron: {@code "detik menit jam hari-bulan bulan hari-minggu"}
     * Ekspresi {@code "0 5 0 * * *"} = setiap hari pukul 00:05:00.
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void dailyVoucherSummaryLog() {
        try {
            long available = voucherRepository.countByIsRedeemed(false);
            long redeemed  = voucherRepository.countByIsRedeemed(true);
            long total     = available + redeemed;

            log.info("============================================================");
            log.info("[VoucherScheduler] === RINGKASAN VOUCHER HARIAN ===");
            log.info("[VoucherScheduler] Total voucher    : {}", total);
            log.info("[VoucherScheduler] Sudah diklaim    : {}", redeemed);
            log.info("[VoucherScheduler] Masih tersedia   : {}", available);
            if (total > 0) {
                log.info("[VoucherScheduler] Tingkat klaim    : {}%",
                        String.format("%.1f", (redeemed * 100.0) / total));
            }
            log.info("============================================================");

        } catch (Exception e) {
            log.error("[VoucherScheduler] Error saat membuat ringkasan harian: {}", e.getMessage(), e);
        }
    }
}
