package com.priestess.core.scheduler;

import com.priestess.core.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherScheduler {

    private final VoucherRepository voucherRepository;

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
