package com.priestess.oracle.service.impl;

import com.priestess.oracle.entity.ComplaintDocument;
import com.priestess.oracle.service.GeminiAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * GeminiAiServiceImpl — Entry point ASINKRON untuk analisis Gemini AI.
 *
 * <h2>Kenapa @Async dan @Retryable tidak boleh di method yang sama?</h2>
 * <p>Spring AOP membuat satu proxy per bean. Jika satu method punya dua anotasi
 * AOP (@Async + @Retryable), Spring hanya bisa menerapkan satu proxy secara efektif.
 * Hasilnya: @Retryable tidak pernah aktif karena @Async proxy memotong rantai.
 *
 * <h2>Solusi: Delegasi ke bean terpisah</h2>
 * <ul>
 *   <li>{@code GeminiAiServiceImpl} — hanya menangani @Async (proxy async)</li>
 *   <li>{@link RetryableGeminiExecutor} — hanya menangani @Retryable (proxy retry)</li>
 * </ul>
 * Dengan memisahkan keduanya ke bean yang berbeda, Spring dapat membuat proxy
 * terpisah untuk masing-masing — keduanya berjalan sempurna.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiAiServiceImpl implements GeminiAiService {

    // Bean terpisah yang menangani @Retryable
    private final RetryableGeminiExecutor retryableExecutor;

    /**
     * Dipanggil oleh {@code ComplaintServiceImpl} setelah menyimpan keluhan.
     * Method ini langsung return (non-blocking) — analisis dilanjutkan di thread pool
     * {@code oracle-async-*} yang dikonfigurasi di {@code application.properties}.
     *
     * @param complaint dokumen yang sudah disimpan di MongoDB (status = OPEN)
     */
    @Async
    @Override
    public void analyzeComplaint(ComplaintDocument complaint) {
        log.info("[GeminiAI] Thread async dimulai untuk complaintId={}", complaint.getComplaintId());
        try {
            // Delegasi ke RetryableGeminiExecutor yang punya @Retryable
            retryableExecutor.executeWithRetry(complaint);
        } catch (Exception e) {
            // Dicapai hanya jika semua 3 percobaan retry habis
            log.error("[GeminiAI] SEMUA RETRY HABIS untuk complaintId={}. Keluhan tetap OPEN. Error: {}",
                    complaint.getComplaintId(), e.getMessage());
        }
    }
}
